package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.CantorNormalForm;
import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.bms.BmsMatrix;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
 * The BMS section of the self check: canonicalisation, standard-form detection, the three worked examples
 * of the task definition, transitivity and antisymmetry on fixed and fixed-seed matrices, the reference's
 * known ordinal nodes, and the rendering round trip.
 *
 * <p>It is a separate class so that the matrix layer reads on its own, but it reports into the same
 * counters as {@link GoogologyCheck}, so the single printed total still counts every check.
 */
public final class BmsCheck {

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    private BmsCheck() {
    }

    /** The checks that ran and the descriptions that failed, for {@link GoogologyCheck} to merge. */
    public record Result(int passed, List<String> failures) {
    }

    /** Runs the BMS section and returns its counts. */
    public static Result run() {
        passed = 0;
        FAILURES.clear();

        section("16) BMS 标准式与规范形（Bashicu 矩阵序数）");
        matrixShape();
        canonicalisation();
        standardFormDetection();
        theThreeExamples();
        ordering();
        referenceIdentities();
        rendering();

        return new Result(passed, List.copyOf(FAILURES));
    }

    // ------------------------------------------------------------------ matrix shape

    private static void matrixShape() {
        BmsMatrix matrix = BmsMatrix.of(new int[][]{{0}, {1, 1}, {2}});
        equal("BMS 的书写形式：列用括号、行用逗号", "(0)(1,1)(2)", matrix.toString());
        check("rows 是最高的一列（(1,1) 有两行）", matrix.rows() == 2);
        check("length 是列数", matrix.length() == 3 && matrix.columns().size() == 3);
        equal("第一列原样保留", List.of(0), matrix.columns().get(0));
        equal("第二列保留两行", List.of(1, 1), matrix.columns().get(1));
        check("of 只接受规范写法：列末的 0 被拒绝",
                throwsIllegalArgument(() -> BmsMatrix.of(new int[][]{{0}, {1, 0}})));
        check("空列被拒绝", throwsIllegalArgument(() -> BmsMatrix.of(new int[][]{{0}, {}})));
        check("负数不是自然数", throwsIllegalArgument(() -> BmsMatrix.of(new int[][]{{0}, {-1}})));
        check("canonical 同样拒绝空列", throwsIllegalArgument(() -> BmsMatrix.canonical(new int[][]{{0}, {}})));
        check("矩阵不可变：外层与内层列表都不能改",
                throwsUnsupported(() -> matrix.columns().add(List.of(0)))
                        && throwsUnsupported(() -> matrix.columns().get(0).add(1)));
        check("空矩阵合法：长度 0、行数 0、渲染 ()",
                BmsMatrix.canonical(new int[0][]).isEmpty()
                        && BmsMatrix.canonical(new int[0][]).length() == 0
                        && BmsMatrix.canonical(new int[0][]).rows() == 0
                        && BmsMatrix.canonical(new int[0][]).toString().equals("()"));
    }

    // ------------------------------------------------------------------ canonicalisation

    private static void canonicalisation() {
        BmsMatrix oneRow = BmsMatrix.of(new int[][]{{0}, {1}, {2}});
        BmsMatrix twoRows = BmsMatrix.canonical(new int[][]{{0, 0}, {1, 0}, {2, 0}});
        BmsMatrix threeRows = BmsMatrix.canonical(new int[][]{{0, 0, 0}, {1, 0, 0}, {2, 0, 0}});
        check("TODO：BMS(0)(1)(2) = BMS(0,0)(1,0)(2,0)", oneRow.equals(twoRows));
        check("TODO：并且 = BMS(0,0,0)(1,0,0)(2,0,0)", oneRow.equals(threeRows));
        check("规范形相等则哈希也相等",
                oneRow.hashCode() == twoRows.hashCode() && twoRows.hashCode() == threeRows.hashCode());
        check("isStandard 报告输入是否已经规范", oneRow.isStandard() && !twoRows.isStandard() && !threeRows.isStandard());
        equal("补出来的 0 行被删掉：三行写法渲染成一行", "(0)(1)(2)", threeRows.toString());
        check("规范形的 rows 是删零后的高度（这里是 1）", threeRows.rows() == 1);
        equal("全零列折叠成 (0)", "(0)", BmsMatrix.canonical(new int[][]{{0, 0, 0}}).toString());
        check("首列的补零同样折叠，所以 (0)(1,1) 与 (0,0)(1,1) 是同一个矩阵",
                BmsMatrix.of(new int[][]{{0}, {1, 1}})
                        .equals(BmsMatrix.canonical(new int[][]{{0, 0}, {1, 1}})));
        equal("builder 与 canonical 等价", twoRows,
                BmsMatrix.builder().column(0, 0).column(1, 0).column(2, 0).build());
        equal("parse 也做规范化", BmsMatrix.canonical(new int[][]{{0}, {1, 1}, {2, 0}}),
                BmsMatrix.parse("(0)(1,1)(2,0)"));
        check("parse 的 isStandard 反映文本是否规范",
                !BmsMatrix.parse("(0)(1,1)(2,0)").isStandard()
                        && BmsMatrix.parse("(0)(1,1)(2)").isStandard());
        check("parse 拒绝未闭合的列", throwsIllegalArgument(() -> BmsMatrix.parse("(0)(1")));
        check("parse 拒绝空列（() 只表示整个空矩阵）", throwsIllegalArgument(() -> BmsMatrix.parse("(0)()")));
        check("空矩阵的渲染往返", BmsMatrix.parse("()").equals(BmsMatrix.canonical(new int[0][])));
    }

