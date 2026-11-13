package io.github.sob1234509876.oc.googology.ordinal.check;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.bms.BmsOrdinal;
import io.github.sob1234509876.oc.googology.ordinal.OrdinalName;
import io.github.sob1234509876.oc.googology.ordinal.OrdinalRenderRegister;
import io.github.sob1234509876.oc.googology.ordinal.OrdinalRendering;

import java.util.List;

/**
 * Self check of the ordinal rendering register: the mechanism only - registration and lookup by
 * canonical text, the aligned ASCII/LaTeX name chain with its {@code '='} join, the rejections
 * (duplicates, blanks, mismatched chains), and the fallbacks for values that are not registered. The
 * register ships empty, so the worked example of the check ({@code BMS(0)(1,1,1)(2,2)} as
 * {@code ψ(psd. Π_ω)} = {@code SSO}) is test data inside this program, not a table entry. Run with
 * {@code ./gradlew :projects:oc:oc-googology-ordinal:selfcheck}.
 */
public final class OrdinalRenderRegisterCheck {

    private static int passed;
    private static final List<String> FAILURES = new java.util.ArrayList<>();

    private OrdinalRenderRegisterCheck() {
    }

    public static void main(String[] args) {
        section("1) 登记");
        OrdinalRenderRegister register = OrdinalRenderRegister.create();
        OrdinalRendering sso = OrdinalRendering.parse(
                "BMS(0)(1,1,1)(2,2)=p(psd. Pi(w))=SSO",
                "\\mathrm{BMS}(0)(1,1,1)(2,2)=\\psi(\\mathrm{psd.}\\ \\Pi_\\omega)=\\mathrm{SSO}");
        check("空寄存器的 size 为零且什么都不认识", register.size() == 0 && !register.knows("(0)(1,1)"));
        register.register("(0)(1,1,1)(2,2)", sso);
        check("登记后 find 找到同一个 rendering", register.find("(0)(1,1,1)(2,2)").orElseThrow() == sso);
        check("knows 按正则文本判断", register.knows("(0)(1,1,1)(2,2)") && !register.knows("(0)(1,1)"));
        check("size 记录登记数", register.size() == 1);
        check("重复登记被拒绝", throwsIllegalArgument(
                () -> register.register("(0)(1,1,1)(2,2)", sso)));
        check("空白正则文本被拒绝", throwsIllegalArgument(() -> register.register("  ", sso)));
        check("null 正则文本或 rendering 被拒绝",
                throwsIllegalArgument(() -> register.register(null, sso))
                        && throwsIllegalArgument(() -> register.register("(0)", null)));

        section("2) 名字链");
        equal("parse 按 '=' 拆成对齐的链", "A=B=C", OrdinalRendering
                .parse("A=B=C", "\\mA=\\mB=\\mC").ascii());
        equal("LaTeX 链同样按 '=' 连接", "\\mA=\\mB=\\mC", OrdinalRendering
                .parse("A=B=C", "\\mA=\\mB=\\mC").latex());
        check("两条链名字数不一致被拒绝", throwsIllegalArgument(
                () -> OrdinalRendering.parse("A=B", "\\mA=\\mB=\\mC")));
        check("空链被拒绝", throwsIllegalArgument(OrdinalRendering::of));
        check("空白的 ASCII 或 LaTeX 拼写被拒绝",
                throwsIllegalArgument(() -> OrdinalName.of(" ", "\\mA"))
                        && throwsIllegalArgument(() -> OrdinalName.of("A", "")));
        check("of 工厂与 parse 工厂得到等价的 rendering", OrdinalRendering
                .of(OrdinalName.of("A", "\\mA"), OrdinalName.of("B", "\\mB"))
                .equals(OrdinalRendering.parse("A=B", "\\mA=\\mB")));

        section("3) 已登记值的渲染（SSO 示例）");
        BmsOrdinal ssoOrdinal = BmsOrdinal.of(new int[][] {{0}, {1, 1, 1}, {2, 2}});
        equal("BMS 序数的正则文本就是矩阵形式", "(0)(1,1,1)(2,2)", ssoOrdinal.toString());
        equal("ASCII 渲染给完整名字链", "BMS(0)(1,1,1)(2,2)=p(psd. Pi(w))=SSO", register.ascii(ssoOrdinal));
        equal("LaTeX 渲染给 \\mathrm 名字链", "\\mathrm{BMS}(0)(1,1,1)(2,2)"
                        + "=\\psi(\\mathrm{psd.}\\ \\Pi_\\omega)=\\mathrm{SSO}",
                register.latex(ssoOrdinal));
        check("find 返回的 rendering 与渲染一致", register.find(ssoOrdinal.toString()).orElseThrow()
                .ascii().equals(register.ascii(ssoOrdinal)));

        section("4) 未登记值的回退");
        Natural tall = Natural.power(Natural.TEN, Natural.of(1234));
        equal("未登记的 Natural 回退到自己的 ASCII 形式", "10^1234", register.ascii(tall));
        equal("未登记的 Natural 的 LaTeX 回退到自己的 LaTeX 形式", "10^{1234}", register.latex(tall));
        equal("精确值回退到十进制数字", "42", register.ascii(Natural.of(42)));
        equal("BEYOND 的回退", "BEYOND", register.ascii(Natural.BEYOND));
        equal("BEYOND 的 LaTeX 回退", "\\text{BEYOND}", register.latex(Natural.BEYOND));
        Object foreign = new Object() {
            @Override
            public String toString() {
                return "ω^ω (自定义记号)";
            }
        };
        equal("外来对象回退到自己的 toString（ASCII 与 LaTeX 同路）",
                "ω^ω (自定义记号)", register.ascii(foreign));
        equal("外来对象的 LaTeX 回退", "ω^ω (自定义记号)", register.latex(foreign));
        check("null 值被拒绝", throwsIllegalArgument(() -> register.ascii(null))
                && throwsIllegalArgument(() -> register.latex(null)));

        section("5) 共享实例");
        check("standard() 返回同一个实例", OrdinalRenderRegister.standard() == OrdinalRenderRegister.standard());
        check("标准实例保持为空（表的内容由别处填充）", OrdinalRenderRegister.standard().size() == 0);
        check("独立实例互不干扰", OrdinalRenderRegister.create().size() == 0
                && !OrdinalRenderRegister.standard().knows("(0)(1,1,1)(2,2)"));

        summary();
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException | NullPointerException expected) {
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
        boolean same = java.util.Objects.equals(expected, actual);
        check(description + "（期望 " + expected + "，实际 " + actual + "）", same);
    }

    private static void summary() {
        System.out.println();
        if (FAILURES.isEmpty()) {
            System.out.println("googology-ordinal self check: all " + passed + " checks passed.");
            return;
        }
        System.out.println("googology-ordinal self check: " + FAILURES.size() + " of "
                + (passed + FAILURES.size()) + " FAILED:");
        for (String failure : FAILURES) {
            System.out.println("  - " + failure);
        }
        throw new AssertionError(FAILURES.size() + " checks failed");
    }
}
