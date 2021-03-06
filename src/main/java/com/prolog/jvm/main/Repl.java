package com.prolog.jvm.main;

import static com.prolog.jvm.zip.util.ReplConstants.HALT;
import static com.prolog.jvm.zip.util.ReplConstants.PROMPT;
import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;

import com.prolog.jvm.zip.PrologBytecodeImpl.MementoImpl;

/**
 * Class implementing the Read-Eval-Print Loop.
 *
 * @author Arno Bastenhof
 *
 */
public enum Repl {

    /**
     * The unique instance for this class.
     */
    INSTANCE;

    /**
     * Executes the Read-Eval-Print Loop.
     *
     * @param in source for reading in queries
     * @param out target for writing answers to queries
     * @throws NullPointerException if {@code in == null} or {@code out ==
     * null}
     * @throws IOException
     */
    public void run(final Reader in, final Writer out) throws Exception {
        // check preconditions
        requireNonNull(in);
        requireNonNull(out);

        // the code address where compiled queries will be stored
        final int queryAddr = Factory.getBytecode().getCodeSize();

        // bytecode state prior to the compilation of any queries
        final MementoImpl m = Factory.getBytecode().createMemento();

        try (final BufferedReader reader = new BufferedReader(in)) {
            String userInput;
            out.append(PROMPT).flush();
            while (!HALT.equals(userInput = reader.readLine())) {
                try (final StringReader sr = new StringReader(userInput)) {
                    Factory.newQueryCompiler().compile(sr);
                } catch (Exception e) {
                    out.append(e.getMessage()).append('\n').append(PROMPT)
                            .flush();
                    continue;
                }
                Factory.getInterpreter().execute(queryAddr, reader, out);
                Factory.getBytecode().setMemento(m);
                out.append(PROMPT).flush();
            }
        }
    }
}