    // ------------------------------------------------------------------ standard form detection

    private static void standardFormDetection() {
        check("BmsOrdinal 拒绝首列不全零的矩阵",
                throwsIllegalArgument(() -> BmsOrdinal.of(new int[][]{{1}, {2}})));
        check("BmsOrdinal 规范化首列的补零",
                BmsOrdinal.of(new int[][]{{0, 0}, {1, 1}})
                        .equals(BmsOrdinal.of(new int[][]{{0}, {1, 1}})));
        check("(0)(0) 是 2，第二列 (0) 没有被当成冗余删掉",
                BmsOrdinal.of(new int[][]{{0}, {0}}).length() == 2);
        check("深层标准式条件不由本类判定：首列为零的矩阵会被接受，标准性由调用方保证",
                BmsOrdinal.of(new int[][]{{0}, {1, 1}, {3}}).length() == 3);
        check("BmsOrdinal 的空矩阵就是 0", BmsOrdinal.of(new int[0][]).isEmpty()
                && BmsOrdinal.of(new int[0][]).toString().equals("()"));
    }

    // ------------------------------------------------------------------ the three worked examples

    private static void theThreeExamples() {
        BmsOrdinal a = BmsOrdinal.of(new int[][]{{0}, {1, 1}});
        BmsOrdinal b = BmsOrdinal.of(new int[][]{{0}, {1}, {2}});
        BmsOrdinal c = BmsOrdinal.of(new int[][]{{0}, {1, 0}});

        check("例一：A = BMS(0)(1,1) > B = BMS(0)(1)(2)", a.compareTo(b) > 0);
        equal("例一：compareMagnitude 给出证明 GREATER", ComparisonResult.GREATER, a.compareMagnitude(b));
        equal("例二：B 的三种写法给出同一个矩阵", b.matrix(),
                BmsOrdinal.of(new int[][]{{0, 0, 0}, {1, 0, 0}, {2, 0, 0}}).matrix());
        check("例二：三种写法的序数相等",
                b.compareTo(BmsOrdinal.of(new int[][]{{0, 0}, {1, 0}, {2, 0}})) == 0
                        && b.compareTo(BmsOrdinal.of(new int[][]{{0, 0, 0}, {1, 0, 0}, {2, 0, 0}})) == 0);
        equal("例三：C = BMS(0)(1,0) 规范化成 BMS(0)(1)", BmsOrdinal.of(new int[][]{{0}, {1}}), c);
        check("例三：B > C，因为前两列相等而 B 的第三列先耗尽比较", b.compareTo(c) > 0);
        check("例三：C 只有两列，B 有三列，且两者不相等",
                c.length() == 2 && b.length() == 3 && !c.equals(b));
        check("例三：原始列 (1,0) 比 (1) 长，但规范化后 C 的第二列就是 (1)",
                c.matrix().columns().get(1).equals(List.of(1)));
    }

    // ------------------------------------------------------------------ order

