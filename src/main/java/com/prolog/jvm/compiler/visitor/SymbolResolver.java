package com.prolog.jvm.compiler.visitor;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import com.prolog.jvm.compiler.ast.Ast;
import com.prolog.jvm.main.Factory;
import com.prolog.jvm.symbol.ClauseSymbol;
import com.prolog.jvm.symbol.FunctorSymbol;
import com.prolog.jvm.symbol.PredicateSymbol;
import com.prolog.jvm.symbol.Scope;
import com.prolog.jvm.symbol.Symbol;
import com.prolog.jvm.symbol.SymbolKey;
import com.prolog.jvm.symbol.SymbolKeys;
import com.prolog.jvm.symbol.VariableSymbol;

/**
 * Visitor for traversing an {@link Ast} to register {@link Symbol} definitions
 * and resolve their uses.
 *
 * @author Arno Bastenhof
 */
public final class SymbolResolver extends BasicPrologVisitor<Ast> {

	// A mapping of AST nodes to the symbols to which they have been resolved
	private final Map<Ast, Symbol> symbols = new IdentityHashMap<>();

	private ClauseSymbol currentClause;

	private Scope currentScope;

	/**
	 * Creates a new instance based on the specified global {@code scope}, used
	 * for resolving functor-, clause- and predicate symbols.
	 * <p>
	 * Typically, when compiling a program, {@code scope} should be new,
	 * obtained through{@link Scope#newRootInstance()}. In contrast, when
	 * compiling a query, references may be encountered to global symbols
	 * previously found in the program, and which hence should still be
	 * available in the {@code scope} that's passed in. It is recommended that
	 * client code does not instantiate this class directly, but rather invoke
	 * one of the factory methods {@link Factory#newProgramCompiler()} or
	 * {@link Factory#newQueryCompiler()}, ensuring only instances of this
	 * class are used that satisfy the above guidelines.
	 *
	 * @param scope the 'global' root scope; not allowed to be null
	 * @throws NullPointerException if {@code scope == null}
	 */
	public SymbolResolver(Scope scope) {
		this.currentScope = checkNotNull(scope);
	}

	private void pushScope() {
		this.currentScope = Scope.newIntermediateInstance(this.currentScope);
	}

	private void popScope() {
		this.currentScope = this.currentScope.getParent();
	}

	/**
	 * Returns an unmodifiable {@link Map} associating {@link Ast} nodes with
	 * the symbols to which they have been resolved.
	 */
	public Map<Ast, Symbol> getSymbols() {
		return Collections.unmodifiableMap(this.symbols);
	}

	@Override
	public void preVisitClause(Ast clause) {
		// Obtain the root node for the clause's head literal
		Ast literal = getHeadLiteral(clause);

		// Define a symbol for the clause
		ClauseSymbol symbol = getClauseSymbol(literal);
		this.currentClause = symbol;

		// Set the number of parameters passed to this clause
		symbol.setParams(literal.getArity());

		// Define a FunctorSymbol for the head literal, aiding disassembly
		// TODO Assembly generation not yet implemented
		this.symbols.put(literal, getFunctorSymbol(literal));

		// Push the clause scope
		pushScope();

		// TODO Push a scope for the head literal
	}

	private Ast getHeadLiteral(Ast clause) {
		Iterator<Ast> it = clause.iterator();
		if (!it.hasNext()) {
			throw new IllegalArgumentException();
		}
		return clause.iterator().next();
	}

	@Override
	public void inVisitClause(Ast param) {
		// TODO Pop the scope for the head literal

		// TODO Push a scope for the first goal literal
	}

	@Override
	public void postVisitClause(Ast clause) {
		// Update the stack frame size
		// (Note this assumes only variable symbols are stored in the clause scope)
		this.currentClause.setLocals(this.currentScope.getSize());

		// Save the clause symbol for future reference
		this.symbols.put(clause, this.currentClause);

		// Pop the clause scope
		popScope();
	}

