package io.github.sob1234509876.oc.googology.calc;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.TransfiniteHyperoperation;
import io.github.sob1234509876.oc.googology.bms.BmsMatrix;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinalNotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pure command engine of the debugging calculator: {@link #execute(String)} turns one input line into
 * one output text and never throws, so the interactive REPL in {@link Calc} is a bare read-print loop and
 * the self check can drive the exact same code paths without a console.
 *
 * <p><b>Commands</b> (a line whose first word is a command runs that command; anything else is a natural
 * expression to evaluate):
 *
 * <ul>
 *   <li>a <b>natural expression</b>: {@code + - * / mod} at the usual precedence, and the arrow family
 *       {@code ^, ^^, ↑, ↑↑, ...} right associative on top ({@code 2^3^2} is {@code 2^(3^2)}); atoms are
 *       whatever {@link Natural#parse(String)} reads - decimal digits, {@code 1e100}, {@code beyond};</li>
 *   <li>{@code cmp a b} - the three comparison channels: the total order's sign, the four-valued proof
 *       (or {@code UNKNOWN}), and the documented lean;</li>
 *   <li>{@code log base x}, {@code slog base x}, {@code root d x}, {@code hyperinv arrows base x} - the
 *       level-resolved inverses of {@link Natural};</li>
 *   <li>{@code hyp base level height} - {@code base{level}height} through
 *       {@link TransfiniteHyperoperation}; the level is a Bashicu matrix in text form ({@code (0)(1)} is
 *       {@code ω}) or a plain number for a finite level;</li>
 *   <li>{@code rank x} - the hyperoperation rank of a tower and its level reduced at the tower's own
 *       height;</li>
 *   <li>{@code ord matrix}, {@code ordcmp a b}, {@code ordseq matrix [n]} - read a Bashicu ordinal, compare
 *       two, and print the first {@code n} fundamental sequence elements in the {@code ω[m] = m+1}
 *       convention;</li>
 *   <li>{@code format plain|ascii|latex} - the output rendering, backed by {@link NaturalFormat};</li>
 *   <li>{@code help}, {@code quit}.</li>
 * </ul>
 *
 * <p><b>Forgiving by design.</b> Whitespace is optional around operators, every bad input produces a
 * message naming what went wrong, and no command mutates anything except the output format. Ordinal
 * arguments are single whitespace-delimited tokens: the parentheses of the matrix writing are not grouped
 * expressions, which keeps the two grammars from ever colliding.
 *
 * @see Calc
 * @since 2.0a
 */
public final class CalcEngine {

    /** A rejected input: carries the message the REPL prints and never a stack trace. */
    private static final class CalcError extends RuntimeException {

        private CalcError(String message) {
            super(message);
        }
    }

    /**
     * Splits an expression into atoms and operators: runs of carets, runs of up arrows, the four written
     * operators and {@code mod}. Kept as one pattern so the tokenizer and its arrow counting stay together.
     */
    private static final Pattern OPERATOR = Pattern.compile("(\\^+|↑+|\\+|\\*|/|mod|-)");

    private static final List<String> COMMANDS = List.of(
            "<表达式>", "cmp a b", "log base x", "slog base x", "root d x", "hyperinv arrows base x",
            "hyp base level height", "rank x", "ord matrix", "ordcmp a b", "ordseq matrix [n]",
            "format plain|ascii|latex", "help");

    private String format = "ascii";

    /**
     * Executes one line and returns the output, empty for a blank line or a silent success. This is the
     * one entry point both the REPL and the self check drive, and it never throws: a rejected input comes
     * back as an {@code 错误：...} line.
     */
    public String execute(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            return dispatch(trimmed);
        } catch (CalcError error) {
            return "错误：" + error.getMessage();
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException
                | ArithmeticException error) {
            return "错误：" + error.getMessage();
        }
    }

    /** The current output format, for the prompt-less scripted use. */
    public String format() {
        return format;
    }

    private String dispatch(String line) {
        String head = firstWord(line);
        String rest = line.substring(head.length()).trim();
        switch (head) {
            case "help":
                return help();
            case "quit":
            case "exit":
                return "quit";
            case "format":
                return setFormat(rest);
            case "cmp":
                return compare(rest);
            case "log":
                return inverse(rest, 1);
            case "slog":
                return inverse(rest, 2);
            case "root":
                return root(rest);
            case "hyperinv":
                return hyperInverse(rest);
            case "hyp":
                return hyperoperation(rest);
            case "rank":
                return rank(rest);
            case "ord":
                return ordinal(rest);
            case "ordcmp":
                return ordinalCompare(rest);
            case "ordseq":
                return ordinalSequence(rest);
            default:
                if (looksLikeCommand(head)) {
                    throw new CalcError("命令 " + head + " 需要参数，用法见 help");
                }
                return formatNatural(evalNatural(line));
        }
    }

    private static boolean looksLikeCommand(String head) {
        return switch (head) {
            case "cmp", "log", "slog", "root", "hyperinv", "hyp", "rank", "ord", "ordcmp", "ordseq", "format" -> true;
            default -> false;
        };
    }

    private static String firstWord(String line) {
        int end = 0;
        while (end < line.length() && !Character.isWhitespace(line.charAt(end))) {
            end++;
        }
        return line.substring(0, end);
    }

    private static String help() {
        StringBuilder text = new StringBuilder();
        text.append("调试用大数计算器。表达式：+ - * / mod，幂与箭头 ^ ^^ ↑ ↑↑ …（右结合），1e100、beyond。命令：");
        for (String command : COMMANDS) {
            text.append("\n  ").append(command);
        }
        text.append("\n  quit             退出（管道模式下到输入末尾自然退出）");
        return text.toString();
    }

    private String setFormat(String rest) {
        if (rest.equals("plain") || rest.equals("ascii") || rest.equals("latex")) {
            format = rest;
            return "输出格式已切换为 " + rest;
        }
        if (rest.isEmpty()) {
            return "当前输出格式：" + format;
        }
        throw new CalcError("format 需要 plain、ascii 或 latex，当前是 " + format);
    }

    /** Renders a natural result in the current format, with a describe line underneath. */
    private String formatNatural(Natural value) {
        String main;
        if (format.equals("plain")) {
            main = value.toString();
        } else if (format.equals("latex")) {
            main = NaturalFormat.latex(value);
        } else {
            main = NaturalFormat.ascii(value);
        }
        return main + "\n" + value.describe();
    }

    // ------------------------------------------------------------------ natural expressions

    /**
     * Evaluates a natural expression: tokens the line into atoms and operators and runs the precedence
     * climb from {@code + -} over {@code * / mod} to the arrows. Right associativity on the arrow level
     * is what makes {@code 2^3^2} read as {@code 2^(3^2)}.
     *
     * @throws CalcError when any atom is not something {@link Natural#parse(String)} understands
     */
    public Natural evalNatural(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            throw new CalcError("空表达式：" + text);
        }
        Parser parser = new Parser(tokens);
        Natural value = parser.parseSum();
        if (!parser.done()) {
            throw new CalcError("表达式没读完，多余的部分从 " + parser.peek() + " 开始：" + text);
        }
        return value;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = OPERATOR.matcher(text);
        int start = 0;
        while (matcher.find()) {
            addAtom(tokens, text.substring(start, matcher.start()));
            tokens.add(matcher.group());
            start = matcher.end();
        }
        addAtom(tokens, text.substring(start));
        return tokens;
    }

    /** Adds an atom token unless it is blank; atoms are read with {@link Natural#parse(String)}. */
    private static void addAtom(List<String> tokens, String raw) {
        String atom = raw.trim();
        if (!atom.isEmpty()) {
            tokens.add(atom);
        }
    }

    /** The precedence climb over a finished token list. */
    private static final class Parser {

        private final List<String> tokens;
        private int position;

        private Parser(List<String> tokens) {
            this.tokens = tokens;
        }

        boolean done() {
            return position >= tokens.size();
        }

        String peek() {
            return done() ? "" : tokens.get(position);
        }

        private String next() {
            if (done()) {
                throw new CalcError("表达式在运算符处结束");
            }
            return tokens.get(position++);
        }

        Natural parseSum() {
            Natural value = parseProduct();
            while (peek().equals("+") || peek().equals("-")) {
                String operator = next();
                Natural right = parseProduct();
                value = operator.equals("+") ? value.add(right) : value.subtractClamped(right);
            }
            return value;
        }

        Natural parseProduct() {
            Natural value = parseArrows();
            while (peek().equals("*") || peek().equals("/") || peek().equalsIgnoreCase("mod")) {
                String operator = next();
                Natural right = parseArrows();
                if (operator.equals("*")) {
                    value = value.multiply(right);
                } else if (operator.equals("/")) {
                    value = value.divide(right);
                } else {
                    Natural dividend = value;
                    value = value.remainder(right).orElseThrow(() -> new CalcError(
                            dividend + " mod " + right + " 的余数在结构分辨率下不可知"));
                }
            }
            return value;
        }

        Natural parseArrows() {
            Natural value = parseAtom();
            int arrows = arrowCount(peek());
            if (arrows > 0) {
                next();
                Natural height = parseArrows();   // right associative: 2^3^2 = 2^(3^2)
                return Natural.arrow(value, arrows, height);
            }
            return value;
        }

        private Natural parseAtom() {
            String token = next();
            Optional<Natural> parsed = Natural.parse(token);
            if (parsed.isEmpty()) {
                throw new CalcError("无法解析 " + token + "（数字、1e100、beyond 或运算符）");
            }
            return parsed.get();
        }

        private static int arrowCount(String token) {
            if (token.isEmpty()) {
                return 0;
            }
            int arrows = 0;
            while (arrows < token.length() && (token.charAt(arrows) == '^' || token.charAt(arrows) == '↑')) {
                arrows++;
            }
            return arrows == token.length() ? arrows : 0;
        }
    }

    private Natural evalOne(String text, String what) {
        if (text.isEmpty()) {
            throw new CalcError(what + " 缺少参数");
        }
        return evalNatural(text);
    }

    /** Splits the rest of a two-argument command at the first whitespace run. */
    private static String[] twoArguments(String rest, String command) {
        int split = splitIndex(rest);
        if (split < 0) {
            throw new CalcError(command + " 需要两个参数");
        }
        return new String[]{rest.substring(0, split).trim(), rest.substring(split).trim()};
    }

    private static int splitIndex(String rest) {
        for (int index = 0; index < rest.length(); index++) {
            if (Character.isWhitespace(rest.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ comparison and inverses

    private String compare(String rest) {
        String[] parts = twoArguments(rest, "cmp");
        Natural left = evalOne(parts[0], "cmp");
        Natural right = evalOne(parts[1], "cmp");
        ComparisonResult proof = left.compareMagnitude(right);
        ComparisonResult lean = left.compareWithLean(right);
        int order = left.compareTo(right);
        StringBuilder text = new StringBuilder();
        text.append("全序：").append(relative(left, right, order));
        text.append("\n四值证明：").append(proof);
        text.append("\n带倾向：").append(lean);
        text.append("\nlog10 ≈ ").append(logText(left)).append(" 与 ").append(logText(right));
        return text.toString();
    }

    private static String relative(Natural left, Natural right, int order) {
        if (order == 0) {
            return left + " = " + right;
        }
        return order < 0 ? left + " < " + right : left + " > " + right;
    }

    private static String logText(Natural value) {
        double log = value.log10Approx();
        return log >= 1.0e300 ? "≥1e300" : String.valueOf(Math.round(log * 1000.0) / 1000.0);
    }

    /** The inverses {@code log} and {@code slog}: the base comes first, the value second. */
    private String inverse(String rest, int arrows) {
        String[] parts = twoArguments(rest, arrows == 1 ? "log" : "slog");
        Natural base = evalOne(parts[0], arrows == 1 ? "log" : "slog");
        Natural value = evalOne(parts[1], "值");
        Optional<Natural> result = arrows == 1 ? value.log(base) : value.superLog(base);
        if (result.isEmpty()) {
            return arrows == 1
                    ? "log_" + base + "(" + value + ") 在这个结构分辨率下不可知"
                    : "slog_" + base + "(" + value + ") 在这个结构分辨率下不可知";
        }
        return formatNatural(result.get());
    }

    private String root(String rest) {
        String[] parts = twoArguments(rest, "root");
        Natural degree = evalOne(parts[0], "root");
        Natural value = evalOne(parts[1], "值");
        return formatNatural(value.root(degree).orElseThrow(() -> new CalcError(
                value + " 的 " + degree + " 次方根在结构分辨率下不可知")));
    }

    private String hyperInverse(String rest) {
        List<String> arguments = List.of(rest.trim().split("\\s+"));
        if (arguments.size() != 3) {
            throw new CalcError("hyperinv 需要三个参数：arrows base value");
        }
        int arrows;
        try {
            arrows = Integer.parseInt(arguments.get(0));
        } catch (NumberFormatException error) {
            throw new CalcError("箭头数必须是整数：" + arguments.get(0));
        }
        Natural base = evalOne(arguments.get(1), "hyperinv");
        Natural value = evalOne(arguments.get(2), "hyperinv");
        Optional<Natural> result = value.hyperInverse(base, arrows);
        if (result.isEmpty()) {
            return "hyperInverse(" + base + ", " + arrows + ") 对 " + value + " 不可证，返回空";
        }
        return formatNatural(result.get());
    }

    // ------------------------------------------------------------------ transfinite levels

    private String hyperoperation(String rest) {
        List<String> arguments = List.of(rest.trim().split("\\s+"));
        if (arguments.size() != 3) {
            throw new CalcError("hyp 需要三个参数：base level height（级别写 BMS 矩阵或数字）");
        }
        Natural base = evalOne(arguments.get(0), "hyp");
        Ordinal level = evalLevel(arguments.get(1));
        Natural height = evalOne(arguments.get(2), "hyp");
        Natural value = TransfiniteHyperoperation.apply(base, level, height);
        String levelText = level.finiteValue().isPresent()
                ? level.finiteValue().orElseThrow().toString()
                : ((BmsOrdinal) level).render();
        return formatNatural(value) + "\n级别：" + levelText + "，归约后：" + reducedLevelText(value);
    }

    /** The hyperoperation level: a Bashicu matrix in text form, or a number for a finite level. */
    private static Ordinal evalLevel(String token) {
        Optional<Natural> finite = Natural.parse(token);
        if (finite.isPresent()) {
            return BmsOrdinalNotation.standard().finite(finite.get());
        }
        try {
            return BmsOrdinal.of(BmsMatrix.parse(token));
        } catch (IllegalArgumentException error) {
            throw new CalcError("级别既不是数字也不是 BMS 矩阵：" + token);
        }
    }

    private static String reducedLevelText(Natural value) {
        Optional<Ordinal> reduced = value.reducedLevel();
        if (reduced.isEmpty()) {
            return value.finiteLevel().isPresent()
                    ? "有限级别 " + value.finiteLevel().orElseThrow()
                    : "在预算内归约不完";
        }
        Ordinal level = reduced.get();
        return level instanceof BmsOrdinal ordinal ? ordinal.render() : level.toString();
    }

    private String rank(String rest) {
        Natural value = evalOne(rest, "rank");
        StringBuilder text = new StringBuilder();
        text.append(formatNatural(value));
        text.append("\nrank()：").append(value.rank().map(Ordinal::render).orElse("（精确值或 BEYOND 没有级别）"));
        text.append("\n归约后级别：").append(reducedLevelText(value));
        return text.toString();
    }

    // ------------------------------------------------------------------ ordinals

    private static BmsOrdinal evalOrdinal(String token) {
        try {
            return BmsOrdinal.of(BmsMatrix.parse(token));
        } catch (IllegalArgumentException error) {
            throw new CalcError("不是 BMS 矩阵：" + token + "（例如 (0)(1) 是 ω，(0)(1,1) 是 ε₀）");
        }
    }

    private String ordinal(String rest) {
        BmsOrdinal value = evalOrdinal(rest);
        return value.toString() + "（" + value.render() + "）"
                + (value.finiteValue().isPresent() ? "，有限" : value.isLimit() ? "，极限" : "，后继");
    }

    private String ordinalCompare(String rest) {
        String[] parts = twoArguments(rest, "ordcmp");
        BmsOrdinal left = evalOrdinal(parts[0]);
        BmsOrdinal right = evalOrdinal(parts[1]);
        int order = left.compareTo(right);
        return order == 0 ? left + " = " + right
                : order < 0 ? left + " < " + right : left + " > " + right;
    }

    private String ordinalSequence(String rest) {
        String matrix = rest;
        int count = 5;
        int split = splitIndex(rest);
        if (split >= 0) {
            matrix = rest.substring(0, split).trim();
            try {
                count = Integer.parseInt(rest.substring(split).trim());
            } catch (NumberFormatException error) {
                throw new CalcError("基本列项数必须是整数：" + rest.substring(split).trim());
            }
        }
        if (count < 0 || count > 64) {
            throw new CalcError("基本列项数要在 0 到 64 之间，是 " + count);
        }
        BmsOrdinal value = evalOrdinal(matrix);
        if (!value.isLimit()) {
            return value + " 不是极限序数，没有基本列";
        }
        StringBuilder text = new StringBuilder(value + " 的基本列（ω[m]=m+1 约定，前 " + count + " 项）：");
        for (int index = 0; index < count; index++) {
            Optional<Ordinal> element = value.fundamentalSequence(Natural.of(index));
            if (element.isEmpty()) {
                text.append("\n  [").append(index).append("] 不可物化（实现的上限）");
                continue;
            }
            Ordinal item = element.get();
            String rendered = item instanceof BmsOrdinal ordinal ? ordinal.render() : item.toString();
            text.append("\n  [").append(index).append("] ").append(rendered);
        }
        return text.toString();
    }
}