    private static void ordering() {
        List<BmsOrdinal> chain = List.of(
                BmsOrdinal.of(new int[0][]),                                        // 0
                BmsOrdinal.of(new int[][]{{0}}),                                    // 1
                BmsOrdinal.of(new int[][]{{0}, {0}}),                               // 2
                BmsOrdinal.of(new int[][]{{0}, {1}}),                               // ω
                BmsOrdinal.of(new int[][]{{0}, {1}, {0}}),                          // ω + 1
                BmsOrdinal.of(new int[][]{{0}, {1}, {1}}),                          // ω²
                BmsOrdinal.of(new int[][]{{0}, {1}, {2}}),                          // ω^ω
                BmsOrdinal.of(new int[][]{{0, 0}, {1, 1}}),                         // ε₀
                BmsOrdinal.of(new int[][]{{0, 0}, {1, 1}, {1, 1}}),                 // ε₁
                BmsOrdinal.of(new int[][]{{0, 0}, {1, 1}, {2, 0}}),                 // ε_ω
                BmsOrdinal.of(new int[][]{{0, 0}, {1, 1}, {2, 1}}));                // ζ₀
        check("参考节点严格递增：0 < 1 < 2 < ω < ω+1 < ω² < ω^ω < ε₀ < ε₁ < ε_ω < ζ₀",
                strictlyIncreasing(chain));
        check("参考节点两两反对称，且只有同一个矩阵才比出 0（" + (chain.size() * chain.size()) + " 对）",
                antisymmetric(chain));
        check("参考节点上所有三元组都传递（" + triples(chain.size()) + " 组）", transitive(chain));
        check("参考节点上 compareMagnitude 总是证明，而且方向与 compareTo 一致", allProofs(chain));

        List<BmsOrdinal> random = new ArrayList<>(new LinkedHashSet<>(randomOrdinals(new Random(20261024L), 12)));
        random.sort(BmsOrdinal::compareTo);
        int triples = triples(random.size());
        check("固定种子的 12 个矩阵去重排序后两两递增（非降序），" + triples + " 组三元组全部传递",
                nonDecreasing(random) && transitive(random));
        check("固定种子的矩阵上反对称，相等当且仅当 compareTo 为 0", antisymmetric(random));
        check("固定种子的矩阵上 compareMagnitude 全部是证明", allProofs(random));

        List<BmsOrdinal> sorted = new ArrayList<>(chain);
        sorted.sort(BmsOrdinal::compareTo);
        check("Collections.sort 把参考节点排成已知的序", sorted.equals(chain));
        BmsOrdinal padded = BmsOrdinal.of(new int[][]{{0, 0}, {1, 1, 0}});
        BmsOrdinal original = BmsOrdinal.of(new int[][]{{0}, {1, 1}});
        check("补零写法与规范写法相等、哈希相同、compareTo 为 0",
                padded.equals(original) && padded.hashCode() == original.hashCode()
                        && padded.compareTo(original) == 0);
    }

    // ------------------------------------------------------------------ reference nodes

    private static void referenceIdentities() {
        CantorNormalForm notation = CantorNormalForm.standard();
        List<Ordinal> ordinals = List.of(
                notation.one(),                                    // (0)       = 1
                notation.of(2),                                    // (0)(0)    = 2
                notation.omega(),                                  // (0)(1)    = ω
                notation.omegaPower(notation.of(2)),               // (0)(1)(1) = ω²
                notation.omegaPower(notation.omega()),             // (0)(1)(2) = ω^ω
                notation.limit());                                 // (0,0)(1,1) = ε₀
        List<BmsOrdinal> matrices = List.of(
                BmsOrdinal.of(new int[][]{{0}}),
                BmsOrdinal.of(new int[][]{{0}, {0}}),
                BmsOrdinal.of(new int[][]{{0}, {1}}),
                BmsOrdinal.of(new int[][]{{0}, {1}, {1}}),
                BmsOrdinal.of(new int[][]{{0}, {1}, {2}}),
                BmsOrdinal.of(new int[][]{{0, 0}, {1, 1}}));
        int pairs = 0;
        boolean agrees = true;
        for (int left = 0; left < matrices.size(); left++) {
            for (int right = 0; right < matrices.size(); right++) {
                int byMatrix = matrices.get(left).compareTo(matrices.get(right));
                int byOrdinal = ordinals.get(left).compareTo(ordinals.get(right));
                agrees &= Integer.signum(byMatrix) == Integer.signum(byOrdinal);
                pairs++;
            }
        }
        check("参考节点与康托尔序数同序（(0)=1、(0)(0)=2、(0)(1)=ω、(0)(1)(1)=ω²、"
                + "(0)(1)(2)=ω^ω、(0,0)(1,1)=ε₀；" + pairs + " 对）", agrees);
        check("参考节点的相邻关系：(0)(1)(1) < (0)(1)(2) < (0,0)(1,1) < (0,0)(1,1)(1,1)",
                matrices.get(3).compareTo(matrices.get(4)) < 0
                        && matrices.get(4).compareTo(matrices.get(5)) < 0
                        && matrices.get(5).compareTo(BmsOrdinal.of(new int[][]{{0, 0}, {1, 1}, {1, 1}})) < 0);
    }

