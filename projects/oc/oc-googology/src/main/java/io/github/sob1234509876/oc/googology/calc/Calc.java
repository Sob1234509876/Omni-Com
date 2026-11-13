package io.github.sob1234509876.oc.googology.calc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The debugging calculator's REPL: a bare read-eval-print loop over {@link CalcEngine}. Start it with
 *
 * <pre>./gradlew :projects:oc:oc-googology:calc --console=plain -q</pre>
 *
 * <p>stdin works in both modes a debugging session needs. Interactively the loop prompts on stdout and
 * answers every line; scripted, a pipe feeds it one command per line and the loop ends at the end of
 * input, so {@code echo "2^10+5" | ./gradlew :projects:oc:oc-googology:calc} prints the value without any
 * prompt noise. Commands can also be passed as program arguments, which runs each one and exits - handy
 * from an editor's run configuration. Type {@code help} for the command list.
 *
 * <p>The loop itself never crashes: {@link CalcEngine#execute(String)} answers bad input with an error
 * line, and anything unexpected is caught and reported before the next prompt. The only way out is
 * {@code quit}, the end of input, or a signal.
 *
 * @see CalcEngine
 * @since 2.0a
 */
public final class Calc {

    private Calc() {
    }

    /**
     * Runs the calculator: command arguments are executed one by one and the program exits; without
     * arguments the REPL reads stdin until it ends or {@code quit} arrives.
     */
    public static void main(String[] args) throws IOException {
        CalcEngine engine = new CalcEngine();
        if (args.length > 0) {
            for (String line : args) {
                print(engine, line);
            }
            return;
        }
        boolean interactive = System.console() != null;
        if (interactive) {
            System.out.println("oc-googology 调试计算器，help 看命令，quit 退出。");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                if (interactive) {
                    System.out.print("> ");
                    System.out.flush();
                }
                String line = reader.readLine();
                if (line == null) {
                    return;
                }
                print(engine, line);
                if (engine.execute(line).equals("quit")) {
                    return;
                }
            }
        }
    }

    /** Executes one line and prints the output; a blanket catch keeps the loop alive on anything unexpected. */
    private static void print(CalcEngine engine, String line) {
        String output;
        try {
            output = engine.execute(line);
        } catch (Throwable error) {
            output = "错误：" + error;
        }
        if (!output.isEmpty()) {
            System.out.println(output);
            System.out.flush();
        }
    }
}
