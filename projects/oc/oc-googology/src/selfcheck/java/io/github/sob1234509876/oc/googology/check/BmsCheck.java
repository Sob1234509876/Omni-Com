package io.github.sob1234509876.oc.googology.check;

import io.github.sob1234509876.oc.googology.CantorNormalForm;
import io.github.sob1234509876.oc.googology.ComparisonResult;
import io.github.sob1234509876.oc.googology.Ordinal;
import io.github.sob1234509876.oc.googology.bms.BmsMatrix;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveBms;
import io.github.sob1234509876.oc.googology.bms.NonRecursiveExpansion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * The BMS section of the self check: canonicalisation, standard-form detection, the three worked examples
 * of the task definition, transitivity and antisymmetry on fixed and fixed-seed matrices, the reference's
 * known ordinal nodes, the rendering round trip, the expansion rules (the article's worked examples, the
 * golden cases, the ancestor exception, and the {@code S > S[n]} / {@code S[0] < S[1] < ...} consistency
 * property), and the non-recursive BMS variant with its two golden equalities and its non-recursive
 * expansion.
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

        section("17) BMS 展开：基本列、G/B/Δ 与祖先例外");
        expansionExamples();
        expansionConsistency();

        section("18) 非递归 BMS（NRBMS）");
        nonRecursiveBms();

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

    // ------------------------------------------------------------------ expansion examples

    /**
     * The article's three worked examples, plus cases derived mechanically from the same rules. The three
     * article examples are the authority for the rule: example two exercises the ordinary ascension, example
     * three exercises the ancestor exception (the fifth column's third row stays 0 instead of moving), and
     * example one is the no-bad-root successor. The "[推导]" cases are not quoted from the article - they are
     * consequences of the same rules, checked here so a regression in the rule shows up on a small matrix.
     */
    private static void expansionExamples() {
        BmsOrdinal exampleTwo = bms("(0)(1,1,1)(2,2,2)(3,3,3)(4,2)");
        expansionEquals("BMS 例二：S[0] 是 G = (0)", exampleTwo, 0, "(0)");
        expansionEquals("BMS 例二：S[1] 是 G ~ B", exampleTwo, 1,
                "(0)(1,1,1)(2,2,2)(3,3,3)");
        expansionEquals("BMS 例二：S[2] 增加 B_1（Δ=(3,0,0)，没有祖先例外）", exampleTwo, 2,
                "(0)(1,1,1)(2,2,2)(3,3,3)(4,1,1)(5,2,2)(6,3,3)");
        expansionEquals("BMS 例二：S[3] 增加 B_2", exampleTwo, 3,
                "(0)(1,1,1)(2,2,2)(3,3,3)(4,1,1)(5,2,2)(6,3,3)(7,1,1)(8,2,2)(9,3,3)");

        BmsOrdinal exampleThree = bms("(0)(1,1,1,1)(2,2,2,1)(3,3,1,1)(4,2)(5,1,1,1)(6,2,2,1)(7,3,1,1)");
        expansionEquals("BMS 例三：G 为空，S[0] = 0", exampleThree, 0, "()");
        expansionEquals("BMS 例三：S[1] 是坏部 B 本身", exampleThree, 1,
                "(0)(1,1,1,1)(2,2,2,1)(3,3,1,1)(4,2)(5,1,1,1)(6,2,2,1)");
        expansionEquals("BMS 例三：S[2] 增加 B_1（文章给出的展开）", exampleThree, 2,
                "(0)(1,1,1,1)(2,2,2,1)(3,3,1,1)(4,2)(5,1,1,1)(6,2,2,1)"
                        + "(7,3,1)(8,4,2,1)(9,5,3,1)(10,6,2,1)(11,5)(12,4,2,1)(13,5,3,1)");
        expansionEquals("BMS 例三：S[3] 增加 B_2", exampleThree, 3,
                "(0)(1,1,1,1)(2,2,2,1)(3,3,1,1)(4,2)(5,1,1,1)(6,2,2,1)"
                        + "(7,3,1)(8,4,2,1)(9,5,3,1)(10,6,2,1)(11,5)(12,4,2,1)(13,5,3,1)"
                        + "(14,6,2)(15,7,3,1)(16,8,4,1)(17,9,3,1)(18,8)(19,7,3,1)(20,8,4,1)");
        check("BMS 例三的祖先例外：B 第五列第三行的 0 的祖先不经过坏根，所以 B_1 的这一列是 (11,5) 而不是 (11,5,1)",
                exampleThree.fundamentalSequence(2).orElseThrow().matrix().columns().get(11)
                        .equals(List.of(11, 5)));

        BmsOrdinal exampleOne = bms("(0)(1,1,1)(2,2,1)(3,2)(0)");
        check("BMS 例一：末列全零，没有坏根，是后继", exampleOne.isSuccessor() && !exampleOne.isLimit());
        check("BMS 例一：后继没有基本列", exampleOne.fundamentalSequence(0).isEmpty()
                && exampleOne.fundamentalSequence(3).isEmpty());
        equal("BMS 例一：前驱是去掉末列", bms("(0)(1,1,1)(2,2,1)(3,2)"),
                exampleOne.predecessor().orElseThrow());
        equal("BMS 例一：前驱的后继回到原式", exampleOne, exampleOne.predecessor().orElseThrow().successor());

        BmsOrdinal zero = bms("()");
        check("空矩阵是 0：没有基本列，也不是后继", zero.isZero() && zero.fundamentalSequence(0).isEmpty()
                && !zero.isSuccessor());
        equal("0 的后继是 (0)", bms("(0)"), zero.successor());
        check("(0) 是后继，前驱是 0", bms("(0)").isSuccessor()
                && bms("(0)").predecessor().orElseThrow().isZero());
        check("(0)(0) 是 2，也是后继，前驱是 (0)", bms("(0)(0)").isSuccessor()
                && bms("(0)(0)").predecessor().orElseThrow().equals(bms("(0)")));
        check("负的基本列下标被拒绝", throwsIllegalArgument(() -> zero.fundamentalSequence(-1)));
        check("对零和后继调用 expand 会抛 IllegalStateException",
                throwsIllegalState(() -> zero.expand(0)) && throwsIllegalState(() -> bms("(0)").expand(0)));

        // Derived cases: the article gives the nodes in its strength table, the finite elements follow
        // mechanically from the expansion rules above.
        BmsOrdinal omega = bms("(0)(1)");
        check("BMS 的 (0)(1) 末列是 (1)，但它是 ω 极限而不是后继（单 (1) 后继规则属于 NRBMS）",
                !omega.isSuccessor() && omega.isLimit());
        check("BMS 的后继是在末尾加 (0)：(0)(1)(0) 是 (0)(1) 的后继",
                bms("(0)(1)(0)").isSuccessor()
                        && bms("(0)(1)(0)").predecessor().orElseThrow().equals(omega));
        expansionEquals("[推导] (0)(1)=ω：S[0] = 0", omega, 0, "()");
        expansionEquals("[推导] (0)(1)=ω：S[1] = 1", omega, 1, "(0)");
        expansionEquals("[推导] (0)(1)=ω：S[2] = 2", omega, 2, "(0)(0)");
        expansionEquals("[推导] (0)(1)=ω：S[3] = 3", omega, 3, "(0)(0)(0)");

        BmsOrdinal omegaSquared = bms("(0)(1)(1)");
        expansionEquals("[推导] (0)(1)(1)=ω²：S[0] = 0", omegaSquared, 0, "()");
        expansionEquals("[推导] (0)(1)(1)=ω²：S[1] = ω", omegaSquared, 1, "(0)(1)");
        expansionEquals("[推导] (0)(1)(1)=ω²：S[2] = ω·2", omegaSquared, 2, "(0)(1)(0)(1)");
        expansionEquals("[推导] (0)(1)(1)=ω²：S[3] = ω·3", omegaSquared, 3, "(0)(1)(0)(1)(0)(1)");

        BmsOrdinal epsilonZero = bms("(0)(1,1)");
        expansionEquals("[推导] (0)(1,1)=ε₀：S[0] = 0", epsilonZero, 0, "()");
        expansionEquals("[推导] (0)(1,1)=ε₀：S[1] = 1", epsilonZero, 1, "(0)");
        expansionEquals("[推导] (0)(1,1)=ε₀：S[2] = ω", epsilonZero, 2, "(0)(1)");
        expansionEquals("[推导] (0)(1,1)=ε₀：S[3] = ω^ω", epsilonZero, 3, "(0)(1)(2)");
        expansionEquals("[推导] (0)(1,1)=ε₀：S[4] = ω^ω^ω", epsilonZero, 4, "(0)(1)(2)(3)");

        BmsOrdinal epsilonOmega = bms("(0)(1,1)(2)");
        expansionEquals("[推导] (0)(1,1)(2)=ε_ω：S[0] = 1", epsilonOmega, 0, "(0)");
        expansionEquals("[推导] (0)(1,1)(2)=ε_ω：S[1] = ε₀", epsilonOmega, 1, "(0)(1,1)");
        expansionEquals("[推导] (0)(1,1)(2)=ε_ω：S[2] = ε₁", epsilonOmega, 2, "(0)(1,1)(1,1)");
        expansionEquals("[推导] (0)(1,1)(2)=ε_ω：S[3] = ε₂", epsilonOmega, 3, "(0)(1,1)(1,1)(1,1)");

        BmsOrdinal omegaOmega = bms("(0)(1,1,1)");
        expansionEquals("[推导] (0)(1,1,1)=ψ(Ω_ω)：S[0] = 0", omegaOmega, 0, "()");
        expansionEquals("[推导] (0)(1,1,1)=ψ(Ω_ω)：S[1] = 1", omegaOmega, 1, "(0)");
        expansionEquals("[推导] (0)(1,1,1)=ψ(Ω_ω)：S[2] = (0)(1,1)", omegaOmega, 2, "(0)(1,1)");
        expansionEquals("[推导] (0)(1,1,1)=ψ(Ω_ω)：S[3] = (0)(1,1)(2,2)", omegaOmega, 3, "(0)(1,1)(2,2)");

        BmsOrdinal psiOmegaTwo = bms("(0)(1,1)(2,2)");
        expansionEquals("[推导] (0)(1,1)(2,2)=ψ(Ω_2)：S[0] = 1", psiOmegaTwo, 0, "(0)");
        expansionEquals("[推导] (0)(1,1)(2,2)=ψ(Ω_2)：S[1] = ε₀", psiOmegaTwo, 1, "(0)(1,1)");
        expansionEquals("[推导] (0)(1,1)(2,2)=ψ(Ω_2)：S[2] = (0)(1,1)(2,1)", psiOmegaTwo, 2,
                "(0)(1,1)(2,1)");
        expansionEquals("[推导] (0)(1,1)(2,2)=ψ(Ω_2)：S[3] = (0)(1,1)(2,1)(3,1)", psiOmegaTwo, 3,
                "(0)(1,1)(2,1)(3,1)");
    }

    // ------------------------------------------------------------------ expansion consistency

    /**
     * The property that makes the fundamental sequence usable: every tested standard form is strictly above
     * every element of its fundamental sequence, and the sequence itself is strictly increasing. The
     * argument is structural - the first bad-part copy differs from the last column first at the LNZ row,
     * where the bad root is smaller, so {@code S[2]} is below {@code S}; {@code S[1]} and {@code S[0]} are
     * prefixes - but only the finite sample below is checked. The sample is the reference's own standard
     * nodes and worked examples.
     */
    private static void expansionConsistency() {
        List<BmsOrdinal> standard = List.of(
                bms("(0)(1)"),
                bms("(0)(1)(1)"),
                bms("(0)(1)(2)"),
                bms("(0)(1,1)"),
                bms("(0)(1,1)(1,1)"),
                bms("(0)(1,1)(2)"),
                bms("(0)(1,1)(2,1)"),
                bms("(0)(1,1,1)"),
                bms("(0)(1,1,1)(2,1)"),
                bms("(0)(1,1,1)(2,1,1)"),
                bms("(0)(1,1,1)(2,2,1)"),
                bms("(0)(1,1,1)(2,2,2)"),
                bms("(0)(1,1,1)(2,2,2)(3,3,3)"),
                bms("(0)(1,1)(2,2)"),
                bms("(0)(1,1,1)(2,2,2)(3,3,3)(4,2)"),
                bms("(0)(1,1,1,1)(2,2,2,1)(3,3,1,1)(4,2)(5,1,1,1)(6,2,2,1)(7,3,1,1)"));

        boolean allPresent = true;
        boolean allBelow = true;
        boolean allIncreasing = true;
        boolean allZeroBased = true;
        boolean allClosed = true;
        int expanded = 0;
        for (BmsOrdinal base : standard) {
            List<BmsOrdinal> sequence = new ArrayList<>();
            for (int index = 0; index < 5; index++) {
                Optional<BmsOrdinal> next = base.fundamentalSequence(index);
                if (next.isEmpty()) {
                    allPresent = false;
                    break;
                }
                sequence.add(next.get());
                expanded++;
            }
            if (sequence.size() != 5) {
                continue;
            }
            for (BmsOrdinal element : sequence) {
                allBelow &= element.compareTo(base) < 0;
                if (!element.isEmpty()) {
                    for (int value : element.matrix().columns().get(0)) {
                        allZeroBased &= value == 0;
                    }
                }
                if (!element.isEmpty() && element.isLimit()) {
                    allClosed &= element.fundamentalSequence(0).isPresent();
                }
            }
            for (int index = 1; index < sequence.size(); index++) {
                allIncreasing &= sequence.get(index - 1).compareTo(sequence.get(index)) < 0;
            }
        }
        check("一致性：16 个标准式 × 5 个下标全部有基本列元素（共 " + expanded + " 个）",
                allPresent && expanded == 80);
        check("一致性：每个测试的 S 都严格大于 S[0..4]（" + standard.size() + " 个标准式）", allBelow);
        check("一致性：每个测试的 S[0] < S[1] < S[2] < S[3] < S[4] 严格递增", allIncreasing);
        check("一致性：展开结果仍是零基规范形（首列全零）", allZeroBased);
        check("闭包：基本列元素仍是本记号能继续展开的式子", allClosed);
    }

    // ------------------------------------------------------------------ non-recursive BMS

    /**
     * The second article's rules: the first column is a run of ones, trailing zeros are free, a last column
     * {@code (1)} is the successor, the bad-root case uses the BMS expansion, and the no-bad-root case is the
     * article's non-recursive expansion (an infinite column, exposed as a description because it is not a
     * finite matrix). The golden equalities are copied verbatim from the article.
     */
    private static void nonRecursiveBms() {
        NonRecursiveBms padded = nrbms("(1,1,1,1,0)(2,2,1,0,0)(3,3,2,0,0)");
        NonRecursiveBms canonical = nrbms("(1,1,1,1)(2,2,1)(3,3,2)");
        equal("NRBMS 金例：列末的 0 可以任意添加（文章原式）", canonical, padded);
        check("NRBMS 金例：两种写法的哈希一致", canonical.hashCode() == padded.hashCode());
        equal("NRBMS 金例：规范化后的写法", "(1,1,1,1)(2,2,1)(3,3,2)", padded.toString());
        check("NRBMS 首列必须是若干的 1", throwsIllegalArgument(() -> nrbms("(0)(1,1)")));
        check("NRBMS of(int[][]) 同样校验首列（不是 parse 专属）",
                throwsIllegalArgument(() -> NonRecursiveBms.of(new int[][]{{0}, {1, 1}})));
        equal("NRBMS of(int[][]) 接受合法矩阵", "(1,1)(2,2)",
                NonRecursiveBms.of(new int[][]{{1, 1}, {2, 2}}).toString());
        check("NRBMS 末列必须有非零项，单独的 (0) 不是标准式",
                throwsIllegalArgument(() -> nrbms("(1,1,1)(0)")));
        check("NRBMS 空矩阵是 0", nrbms("()").isZero() && nrbms("()").isEmpty());

        NonRecursiveBms successor = nrbms("(1,1,1)(2,2)(1)");
        NonRecursiveBms predecessor = nrbms("(1,1,1)(2,2)");
        check("NRBMS 金例：末列 (1) 是后继", successor.isSuccessor() && !successor.isLimit());
        equal("NRBMS 金例：(1,1,1)(2,2)(1) 的前驱是 (1,1,1)(2,2)", predecessor,
                successor.predecessor().orElseThrow());
        equal("NRBMS 金例：前驱再加一回到原式", successor, predecessor.successor());
        check("NRBMS 后继没有基本列", successor.fundamentalSequence(0).isEmpty());
        check("NRBMS 后继没有非递归展开", successor.nonRecursiveExpansion().isEmpty());

        NonRecursiveBms bmsLimit = nrbms("(1,1,1)(2,2)(3,3,1,1)");
        check("NRBMS 坏根存在时按 BMS 规则展开", bmsLimit.hasBadRoot());
        equal("NRBMS 文章例：(1,1,1)(2,2)(3,3,1,1)[0] = G", nrbms("(1,1,1)"),
                bmsLimit.fundamentalSequence(0).orElseThrow());
        equal("NRBMS 文章例：(1,1,1)(2,2)(3,3,1,1)[1]", nrbms("(1,1,1)(2,2)"),
                bmsLimit.fundamentalSequence(1).orElseThrow());
        equal("NRBMS 文章例：(1,1,1)(2,2)(3,3,1,1)[2]", nrbms("(1,1,1)(2,2)(3,3,1)"),
                bmsLimit.fundamentalSequence(2).orElseThrow());
        equal("NRBMS 文章例：(1,1,1)(2,2)(3,3,1,1)[3]",
                nrbms("(1,1,1)(2,2)(3,3,1)(4,4,2)"), bmsLimit.fundamentalSequence(3).orElseThrow());
        check("NRBMS 坏根情形没有非递归展开", bmsLimit.nonRecursiveExpansion().isEmpty());

        NonRecursiveBms nonRecursive = nrbms("(1,1,1)(2,2,1)");
        check("NRBMS 无坏根：这是非递归情形", !nonRecursive.hasBadRoot() && nonRecursive.isLimit());
        check("NRBMS 无坏根：没有有限基本列元素", nonRecursive.fundamentalSequence(0).isEmpty());
        NonRecursiveExpansion expansion = nonRecursive.nonRecursiveExpansion().orElseThrow();
        equal("NRBMS 非递归展开：(1,1,1)(2,2,1)", "(1,1,1)(2,2)(3,3,1,1,1,1,…)", expansion.toString());
        equal("NRBMS 非递归展开保留截断的末列 (2,2)", "(1,1,1)(2,2)", expansion.fixed().toString());
        equal("NRBMS 非递归展开的新列前缀是 (3,3)", List.of(3, 3), expansion.column());

        NonRecursiveExpansion second = nrbms("(1,1)(2,2,1)(3,1)").nonRecursiveExpansion().orElseThrow();
        equal("NRBMS 非递归展开例二", "(1,1)(2,2,1)(3)(4,1,1,1,1,…)", second.toString());
        equal("NRBMS 非递归展开例二保留截断的末列 (3)", "(1,1)(2,2,1)(3)", second.fixed().toString());
        equal("NRBMS 非递归展开例二的新列前缀是 (4)", List.of(4), second.column());

        check("NRBMS 比较复用字典序：前缀更小", predecessor.compareTo(successor) < 0);
        check("NRBMS compareMagnitude 同样是证明", predecessor.compareMagnitude(successor)
                == ComparisonResult.LESS);
        check("NRBMS 补零写法与规范写法比较为 0", padded.compareTo(canonical) == 0);
        check("NRBMS toString 与矩阵写法一致",
                successor.toString().equals("(1,1,1)(2,2)(1)"));
        check("NRBMS 负下标被拒绝", throwsIllegalArgument(() -> bmsLimit.fundamentalSequence(-1)));
        check("NRBMS 非递归情形调用 expand 会抛 IllegalStateException",
                throwsIllegalState(() -> nonRecursive.expand(0)));
    }

    // ------------------------------------------------------------------ helpers

    private static BmsOrdinal bms(String text) {
        return BmsOrdinal.of(BmsMatrix.parse(text));
    }

    private static NonRecursiveBms nrbms(String text) {
        return NonRecursiveBms.parse(text);
    }

    /** Compares {@code base.fundamentalSequence(index)} with the expected rendering, failing cleanly. */
    private static void expansionEquals(String description, BmsOrdinal base, int index, String expected) {
        Optional<BmsOrdinal> actual = base.fundamentalSequence(index);
        if (actual.isEmpty()) {
            check(description + "（期望 " + expected + "，实际没有基本列元素）", false);
            return;
        }
        equal(description, bms(expected), actual.get());
    }

    private static boolean throwsIllegalState(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalStateException expected) {
            return true;
        }
    }

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