    // ------------------------------------------------------------------ rendering

    private static void rendering() {
        List<BmsMatrix> matrices = List.of(
                BmsMatrix.canonical(new int[0][]),
                BmsMatrix.of(new int[][]{{0}}),
                BmsMatrix.of(new int[][]{{0}, {0}}),
                BmsMatrix.of(new int[][]{{0}, {1}}),
                BmsMatrix.of(new int[][]{{0}, {1, 1}}),
                BmsMatrix.of(new int[][]{{0}, {1}, {2}}),
                BmsMatrix.canonical(new int[][]{{0, 0}, {1, 1}}),
                BmsMatrix.canonical(new int[][]{{0, 0}, {1, 1}, {2, 0}}));
        boolean roundTrips = true;
        for (BmsMatrix matrix : matrices) {
            BmsMatrix parsed = BmsMatrix.parse(matrix.toString());
            roundTrips &= parsed.equals(matrix) && parsed.toString().equals(matrix.toString());
        }
        check("渲染往返：parse(toString(m)) = m 且再次渲染不变（" + matrices.size() + " 个矩阵）", roundTrips);
        check("BmsOrdinal.toString 与矩阵相同",
                BmsOrdinal.of(new int[][]{{0}, {1}, {2}}).toString().equals("(0)(1)(2)")
                        && BmsOrdinal.of(new int[0][]).toString().equals("()"));
        check("BmsOrdinal 的 rows/length 委托给矩阵",
                BmsOrdinal.of(new int[][]{{0, 0}, {1, 1}, {2, 0}}).rows() == 2
                        && BmsOrdinal.of(new int[][]{{0, 0}, {1, 1}, {2, 0}}).length() == 3);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean strictlyIncreasing(List<BmsOrdinal> values) {
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index - 1).compareTo(values.get(index)) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean nonDecreasing(List<BmsOrdinal> values) {
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index - 1).compareTo(values.get(index)) > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean antisymmetric(List<BmsOrdinal> values) {
        for (int left = 0; left < values.size(); left++) {
            for (int right = 0; right < values.size(); right++) {
                int forward = values.get(left).compareTo(values.get(right));
                int backward = values.get(right).compareTo(values.get(left));
                if (Integer.signum(forward) != -Integer.signum(backward)) {
                    return false;
                }
                if ((forward == 0) != values.get(left).equals(values.get(right))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean transitive(List<BmsOrdinal> values) {
        for (int first = 0; first < values.size(); first++) {
            for (int second = first + 1; second < values.size(); second++) {
                for (int third = second + 1; third < values.size(); third++) {
                    if (values.get(first).compareTo(values.get(second)) >= 0
                            || values.get(second).compareTo(values.get(third)) >= 0
                            || values.get(first).compareTo(values.get(third)) >= 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean allProofs(List<BmsOrdinal> values) {
        for (BmsOrdinal left : values) {
            for (BmsOrdinal right : values) {
                ComparisonResult result = left.compareMagnitude(right);
                if (!result.isProof()
                        || result.signum() != Integer.signum(left.compareTo(right))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int triples(int size) {
        return size * (size - 1) * (size - 2) / 6;
    }

    /**
     * Deterministic pseudo-random standard-shaped matrices: a zero first column and a few columns of a few
     * small rows. The values are not checked for deep standardness - transitivity is a property of the
     * lexicographic order itself - but they exercise mixed heights and lengths.
     */
    private static List<BmsOrdinal> randomOrdinals(Random random, int count) {
        List<BmsOrdinal> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int columnCount = 1 + random.nextInt(4);
            int[][] raw = new int[columnCount][];
            raw[0] = new int[1 + random.nextInt(3)];
            for (int column = 1; column < columnCount; column++) {
                int[] valuesOfColumn = new int[1 + random.nextInt(3)];
                for (int row = 0; row < valuesOfColumn.length; row++) {
                    valuesOfColumn[row] = random.nextInt(4);
                }
                raw[column] = valuesOfColumn;
            }
            values.add(BmsOrdinal.of(raw));
        }
        return values;
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static boolean throwsUnsupported(Runnable action) {
        try {
            action.run();
            return false;
        } catch (UnsupportedOperationException expected) {
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
