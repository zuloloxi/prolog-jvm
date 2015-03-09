package com.prolog.jvm.compiler.parser;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the PrologJvmParser and -Lexer classes generated by ANTLR.
 * 
 * Note: see PrologJvm.g4 for notes on non-standard usages of terminology,
 * particularly with regard to the notion of program.
 * 
 * @author Arno Bastenhof
 *
 */
public final class PrologJvmParserTest {

	// Classpath resources
	private static final String wellFormed     = "gods.pl";  // a well-formed program
	private static final String noFactsOrRules = "query.pl"; // a program lacking program clauses
	private static final String noQuery        = "facts.pl"; // a program lacking a query
	
	@Test
	public final void testProgram() throws IOException {
		try (InputStream is = this.getClass().getResourceAsStream(wellFormed)) {
			testParser(is, p -> p.program(), 0);
		}
		
		try (InputStream is = this.getClass().getResourceAsStream(noFactsOrRules)) {
			testParser(is, p -> p.program(), 2);
		}
		
		try (InputStream is = this.getClass().getResourceAsStream(noQuery)) {
			testParser(is, p -> p.program(), 1);
		}
	}

	@Test
	public final void testQuery() {
		testParser("?- +(succ(zero),succ(zero),N).", p -> p.query(), 0);
		testParser("?- +(succ(zero),succ(zero),N)", p -> p.query(), 1); // Forgot the terminating dot.
		testParser("+(succ(zero),succ(zero),N).", p -> p.query(), 1); // A fact is not a query.
		testParser("+(succ(N),M,succ(K)) :- add(N,M,K).", p -> p.query(), 2); // A rule is not a query.
	}

	@Test
	public final void testFact() {
		testParser("+(zero,N,N).", p -> p.fact(), 0);		
		testParser("+(zero,N,N)", p -> p.fact(), 1); // Forgot the terminating dot.		
		testParser("?- +(succ(zero),succ(zero),N).", p -> p.fact(), 1); // A query is not a fact.
		testParser("+(succ(N),M,succ(K)) :- add(N,M,K).", p -> p.fact(), 1); // A rule is not a fact.		
	}

	@Test
	public final void testPlRule() {
		testParser("+(succ(N),M,succ(K)) :- add(N,M,K).", p -> p.plRule(), 0);
		testParser("+(succ(N),M,succ(K)) :- add(N,M,K)", p -> p.fact(), 1); // Forgot the terminating dot.
		testParser("?- +(succ(zero),succ(zero),N).", p -> p.plRule(), 2); // A query is not a rule
		testParser("+(zero,N,N).", p -> p.plRule(), 1); // A fact is not a rule.
	}
	
	@Test
	public final void testStruc() {
		testParser("\\(an_atom,anotherAtom,_)", p -> p.struc(), 0);
		testParser("X(Y,Z)", p -> p.struc(), 1); // X is not an atom.
		testParser("f(,)", p -> p.struc(), 1); // No arguments supplied.
	}
	
	// Parses the supplied input and returns the number of syntax errors.
	private final void testParser(ANTLRInputStream in, 
			Function<PrologJvmParser,?> rule,
			int syntaxErrors) {
		PrologJvmLexer lexer = new PrologJvmLexer(in);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		PrologJvmParser parser = new PrologJvmParser(tokens);
		rule.apply(parser);
		assertEquals(parser.getNumberOfSyntaxErrors(), syntaxErrors);
	}

	private final void testParser(InputStream in, Function<PrologJvmParser,?> rule, int syntaxErrors) throws IOException {
		testParser(new ANTLRInputStream(in), rule, syntaxErrors);
	}
	
	private final void testParser(String in, Function<PrologJvmParser,?> rule, int syntaxErrors) {
		testParser(new ANTLRInputStream(in), rule, syntaxErrors);
	}

}