	@Override
	public void postVisitGoal(Ast goal) {
		// TODO Pop the scope for the previous goal literal

		PredicateSymbol symbol = getPredicateSymbol(goal);
		this.symbols.put(goal, symbol);

		// TODO Push a scope for the next goal literal
	}

	// TODO postVisitLastGoal: pop the scope for the last goal literal

	@Override
	public void preVisitCompound(Ast functor) {
		this.symbols.put(functor, getFunctorSymbol(functor));
	}

	@Override
	public void visitConstant(Ast constant) {
		this.symbols.put(constant, getFunctorSymbol(constant));
	}

	@Override
	public void visitVariable(Ast variable) {
		this.symbols.put(variable, getVariableSymbol(variable));
	}

	// === Symbol table management (TODO this would benefit from lambda's) ===

	// Strategy class for Symbol creation
	private static abstract class SymbolBuilder<T extends Symbol> {
		abstract T build();
	}

	/*
	 * Looks up the symbol for the given key in the global scope and returns it
	 * if found. Otherwise, a new symbol is defined in the global scope (and
	 * afterwards returned) using the supplied strategy.
	 */
	private <T extends Symbol> T getGlobalSymbol(SymbolKey<T> key,
			SymbolBuilder<T> strategy) {
		assert key != null;
		assert strategy != null;

		T symbol = this.currentScope.resolveGlobal(key);
		if (symbol == null) {
			symbol = strategy.build();
			this.currentScope.defineGlobal(key, symbol);
		}
		return symbol;
	}

	private PredicateSymbol getPredicateSymbol(Ast clause) {
		assert clause != null;

		return getGlobalSymbol(
				SymbolKeys.ofPredicate(clause.getText(), clause.getArity()),
				new SymbolBuilder<PredicateSymbol>(){
					@Override
					PredicateSymbol build() {
						return new PredicateSymbol();
					}
				});
	}

	private FunctorSymbol getFunctorSymbol(Ast functor) {
		assert functor != null;

		final String name = functor.getText();
		final int arity = functor.getArity();
		return getGlobalSymbol(
				SymbolKeys.ofFunctor(name, arity),
				new SymbolBuilder<FunctorSymbol>(){
					@Override
					FunctorSymbol build() {
						return FunctorSymbol.valueOf(name, arity);
					}
				});
	}

	private VariableSymbol getVariableSymbol(Ast variable) {
		assert variable != null;

		// Was this variable already encountered before in this clause?
		SymbolKey<VariableSymbol> key = SymbolKeys.ofVariable(variable.getText());
		VariableSymbol symbol = this.currentScope.resolveLocal(key);
		// If not, create a new one and store it in the clause scope.
		if (symbol == null) {
			int offset = this.currentClause.getParams() + this.currentScope.getSize();
			symbol = new VariableSymbol(offset);
			this.currentScope.defineLocal(key, symbol);
		}
		return symbol;
	}

	private ClauseSymbol getClauseSymbol(Ast literal) {
		assert literal != null;

		// Define a new clause symbol.
		ClauseSymbol symbol = new ClauseSymbol();

		// See if a clause symbol for the same head literal was already defined
		SymbolKey<ClauseSymbol> clauseKey = SymbolKeys.ofClause(
				literal.getText(),
				literal.getArity());
		ClauseSymbol prevSymbol = this.currentScope.resolveGlobal(clauseKey);
		// If so, set its next clause alternative.
		if (prevSymbol != null) {
			prevSymbol.setNext(symbol);
		}
		else {
			// Otherwise, retrieve the predicate symbol for this clause
			PredicateSymbol predSymbol = getPredicateSymbol(literal);
			// and set the predicate's first clause alternative
			predSymbol.setFirst(symbol);
		}

		// Put the new clause symbol in the current scope, overriding the previous
		// alternative for the same predicate if it existed.
		this.currentScope.defineGlobal(clauseKey, symbol);

		return symbol;
	}
}