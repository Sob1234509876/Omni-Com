package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderArgument;
import io.github.sob1234509876.oc.googology.abstractordinal.LadderOrdinal;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveBms;
import io.github.sob1234509876.oc.googology.cardinalregister.CardinalRung;
import io.github.sob1234509876.oc.googology.cardinalregister.RelativePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The cardinal-register section of the self check: stage four of {@code Ordinals.md} item 3, the register of
 * {@link CardinalRung}s from the inaccessible cardinal to the fictional {@code Omega ~ Omega_Absolute}
 * segment.
 *
 * <p>The checks are structural, because the register is a name-and-rank register and not a notation. They
 * assert what the register claims:
 *
 * <ul>
 *   <li>the declared rank is the enum position, and the enum order is the order of {@code Ordinals.md} items
 *       6-21 (with item 15's I3-I0 range kept as four rungs);</li>
 *   <li>the declared order is total, antisymmetric and transitive over all pairs and all triples, and
 *       {@code compareMagnitude} is always a proof in the same direction;</li>
 *   <li>every rung answers {@link ComparisonResult#GREATER} to
 *       {@link CardinalRung#compareWithLadder(LadderOrdinal)} for every ladder value, including the ladder's
 *       top {@code psi_I(I)} - the register's axiom, and a proof value;</li>
 *   <li>every entry's {@link RelativePath} resolves to a real file <em>and</em> the document contains the
 *       declared section heading, and that heading mentions the rung's name (or the case-insensitive leading
 *       word of it); this is the check that the delegation really lands on a definition;</li>
 *   <li>the unverified flags are exactly the three entries {@code Big Cardinals.md} flags - Icarus,
 *       Ord-Berkeley and limit club Berkeley - and their glosses quote the document's own wording (the
 *       Ord-Berkeley gloss states both readings);</li>
 *   <li>the Berkeley/Reinhardt convention is consistent: Berkeley is above Reinhardt in the declared order,
 *       the separate accessor says Reinhardt is conceptually larger, and the accessor is exactly one ordered
 *       pair rather than a second order;</li>
 *   <li>{@code equals} and {@code hashCode} agree with the comparison.</li>
 * </ul>
 *
 * <p>It reports into the same counters as {@link GoogologyCheck} through its {@link Result}, so the one
 * printed total still counts every check. It reads the three cardinal documents from disk, so it needs the
 * repository working tree, but it needs no network and no third-party library.
 */
public final class CardinalRegisterCheck {

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    private CardinalRegisterCheck() {
    }

    /** The checks that ran and the descriptions that failed, for {@link GoogologyCheck} to merge. */
    public record Result(int passed, List<String> failures) {
    }

    /** Runs the cardinal-register section and returns its counts. */
    public static Result run() {
        passed = 0;
        FAILURES.clear();

        section("32) 基数寄存器：19 个梯级、声明秩与文档委托");
        register();
        definitions();

        section("33) 基数寄存器的声明全序、传递性与 compareMagnitude");
        ordering();

        section("34) 基数对序数阶梯的公理：compareWithLadder 恒为 GREATER");
        aboveLadder();

        section("35) 未核实条目：Icarus、Ord-Berkeley 与 limit club Berkeley");
        unverified();

        section("36) Berkeley/Reinhardt 约定、equals 与 hashCode");
        conventions();

        return new Result(passed, List.copyOf(FAILURES));
    }

    // ------------------------------------------------------------------ 32 the register

    private static void register() {
        CardinalRung[] values = CardinalRung.values();
        equal("基数寄存器有 19 个梯级（Ordinals.md 第 6–21 条，第 15 条的 I3–I0 拆成四级）",
                19, values.length);
        equal("枚举顺序就是 Ordinals.md 第 6–21 条的声明顺序",
                List.of("INACCESSIBLE", "MAHLO", "ALPHA_MAHLO", "HYPER_MAHLO", "WEAKLY_COMPACT",
                        "INDESCRIBABLE", "MEASURABLE", "ALPHA_STRONG_AND_STRONG", "SUPERCOMPACT", "I3", "I2",
                        "I1", "I0", "ICARUS", "REINHARDT", "BERKELEY", "ORD_BERKELEY",
                        "LIMIT_CLUB_BERKELEY", "OMEGA_TO_OMEGA_ABSOLUTE"),
                names(values));

        boolean ranks = true;
        for (int index = 0; index < values.length; index++) {
            if (values[index].rank() != index) {
                ranks = false;
            }
        }
        check("rank() 从 0 起与枚举顺序严格同步（0.." + (values.length - 1) + "）", ranks);

        Set<String> titles = new HashSet<>();
        Set<String> notations = new HashSet<>();
        Set<String> glosses = new HashSet<>();
        boolean text = true;
        for (CardinalRung value : values) {
            if (value.title().isBlank() || value.notation().isBlank() || value.gloss().isBlank()) {
                text = false;
            }
            titles.add(value.title());
            notations.add(value.notation());
            glosses.add(value.gloss());
        }
        check("19 条名字、记号与 gloss 都非空，且各自互不相同", text
                && titles.size() == values.length
                && notations.size() == values.length
                && glosses.size() == values.length);

        boolean delegated = true;
        for (CardinalRung value : values) {
            String path = value.definition().path().toString().replace('\\', '/');
            if (!path.startsWith("documents/Human/Cardinals/")) {
                delegated = false;
            }
        }
        check("19 条定义都委托到 documents/Human/Cardinals/ 之内（本库不另造基数定义）", delegated);
    }

    // ------------------------------------------------------------------ 32 the document links

    private static void definitions() {
        CardinalRung[] values = CardinalRung.values();
        Map<Path, List<String>> headingCache = new HashMap<>();
        Set<String> documents = new HashSet<>();
        for (CardinalRung value : values) {
            RelativePath definition = value.definition();
            documents.add(definition.path().toString().replace('\\', '/'));
            Path file;
            try {
                file = definition.resolve();
            } catch (RuntimeException missingRoot) {
                check("第 " + value.rank() + " 级 " + value.title() + " 的文档路径可解析（"
                        + definition.display() + "）：" + missingRoot.getMessage(), false);
                continue;
            }
            boolean exists = Files.isRegularFile(file);
            List<String> headings = exists
                    ? headingCache.computeIfAbsent(file, CardinalRegisterCheck::readHeadings)
                    : List.of();
            String wanted = normalize(definition.section());
            boolean sectionFound = headings.stream()
                    .map(CardinalRegisterCheck::normalize)
                    .anyMatch(wanted::equals);
            boolean nameFound = headings.stream().anyMatch(heading -> mentionsName(heading, value.title()));
            boolean ok = exists && sectionFound && nameFound;
            check("第 " + value.rank() + " 级 " + value.title() + " → " + definition.display()
                            + "（路径存在=" + exists + "，节标题命中=" + sectionFound
                            + "，名字命中=" + nameFound + "）",
                    ok);
        }
        check("19 个梯级恰好委托给 3 份文档（不可达扩展、Big Cardinals、Fake Googology）",
                documents.equals(Set.of(
                        "documents/Human/Cardinals/Inaccessible Cardinal Extension.md",
                        "documents/Human/Cardinals/Big Cardinals.md",
                        "documents/Human/Cardinals/Fake Googology.md")));

        boolean marker = Files.isRegularFile(
                RelativePath.of("documents/Human/Ordinals.md", "Ordinals").resolve());
        check("RelativePath 以 documents/Human/Ordinals.md 为标记定位到仓库根并解析该文件", marker);
    }

    private static List<String> readHeadings(Path file) {
        try {
            List<String> headings = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                int hashes = 0;
                while (hashes < trimmed.length() && trimmed.charAt(hashes) == '#') {
                    hashes++;
                }
                if (hashes > 0 && hashes < trimmed.length() && trimmed.charAt(hashes) == ' ') {
                    headings.add(trimmed.substring(hashes).strip());
                }
            }
            return List.copyOf(headings);
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    /**
     * The heading mentions the rung: either the whole title appears (case-insensitively), or the leading
     * word does. The leading-word fallback is what lets {@code alpha-Mahlo} match
     * {@code 2. $\alpha$-Mahlo cardinals} and {@code Omega ~ Omega_Absolute} match
     * {@code 1. The joke-ordinal of ordinal size: $\Omega$}.
     */
    private static boolean mentionsName(String heading, String title) {
        String lowerHeading = heading.toLowerCase(Locale.ROOT);
        if (lowerHeading.contains(title.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String leading = leadingWord(title).toLowerCase(Locale.ROOT);
        return !leading.isEmpty() && lowerHeading.contains(leading);
    }

    private static String leadingWord(String text) {
        int start = 0;
        while (start < text.length() && !Character.isLetterOrDigit(text.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < text.length()
                && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_')) {
            end++;
        }
        return text.substring(start, end);
    }

    private static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ 33 ordering

    private static void ordering() {
        CardinalRung[] values = CardinalRung.values();

        int pairs = 0;
        boolean antisymmetric = true;
        boolean signOfRank = true;
        int proofs = 0;
        boolean magnitudeProved = true;
        for (CardinalRung left : values) {
            for (CardinalRung right : values) {
                if (left == right) {
                    continue;
                }
                pairs++;
                int forward = left.compareTo(right);
                int backward = right.compareTo(left);
                if (backward != -forward) {
                    antisymmetric = false;
                }
                if (Integer.signum(forward) != Integer.signum(Integer.compare(left.rank(), right.rank()))) {
                    signOfRank = false;
                }
                proofs++;
                ComparisonResult result = left.compareMagnitude(right);
                if (!result.isProof() || result != ComparisonResult.of(forward)) {
                    magnitudeProved = false;
                }
            }
        }
        check("全部 " + pairs + " 个有序对反向互为相反数，且 compareTo 的符号始终等于 rank 之差",
                antisymmetric && signOfRank && pairs == 342);
        check("全部 " + proofs + " 次 compareMagnitude 都是与 compareTo 同向的证明（没有 UNKNOWN / PROBABLE_*）",
                magnitudeProved && proofs == 342);

        int triples = 0;
        boolean transitive = true;
        for (CardinalRung first : values) {
            for (CardinalRung second : values) {
                for (CardinalRung third : values) {
                    triples++;
                    if (first.compareTo(second) < 0
                            && second.compareTo(third) < 0
                            && first.compareTo(third) >= 0) {
                        transitive = false;
                    }
                }
            }
        }
        check("全部 " + triples + " 个三元组满足传递性", transitive && triples == 6859);

        boolean selfZero = true;
        for (CardinalRung value : values) {
            if (value.compareTo(value) != 0
                    || value.compareMagnitude(value) != ComparisonResult.EQUAL
                    || !value.equals(value)
                    || value.hashCode() != value.hashCode()) {
                selfZero = false;
            }
        }
        check("自比较为 0、compareMagnitude 给 EQUAL、equals 与 hashCode 自反", selfZero);

        boolean adjacent = true;
        for (int index = 1; index < values.length; index++) {
            if (values[index - 1].compareTo(values[index]) >= 0) {
                adjacent = false;
            }
        }
        check("相邻梯级严格递增（18 对），rank 顺序就是排序结果", adjacent);

        List<CardinalRung> sorted = new ArrayList<>(List.of(values));
        Collections.reverse(sorted);
        sorted.sort(CardinalRung::compareTo);
        check("Collections.sort 按声明秩恢复枚举顺序", List.of(values).equals(sorted));

        check("compareTo / compareMagnitude 拒绝 null",
                throwsNullPointer(() -> CardinalRung.INACCESSIBLE.compareTo(null))
                        && throwsNullPointer(() -> CardinalRung.INACCESSIBLE.compareMagnitude(null)));
    }

    // ------------------------------------------------------------------ 34 above the ladder

    private static void aboveLadder() {
        List<LadderOrdinal> ladder = ladderSample();
        int comparisons = 0;
        boolean greater = true;
        boolean proofs = true;
        for (CardinalRung rung : CardinalRung.values()) {
            for (LadderOrdinal value : ladder) {
                comparisons++;
                ComparisonResult result = rung.compareWithLadder(value);
                if (result != ComparisonResult.GREATER) {
                    greater = false;
                }
                if (!result.isProof()) {
                    proofs = false;
                }
            }
        }
        check("19 个梯级 × " + ladder.size() + " 个阶梯值共 " + comparisons
                + " 次 compareWithLadder 全部给 GREATER", greater && comparisons == 19 * ladder.size());
        check("GREATER 是证明值（isProof），不是倾向也不是 UNKNOWN", proofs
                && ComparisonResult.GREATER.isProof()
                && !ComparisonResult.GREATER.isProbable()
                && !ComparisonResult.GREATER.isUnknown());

        check("I 严格高于序数阶梯的顶端 ψ_𝕀(𝕀)",
                CardinalRung.INACCESSIBLE.compareWithLadder(LadderOrdinal.psiII())
                        == ComparisonResult.GREATER);
        check("I 高于阶梯里的每一个值（含 ψ 与 0，按公理而非计算）",
                ladder.stream().allMatch(value -> CardinalRung.INACCESSIBLE.compareWithLadder(value)
                        == ComparisonResult.GREATER));

        boolean predicates = true;
        for (CardinalRung rung : CardinalRung.values()) {
            if (!rung.isAboveLadder()) {
                predicates = false;
            }
        }
        check("isAboveLadder() 对每个梯级都为 true（compareWithLadder 的谓词形式）", predicates);
        check("compareWithLadder 拒绝 null",
                throwsNullPointer(() -> CardinalRung.INACCESSIBLE.compareWithLadder(null)));
    }

    private static List<LadderOrdinal> ladderSample() {
        return List.of(
                LadderOrdinal.psi(),
                LadderOrdinal.zero(),
                LadderOrdinal.omega(),
                LadderOrdinal.sho(),
                LadderOrdinal.f(LadderArgument.omega()),
                LadderOrdinal.lrpo(),
                LadderOrdinal.omegaOneCk(),
                LadderOrdinal.ofNrbms(NonRecursiveBms.parse("(1,1,1)(2,2)")),
                LadderOrdinal.limNrbms(),
                LadderOrdinal.tnrbms(LadderArgument.limNrbms()),
                LadderOrdinal.tnrbmsFixpoint(),
                LadderOrdinal.omegaAlpha(LadderArgument.sho()),
                LadderOrdinal.psiII());
    }

    // ------------------------------------------------------------------ 35 unverified

    private static void unverified() {
        Set<CardinalRung> unverified = EnumSet.noneOf(CardinalRung.class);
        int verified = 0;
        for (CardinalRung rung : CardinalRung.values()) {
            if (rung.isVerified()) {
                verified++;
            } else {
                unverified.add(rung);
            }
        }
        check("未核实标记恰好是 Icarus、Ord-Berkeley 与 limit club Berkeley",
                unverified.equals(EnumSet.of(CardinalRung.ICARUS, CardinalRung.ORD_BERKELEY,
                        CardinalRung.LIMIT_CLUB_BERKELEY)));
        check("16 条已核实、3 条未核实，恰好覆盖 19 条", verified == 16 && unverified.size() == 3);

        String icarus = CardinalRung.ICARUS.gloss().toLowerCase(Locale.ROOT);
        check("Icarus 的 gloss 复用文档措辞（best formulation available / flagged for verification /"
                        + " not verified against a primary definition）",
                icarus.contains("best formulation available")
                        && icarus.contains("flagged for verification")
                        && icarus.contains("not verified against a primary definition"));

        String ordBerkeley = CardinalRung.ORD_BERKELEY.gloss().toLowerCase(Locale.ROOT);
        check("Ord-Berkeley 的 gloss 同时给出两种读法（unbounded reading 与 cumulative-hierarchy reading /"
                        + " rank-Berkeley），并明说没有选定一种",
                ordBerkeley.contains("two different ways")
                        && ordBerkeley.contains("unbounded reading")
                        && ordBerkeley.contains("cumulative-hierarchy reading")
                        && ordBerkeley.contains("rank-berkeley")
                        && ordBerkeley.contains("must not be read as settling"));

        String limitClub = CardinalRung.LIMIT_CLUB_BERKELEY.gloss().toLowerCase(Locale.ROOT);
        check("limit club Berkeley 的 gloss 复用文档措辞（reconstructed / flagged for verification /"
                        + " unparametrized form 与 limit clause 未核实）",
                limitClub.contains("reconstructed")
                        && limitClub.contains("flagged for verification")
                        && limitClub.contains("unparametrized")
                        && limitClub.contains("limit of club berkeley cardinals"));

        check("三个未核实条目各自指向 Big Cardinals 的第 14、17、18 节",
                CardinalRung.ICARUS.definition().section().equals("14. Icarus cardinals")
                        && CardinalRung.ORD_BERKELEY.definition().section()
                                .equals("17. Ord-Berkeley cardinals")
                        && CardinalRung.LIMIT_CLUB_BERKELEY.definition().section()
                                .equals("18. Limit club Berkeley cardinals"));
    }

    // ------------------------------------------------------------------ 36 convention and equality

    private static void conventions() {
        check("声明顺序里 Berkeley 在 Reinhardt 之上（一致性强度）",
                CardinalRung.BERKELEY.rank() > CardinalRung.REINHARDT.rank()
                        && CardinalRung.BERKELEY.compareTo(CardinalRung.REINHARDT) > 0
                        && CardinalRung.BERKELEY.compareMagnitude(CardinalRung.REINHARDT)
                                == ComparisonResult.GREATER);

        check("单独的约定访问器说 Reinhardt 在概念上更大",
                CardinalRung.reinhardtIsConceptuallyLargerThanBerkeley()
                        && CardinalRung.REINHARDT.isConceptuallyLargerThan(CardinalRung.BERKELEY)
                        && !CardinalRung.BERKELEY.isConceptuallyLargerThan(CardinalRung.REINHARDT));

        int conceptualPairs = 0;
        for (CardinalRung left : CardinalRung.values()) {
            for (CardinalRung right : CardinalRung.values()) {
                if (left != right && left.isConceptuallyLargerThan(right)) {
                    conceptualPairs++;
                }
            }
        }
        check("概念大小访问器只记录 (REINHARDT, BERKELEY) 这一个有序对（342 个有序对中恰好 1 个），"
                        + "不是第二个全序",
                conceptualPairs == 1);

        check("约定访问器不改变声明顺序：Berkeley 仍严格在 Reinhardt 之上",
                CardinalRung.BERKELEY.compareTo(CardinalRung.REINHARDT) > 0
                        && CardinalRung.REINHARDT.compareTo(CardinalRung.BERKELEY) < 0);

        CardinalRung[] values = CardinalRung.values();
        boolean equality = true;
        for (CardinalRung left : values) {
            for (CardinalRung right : values) {
                boolean equal = left == right;
                if ((left.compareTo(right) == 0) != equal
                        || left.equals(right) != equal
                        || (equal && left.hashCode() != right.hashCode())) {
                    equality = false;
                }
            }
        }
        check("equals/hashCode 与比较一致：compareTo == 0 当且仅当 equals，相等对象哈希相同",
                equality && !CardinalRung.INACCESSIBLE.equals(null)
                        && !CardinalRung.INACCESSIBLE.equals("INACCESSIBLE"));
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> names(CardinalRung[] values) {
        List<String> names = new ArrayList<>();
        for (CardinalRung value : values) {
            names.add(value.name());
        }
        return List.copyOf(names);
    }

    private static boolean throwsNullPointer(Runnable action) {
        try {
            action.run();
            return false;
        } catch (NullPointerException expected) {
            return true;
        }
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [ok] " + description);
        } else {
            FAILURES.add(description);
            System.out.println("  [FAIL] " + description);
        }
    }

    private static void equal(String description, Object expected, Object actual) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        check(description + "（期望 " + expected + "，实际 " + actual + "）", same);
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }
}
