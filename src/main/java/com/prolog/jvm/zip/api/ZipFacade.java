package com.prolog.jvm.zip.api;

import java.util.List;

import com.prolog.jvm.exceptions.BacktrackException;
import com.prolog.jvm.symbol.ClauseSymbol;
import com.prolog.jvm.symbol.FunctorSymbol;

/**
 * Facade for accessing the ZIP machine's runtime data structures.
 * <p>
 * The idea of dissecting the specification of a virtual machine into familiar
 * object-oriented design patterns and hiding them behind a facade was explored
 * before at least in [1], though there having been pushed further in its
 * encapsulation of the machine state.
 * <p>
 * [1] Taivalsaari, Antero. "Implementing a Java Virtual Machine in the Java
 * Programming Language." (1998).
 *
 * @author Arno Bastenhof
 *
 */
public interface ZipFacade {

    // === (Re)initialization ===

    /**
     * (Re)sets the ZIP machine for executing the query at the specified
     * {@code queryAddr}.
     *
     * @param queryAddr the address in code memory whereat the (compiled) query
     * that is to be executed is stored
     */
    void reset(int queryAddr);

    // === Machine mode ===

    /**
     * Sets the machine mode.
     *
     * @param mode the machine mode; should be one of {@link ProcessorModes#ARG}
     * , {@link ProcessorModes#COPY} or {@link ProcessorModes#MATCH}
     */
    void setMode(int mode);

    // === Constant pool ===

    /**
     * Retrieves the constant for the specified index from the constant pool and
     * casts it to the supplied class.
     *
     * @param index the index for the constant pool entry to retrieve
     * @param clazz the class to cast the retrieved constant pool entry to
     * @throws NullPointerException if {@code clazz == null}
     * @throws IndexOutOfBoundsException if {@code index < 0} or {@code index}
     * exceeds the constant pool size - 1
     * @throws ClassCastException if the constant for the specified index cannot
     * be cast to the supplied class
     */
    <T> T getConstant(int index, Class<T> clazz);

    // === Code memory ===

    /**
     * Reads an instruction from code memory and returns its bit-wise
     * disjunction with the machine mode, advancing the instruction pointer as a
     * side effect.
     */
    int fetchOperator();

    /**
     * Reads an operand from code memory, advancing the instruction pointer as a
     * side effect. By passing {@code true} for {@code isVariable}, this
     * indicates the operand designates a variable, in which case its local
     * stack address is returned. Otherwise, the operand is returned as is.
     */
    int fetchOperand(boolean isVariable);

    /**
     * Sets the instruction pointer to the specified {@code address} and saves
     * the return address in the current target frame, if it exists.
     *
     * @return the local stack address at which the target frame has been
     * allocated, or {@link ProcessorModes#MIN_LOCAL_INDEX} if it doesn't exist
     */
    int jump(int address);

    /**
     * Returns the PC register, containing the current address in code memory.
     */
    int getProgramCounter();

    // === Global stack ===

    /**
     * Pushes the representation of a compound term on the global stack.
     *
     * @param symbol a symbol for a functor
     * @return an STR-tagged word
     */
    int pushFunctor(FunctorSymbol symbol);

    // === Local stack ===

    /**
     * Pushes a target frame on the local stack, returning the address therein
     * at which it has been allocated.
     */
    int pushTargetFrame();

    /**
     * Pops the current target frame, bypassing the need for calling
     * {@link #pushSourceFrame(int)} and {@link #popSourceFrame()} in sequence
     * when there are no goals to execute. Note, however, that the target
     * frame may do double duty as a choice point.
     *
     * @param size the frame size, specifying the combined number of parameters
     * and local variables
     */
    void popTargetFrame(final int size);

    /**
     * Sets the last choice point to the current target frame, storing therein
     * the current machine state.
     *
     * @param clause the backtrack clause pointer
     */
    void pushChoicePoint(ClauseSymbol clause);

    /**
     * Sets the last source frame to the current target frame, storing therein
     * the specified frame {@code size}.
     *
     * @param Local stack address for the first local variable cell in the
     * source frame to be pushed
     * @param size the frame size, specifying the combined number of parameters
     * and local variables
     */
    void pushSourceFrame(int size);

    /**
     * Pops the source frame.
     *
     * @return whether execution has finished
     */
    boolean popSourceFrame();

    // === Scratchpad ===

    /**
     * Pushes the specified address together with the machine mode on the
     * scratchpad.
     *
     * @param address a global- or local stack address
     */
    void pushOnScratchpad(int address);

    /**
     * Pops a mode and global- or local stack address from the scratchpad, using
     * the former for restoring the machine mode while returning the latter.
     */
    int popFromScratchpad();

    // === Dereferencing, binding and unification ===

    /**
     * Returns the word found by dereferencing the specified {@code address}.
     */
    int getWordAt(int address);

    /**
     * Sets the given global- or local stack {@code address} to contain the
     * specified {@code word}.
     */
    void setWord(int address, int word);

    /**
     * Writes the given constant {@code symbol} to the specified global- or
     * local stack {@code address}.
     */
    void setWord(int address, FunctorSymbol symbol);

    /**
     * Performs binding on the specified addresses, at least one of which is to
     * contain a reference-tagged word, and {@link #trail(int) trails} if
     * necessary.
     *
     * @param address1 a local- or global stack address
     * @param address2 a local- or global stack address
     * @return {@code address1} if bound to {@code address2}, or
     * {@code address2} otherwise
     */
    int bind(int address1, int address2);

    /**
     * Trails the specified {@code address} if needed. I.e., if a choice point
     * has been allocated on the local stack and either: (a) {@code address} is
     * part of the global stack and occurs before the backtrack global stack
     * top; or (b) it is part of the local stack. If neither condition applies,
     * trailing would have no effect as the contents at {@code address} would
     * already be garbage-collected at backtracking.
     */
    void trail(int address);

    /**
     * Attempts unification on the specified addresses and returns whether said
     * attempt was successful.
     *
     * @param address1 a local- or global stack address
     * @param address2 a local- or global stack address
     * @return a list of addresses that were bound during unification, or
     * {@code null} if unification failed
     */
    List<Integer> unifiable(int address1, int address2);

    // === Backtracking ===

    /**
     * Performs backtracking, recording which variables were unbound in
     * {@code vars}.
     *
     * @param vars a list for storing the addresses of variables that have
     * become unbound during backtracking; must be empty and not allowed to be
     * null
     * @throws BacktrackException if there was no choice point to backtrack to
     * @throws IllegalArgumentException if {@code !vars.isEmpty()}
     * @throws NullPointerException if {@code vars == null}
     */
    int backtrack(List<Integer> vars) throws BacktrackException;

}
