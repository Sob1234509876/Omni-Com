> English: [Big Cardinals.md](Big%20Cardinals.md)

# 大基数

本文档是不可达基数之上的大基数概念的参考，按**一致性强度**递增排列。它接续
[Inaccessible Cardinal Extension](Inaccessible%20Cardinal%20Extension.md) 的层级，该文档中的每个基数都位于最小马洛基数之下，本文档则使用其中引入的记号 $\mathbb{I}_{\alpha}$。

本文档的组织规则正是本文档所要求的那一条：**若某个基数具有由非平凡初等嵌入给出的刻画，则该刻画就是首要定义**，而集合论刻画（平稳集、不可描述性、测度、划分性质）随后作为*等价地*给出。因此马洛之下的条目没有带框的嵌入定义，因为对它们来说这样的定义并不存在：任何非平凡初等嵌入 $j : V \to M$，只要其临界点为 $\kappa$，就已经使 $\kappa$ 可测，而可测性严格强于直至并包括完全不可描述性在内的每一个概念。嵌入优先的区域恰好从可测基数开始，最初的几个条目对此有明确说明。

## 约定

1. **背景理论。** 除条目另有说明外，一切都在 $\mathrm{ZFC}$ 中陈述。无选择的条目——莱因哈特、伯克利、Ord-伯克利、极限 club 伯克利——在 $\mathrm{ZF}$（或 $\mathrm{ZF} + \mathrm{DC}$）中陈述，并且每一条都在自己小节的开头说明这一点。
2. **嵌入。** *初等嵌入* $j : V \to M$ 指：$M$ 是传递类且 $\mathrm{Ord} \subseteq M$，而 $j$ 是 $\langle V,\in\rangle$ 到 $\langle M,\in\rangle$ 的非平凡初等嵌入。*临界点*是 $\mathrm{crit}(j) = \min\{\alpha : j(\alpha) \neq \alpha\}$；它恰在 $j$ 非平凡时存在，而此时 $j(\alpha) = \alpha$ 对所有 $\alpha < \mathrm{crit}(j)$ 成立。对集合模型，同样的记号把 $V$ 换成 $V_{\lambda}$ 或 $V_{\lambda+1}$。
3. **秩。** $V_{\lambda}$ 是所有秩 $< \lambda$ 的集合构成的集合，而 $V_{\lambda+1} = \mathcal{P}(V_{\lambda})$。$L(V_{\lambda+1})$ 是 $\mathrm{ZF}$ 的模型，且是包含 $V_{\lambda+1}$ 与所有序数的最小者。
4. **枚举的记号。** $\mathbb{I}_{\alpha}$ 是配套文档中的第 $\alpha$ 个不可达基数，而 $\mathbb{M}_{\alpha}$ 是第 $\alpha$ 个马洛基数，按递增顺序从 $0$ 开始枚举。
5. **排序约定。** 各小节按一致性强度排序。当一致性强度无法决定某个比较时，顺序依照*概念大小*——即该公理声称能看见宇宙的多少——而文末的阶梯表把每个比较标记为 `定理`、`约定` 或 `未知`。该顺序一般不是全序，其中有两处衔接是本文档的约定而非定理：伊卡洛斯与其相邻者之间，以及伯克利与莱因哈特之间。这两处在原处以及最后一节都被标出。
6. **有一个比较需要提醒。** 人们有时说强性与武丁性不是线性排序的。正确的说法——也是本文档采用的说法——是：每个强基数都是武丁基数，因此按定理，强性位于武丁性*之上*；而逆命题不成立：武丁基数不必是强基数。本文档中真正依赖约定的比较不是强/武丁，而是伯克利/莱因哈特，在那里一致性强度与概念大小指向相反的方向。

## 1. 马洛基数（Mahlo）

基数 $\kappa$ 是**马洛基数**，当且仅当它不可达，且其下方的正则基数在 $\kappa$ 中平稳。

$$
\boxed{
\kappa \text{ 是马洛基数}
\;\Longleftrightarrow\;
\kappa \text{ 是不可达基数}
\;\wedge\;
\{\, \alpha<\kappa : \alpha \text{ 是正则的} \,\} \text{ 平稳于 } \kappa .
}
$$

等价地：每个 club $C \subseteq \kappa$ 都包含一个正则基数；等价地，每个 club 都包含一个不可达基数（两种表述一致，因为 club 的极限点集仍是 club）；等价地，$\kappa$ 不可达，且在 $\kappa$ 之下有平稳多个不可达基数。

**没有嵌入定义。** 马洛基数没有以定义域为 $V$ 的单个非平凡初等嵌入给出的刻画，因为任何这样的嵌入都有临界点，从而见证可测性，而可测性远高于马洛性。这里的刻画是 $\kappa$ 上 club 滤子的一个*反射*性质，这也正是嵌入优先规则在可测性之前无事可说的原因。

性质。

1. $\kappa$ 是马洛基数蕴含 $\kappa$ 不可达；最小马洛基数 $\mathbb{M}_{0}$ 位于配套文档的每个基数之上，包括其自嵌套层级中的每一项。
2. 每个马洛基数都是不可达基数的极限，因此 $\mathbb{I}_{\alpha} < \mathbb{M}_{0}$ 对每个 $\alpha < \mathbb{M}_{0}$ 成立。
3. 马洛基数**不**构成闭类：若 $\langle \mathbb{M}_{\alpha} : \alpha<\omega\rangle$ 递增，则其上确界的共尾度为 $\omega$，因而是奇异的，从而不是马洛基数。因此枚举 $\mathbb{M}_{\alpha}$ 有真正的空隙。
4. 平稳性不能减弱为无界性。把*平稳*换成*无界*，该概念就坍缩进超不可达层级，并停留在每个马洛基数之下；配套文档利用的正是这一点。

**所处位置。** 在配套文档的一切之上，在 $\alpha$-马洛基数之下，而后者是最弱的真正加强。

## 2. $\alpha$-马洛基数

性质*$\kappa$ 是 $\alpha$-马洛基数* 通过对 $\alpha$ 的超限递归定义，其中 $0$ 处的基础情形是不可达性，因此 $1$-马洛基数恰好就是马洛基数。

$$
\boxed{
\begin{aligned}
&\kappa \text{ 是 } 0\text{-马洛基数} &&\Longleftrightarrow\; \kappa \text{ 是不可达基数},\\[2pt]
&\kappa \text{ 是 } (\alpha+1)\text{-马洛基数} &&\Longleftrightarrow\; \kappa \text{ 是马洛基数，且 } \{\, \beta<\kappa : \beta \text{ 是 } \alpha\text{-马洛基数} \,\} \text{ 平稳于 } \kappa,\\[2pt]
&\kappa \text{ 是 } \lambda\text{-马洛基数} &&\Longleftrightarrow\; \forall \alpha<\lambda \ (\kappa \text{ 是 } \alpha\text{-马洛基数}) \quad (\lambda \text{ 是极限序数}).
\end{aligned}
}
$$

用文字说：在后继步骤，要求前一层级在 $\kappa$ 之下平稳；在极限步骤，要求所有更低层级同时成立。平稳性要求正是它与配套文档中无界层级的分界，也是唯一承载强度的步骤。

性质。

1. $\alpha$-马洛性对 $\alpha$ 单调：若 $\kappa$ 是 $\alpha$-马洛基数且 $\beta<\alpha$，则 $\kappa$ 是 $\beta$-马洛基数。
2. $1$-马洛就是马洛，$2$-马洛是“马洛，且其下有平稳多个马洛基数”，依此类推；约定 $0$-马洛 $=$ 不可达，正是使下标恰好对上的原因。
3. 对每个固定的 $\alpha$，$\alpha$-马洛基数按递增顺序枚举为 $\mathbb{M}(\alpha,\beta)$，风格与配套文档中的 $\mathbb{I}(\alpha,\beta)$ 相同；$\mathbb{M}(1,\beta) = \mathbb{M}_{\beta}$。
4. 每个 $\alpha$-马洛基数都是 $\beta$-马洛基数的极限，对每个 $\beta<\alpha$ 都是如此，而且此类极限的集合本身是平稳的。

**所处位置。** 对每个固定的 $\alpha$，严格位于马洛与超马洛之间。

**注意。** 关于 $0$-马洛是不可达性还是马洛性本身，各家来源说法不一。本文档如框中所述，把 $0$-马洛 $=$ 不可达固定下来，使得约定“$1$-马洛 $=$ 马洛”成立；在另一种约定下，每个下标都向下移一位。

## 3. 超马洛基数（hyper-Mahlo）

基数 $\kappa$ 是**超马洛基数**，当且仅当它是 $\alpha$-马洛基数，对每个 $\alpha<\kappa$ 都如此。

$$
\boxed{
\kappa \text{ 是超马洛基数}
\;\Longleftrightarrow\;
\forall \alpha<\kappa \ (\kappa \text{ 是 } \alpha\text{-马洛基数}).
}
$$

这是配套文档中*超不可达*的精确类比：对角线形式，下标跑遍基数本身之下的所有序数，而不是跑遍某个固定序数。

性质。

1. 超马洛性强于每个固定的 $\alpha$ 的 $\alpha$-马洛性，因为超马洛基数按定义就是 $\alpha$-马洛基数。
2. 超马洛性弱于“$\kappa$ 是 $\kappa$-马洛基数”：后者要求在下标 $\kappa$ 本身处再多一步平稳反射。
3. 超马洛基数在每个弱紧致基数之下平稳，且每个弱紧致基数都是超马洛基数。
4. “超超马洛”以及更高的迭代通过对对角线步骤迭代来定义，完全如同不可达情形；它们都仍然位于弱紧致之下。

**所处位置。** 在每个固定的 $\alpha$-马洛层级之上，在弱紧致之下。

## 4. 弱紧致基数（weakly compact）

基数 $\kappa$ 是**弱紧致基数**，当且仅当它不可达且是 $\Pi^{1}_{1}$-不可描述的，即：对每个 $A \subseteq V_{\kappa}$ 以及第二阶语言的每个 $\Pi^{1}_{1}$ 语句 $\varphi$（该语言带有 $A$ 的谓词），$\langle V_{\kappa},\in,A\rangle$ 中的真都被反射到某个 $\langle V_{\alpha},\in,A\cap V_{\alpha}\rangle$，其中 $\alpha<\kappa$。

$$
\boxed{
\kappa \text{ 是弱紧致基数}
\;\Longleftrightarrow\;
\kappa \text{ 是不可达基数} \;\wedge\; \kappa \text{ 是 } \Pi^{1}_{1}\text{-不可描述的}.
}
$$

等价地：每个高度为 $\kappa$、各层大小 $<\kappa$ 的树都有共尾分支（树性质）；等价地 $\kappa \to (\kappa)^{2}_{2}$；等价地，每个 $A \subseteq V_{\kappa}$ 都被某个 $\alpha<\kappa$ 反射，这正是把框中的不可描述性条件写开。

**没有嵌入定义。** 弱紧致性没有由非平凡初等嵌入 $j : V \to M$（临界点为 $\kappa$）给出的刻画：任何这样的嵌入直接给出可测性。存在的那些带嵌入色彩的重述针对的是*集合大小*模型的嵌入，并非本文档所用的形式，因此上面的框是首要定义，嵌入优先规则此时尚不适用。

性质。

1. 弱紧致基数是马洛基数、超马洛基数，并且是超马洛基数的极限。
2. 弱紧致性恰好就是 $\Pi^{1}_{1}$-不可描述性，因此它是下一节不可描述性阶梯的第一级。
3. 最小弱紧致基数是马洛基数的极限，且 $\mathbb{M}_{\alpha}$ 位于其下，对每个 $\alpha < $ 该基数都是如此。
4. 树性质与划分关系真正等价于框中的陈述，而不只是它的推论。

**所处位置。** 在超马洛之上，在 $\Pi^{1}_{2}$-不可描述性之下。

## 5. 不可描述基数（indescribable）

对 $m \ge 1$，基数 $\kappa$ 是**$\Pi^{1}_{m}$-不可描述的**，当且仅当对每个 $A \subseteq V_{\kappa}$ 与每个 $\Pi^{1}_{m}$ 语句 $\varphi$，

$$
\boxed{
\langle V_{\kappa},\in,A\rangle \models \varphi
\;\Longrightarrow\;
\exists \alpha<\kappa \ \bigl( \langle V_{\alpha},\in,A\cap V_{\alpha}\rangle \models \varphi \bigr).
}
$$

基数**完全不可描述**，当且仅当它是 $\Pi^{1}_{m}$-不可描述的，对每个 $m<\omega$ 都如此。

该层级对 $m$ 累积：$\Pi^{1}_{m}$-不可描述性蕴含 $\Pi^{1}_{n}$-不可描述性（$n<m$），因为 $\Pi^{1}_{n}$ 语句一致地也是 $\Pi^{1}_{m}$ 语句。$\Pi^{1}_{1}$ 按定义就是弱紧致，因此阶梯从上一节开始。

性质。

1. $\Pi^{1}_{1}$-不可描述 $=$ 弱紧致；$\Pi^{1}_{2}$-不可描述严格更强，且已经蕴含 $\kappa$ 是弱紧致基数的极限。
2. 完全不可描述性严格强于 $\Pi^{1}_{m}$-不可描述性（对每个固定的 $m$）；它是整个阶梯的并。
3. 完全不可描述性严格弱于可测性：每个可测基数都是完全不可描述的，且每个可测基数都是完全不可描述基数的极限。
4. 不可描述性是结构 $\langle V_{\kappa},\in,A\rangle$ 的反射性质，其中 $A$ 跑遍 $V_{\kappa}$ 的子集；它不是嵌入的性质。

**所处位置。** 在弱紧致与可测之间，并且它是无嵌入区域的最后一个条目。

**注意。** 定义对反射的 $\alpha$ 作存在量化。有些来源给出平稳版本（“平稳多个 $\alpha$ 反射 $A$”）；对 $\Pi^{1}_{1}$ 两者一致，而完全不可描述性用任一种方式都能陈述，但中间 $m$ 的等价性并非本文档所依赖的内容，因此通篇采用存在形式。

## 6. 可测基数（measurable）

基数 $\kappa$ 是**可测基数**，当且仅当它是整个宇宙到传递类的非平凡初等嵌入的临界点。

$$
\boxed{
\kappa \text{ 是可测基数}
\;\Longleftrightarrow\;
\exists\, j : V \to M \ \text{初等},\ M \text{ 传递且 } \mathrm{Ord}\subseteq M,\
\mathrm{crit}(j) = \kappa,\ j \text{ 非平凡}.
}
$$

等价地：$\kappa$ 上存在 $\kappa$-完备非主超滤。由这样的超滤 $U$，超幂 $j_{U} : V \to \mathrm{Ult}$ 因 $\kappa$-完备性而是良基的；它的传递坍缩就是框中的 $M$，而 $\kappa$ 作为 $\mathrm{crit}(j_{U})$ 被恢复。取 $U$ 正规，还可额外安排 $M^{\kappa} \subseteq M$，从而 $V_{\kappa+1} \subseteq M$ 且 $j(\kappa) > \kappa$。

这是第一个具有真正嵌入定义的条目，它为上面的一切定下模式：嵌入就是定义，而测度是它的翻译。

性质。

1. 可测基数都是完全不可描述的、马洛的，并且是前面各节所有基数的极限。
2. $V_{\kappa} \models \mathrm{ZFC}$ 对可测的 $\kappa$ 成立；该嵌入固定 $\kappa$ 之下的每个序数，并移动 $\kappa$ 本身。
3. $\kappa$-完备超滤在 $\kappa$ 上由 Mitchell 序组织；此处不需要这一细化，只需注意可测性是一个长得多的、基于测度的层级的第一个层级。
4. 可测性恰好就是下一节意义上的 $1$-强性。

**所处位置。** 具有 $j : V \to M$ 的第一个条目，在完全不可描述性之上，在每个强性层级之下。

## 7. $\alpha$-强基数与强基数

对序数 $\alpha$，基数 $\kappa$ 是**$\alpha$-强基数**，当且仅当它是某个捕获最初 $\alpha$ 多个位于 $\kappa$ 之上的秩的嵌入的临界点。

$$
\boxed{
\kappa \text{ 是 } \alpha\text{-强基数}
\;\Longleftrightarrow\;
\exists\, j : V \to M \ \text{初等},\ \mathrm{crit}(j)=\kappa,\ V_{\kappa+\alpha} \subseteq M .
}
$$

基数**强**，当且仅当它对每个 $\alpha$ 都是 $\alpha$-强的。

$$
\boxed{
\kappa \text{ 是强基数}
\;\Longleftrightarrow\;
\forall \alpha \ (\kappa \text{ 是 } \alpha\text{-强基数})
\;\Longleftrightarrow\;
\forall \lambda \ \exists j : V \to M \ \bigl( \mathrm{crit}(j)=\kappa \wedge V_{\lambda} \subseteq M \bigr).
}
$$

等价地，用文字说：对每个 $\lambda$，都存在 $V$ 到某个传递类的嵌入，它与 $V$ 至少在秩 $\lambda$ 之前一致，且临界点为 $\kappa$。两个展示出的形式一致，因为 $V_{\lambda} \subseteq V_{\kappa+\alpha}$ 只要 $\lambda \le \kappa+\alpha$ 就成立。

性质。

1. $0$-强性是自动的：$V_{\kappa} \subseteq M$ 对任何非平凡 $j$ 都成立，其中 $\mathrm{crit}(j)=\kappa$；$1$-强性恰好就是可测性；而 $\alpha$-强性对 $\alpha$ 单调。
2. 强基数是可测的，且强基数是其下方强基数的极限。
3. 有些作者加上条款 $j(\kappa) > \kappa+\alpha$。它是多余的，但冗余性的证明用到 Kunen 不一致性，因此这不是一个平凡的省略。
4. 对固定 $\alpha$，该概念弱于武丁性；“对每个 $\alpha$”的情形强于武丁性。因此本节横跨下一个条目，而阶梯表把 $\alpha$-强排在武丁之前、把强排在武丁之后。

**所处位置。** 对固定的 $\alpha \ge 1$，在可测与武丁之间；所有 $\alpha$ 的情形位于武丁与超紧致之间。

**注意。** 本节按请求故意在一处涵盖整个 $\alpha$-层级，尽管该层级在跨过武丁时位置并不单调。关于每一层级所处位置的权威是表格，而不是节序。

## 8. 武丁基数（Woodin）

基数 $\kappa$ 是**武丁基数**，当且仅当对每个函数 $f : \kappa \to \kappa$，都有 $f$ 的一个闭包点在 $\kappa$ 之下，它是某个强到能在该点看见 $j(f)$ 的嵌入的临界点。

$$
\boxed{
\kappa \text{ 是武丁基数}
\;\Longleftrightarrow\;
\forall f : \kappa\to\kappa\ \exists \alpha<\kappa
\Bigl( f''\alpha \subseteq \alpha \ \wedge\ \exists j : V\to M \
\bigl( \mathrm{crit}(j)=\alpha \wedge V_{j(f)(\alpha)} \subseteq M \bigr) \Bigr).
}
$$

等价地：对每个 $A \subseteq V_{\kappa}$，存在 $\alpha<\kappa$ 使得 $\alpha$ 是 $A$-强的，意思是：对每个 $\lambda$ 都存在 $j : V \to M$，满足 $\mathrm{crit}(j)=\alpha$、$V_{\lambda}\subseteq M$ 且 $j(A)\cap V_{\lambda} = A\cap V_{\lambda}$；等价地，$\kappa$ 不可达且此类 $\alpha$ 的集合在 $\kappa$ 中共尾。

该定义是*局部化*的强性：它不要求某一个基数对所有 $\lambda$ 都强，而是要求对每个目标函数，在 $\kappa$ 之下某个 $\alpha$ 强到嵌入自身产生的层级 $j(f)(\alpha)$。

性质。

1. 武丁基数不可达且是马洛基数，并且 $\kappa$ 是可测基数的极限。
2. 每个强基数都是武丁基数；武丁性不蕴含强性。
3. 武丁性是发展 $\mathrm{HOD}$ 理论与 $\Omega$-猜想时所依据的标准假设，并且它是第一个定义对 $\kappa$ 上所有函数量化、而不是对某个固定测度或滤子量化的条目。
4. 该定义等价于形式“对每个 $A \subseteq V_{\kappa}$，$A$-强的 $\alpha$ 在 $\kappa$ 中共尾”，这是实践中用得最多的形式。

**所处位置。** 在每个固定的 $\alpha$-强层级之上，在强基数之下。

**注意（排序）。** 强性蕴含武丁性，因此这两者由定理而非约定排序；不成立的是逆命题。见约定第 6 条。

## 9. 超紧致基数（supercompact）

对 $\lambda \ge \kappa$，基数 $\kappa$ 是**$\lambda$-超紧致的**，当且仅当它是某个嵌入的临界点，该嵌入的目标对 $\lambda$-序列封闭，并且把 $\kappa$ 移过 $\lambda$。

$$
\boxed{
\kappa \text{ 是 } \lambda\text{-超紧致的}
\;\Longleftrightarrow\;
\exists j : V\to M \
\bigl( \mathrm{crit}(j)=\kappa \ \wedge\ j(\kappa) > \lambda \ \wedge\ M^{\lambda}\subseteq M \bigr).
}
$$

基数**超紧致**，当且仅当它是 $\lambda$-超紧致的，其中 $\lambda \ge \kappa$ 可以任意大。

$$
\boxed{
\kappa \text{ 是超紧致的}
\;\Longleftrightarrow\;
\forall \lambda\ge\kappa \ (\kappa \text{ 是 } \lambda\text{-超紧致的}).
}
$$

等价地：对每个 $\lambda \ge \kappa$，存在 $\kappa$-完备、精细、正规的超滤，定义在 $\mathcal{P}_{\kappa}\lambda$ 上，即正规精细测度；等价地，对每个 $\lambda$ 都存在 $j$，满足 $\mathrm{crit}(j)=\kappa$、$j(\kappa)>\lambda$，且 $M$ 包含其自身元素的所有 $\lambda$-序列。

性质。

1. $\kappa$ 是 $\kappa$-超紧致的，恰在 $\kappa$ 可测之时；承载强度的是 $\kappa$ 之上的层级。
2. 超紧致基数是强基数，从而是武丁基数，从而是可测基数；$\kappa$ 超紧致蕴含 $\kappa$ 是其下方强基数的极限。
3. 测度表述是真正的等价，而非推论：$\mathcal{P}_{\kappa}\lambda$ 上的精细正规测度与 $\lambda$-超紧致嵌入可以互相翻译。
4. 超紧致性是第一个其嵌入要求目标对任意大的 $\lambda$ 的 $\lambda$-长序列封闭的条目，这严格多于强性的要求。

**所处位置。** 在强基数之上，在每个秩入秩公理之下。

## 10. I3

**I3** 是最弱的秩入秩公理：一个秩初始段到自身的非平凡初等嵌入。

$$
\boxed{
\mathrm{I3}
\;\Longleftrightarrow\;
\exists \lambda \ \exists j : V_{\lambda} \prec V_{\lambda} \
\bigl( \lambda \text{ 是极限序数},\ j \text{ 非平凡},\ \mathrm{crit}(j)<\lambda \bigr).
}
$$

这里 $j$ 是集合大小的嵌入，$V_{\lambda}$ 满足去掉幂集公理的 $\mathrm{ZFC}$，且 $\mathrm{crit}(j)$ 因 $j$ 非平凡而存在。总可以取 $\lambda$ 为临界序列的极限：若 $\kappa_{0} = \mathrm{crit}(j)$ 且 $\kappa_{n+1} = j(\kappa_{n})$，则 $\lambda' = \sup_{n}\kappa_{n}$ 满足 $j(\lambda')=\lambda'$，因此 $j$ 限制为嵌入 $V_{\lambda'}\to V_{\lambda'}$。在该正规化之后，$\mathrm{cf}(\lambda)=\omega$，且 $\lambda$ 是强极限基数。

性质。

1. 临界序列的每个 $\kappa_{n}$ 都是可测的，而且远不止于此；$\lambda$ 是可测基数的极限。
2. I3 与选择公理兼容：它是关于集合大小结构的陈述，因此 Kunen 不一致性不适用于它。
3. I3 不蕴含 I2；它是秩入秩阶梯的底层。
4. I3 蕴含在 $\lambda$ 之下有无界多个可测基数，但对 $V$ 在 $\lambda$ 之外的情况什么也没说。

**所处位置。** 在超紧致基数之上，在 I2 之下。

## 11. I2

**I2** 是整个宇宙的一个非平凡初等嵌入，其目标捕获秩 $V_{\lambda}$，其中 $\lambda$ 是临界序列的极限。

$$
\boxed{
\mathrm{I2}
\;\Longleftrightarrow\;
\exists j : V \to M \ \text{初等},\ M \text{ 传递},\ \mathrm{crit}(j)=\kappa,\
V_{\lambda}\subseteq M,\ \lambda = \sup_{n<\omega} j^{n}(\kappa).
}
$$

闭包条件加在临界序列的极限处，而不是 $\kappa$ 本身。特别地，$j(\lambda)=\lambda$ 成立，并由此得到 $V_{j(\kappa)} \subseteq M$，所以 I2 嵌入特别是**超强**嵌入；超强性是强性与 I2 之间的自然概念。

性质。

1. I2 蕴含 I3：把 $j$ 限制到 $V_{\lambda}$，得到初等 $V_{\lambda}\to V_{\lambda}$，其临界点为 $\kappa$，因为 $j(\lambda)=\lambda$ 且 $V_{\lambda}\subseteq M$。
2. I2 嵌入是超强的，从而是强的，从而是武丁的，从而是可测的。
3. I2 在一致性强度上严格强于 I3。
4. $M$ 在 $V_{\lambda}$ 处的闭包，正是使 $V_{\lambda}$ 上的限制嵌入成为可能的原因；对仅仅超强的嵌入，没有这样的限制可用。

**所处位置。** 在 I3 之上，在 I1 之下。

**注意。** 关于 I2 的陈述中要求 $M$ 有多大闭包，各家来源不一。这里的表述——在 $\sup_{n} j^{n}(\kappa)$ 处闭包——是标准表述；把 $M$ 只在 $V_{j(\kappa)}$ 处闭包的陈述描述的是超强性，严格更弱。

## 12. I1

**I1** 是 $V_{\lambda+1}$ 到自身的非平凡初等嵌入。

$$
\boxed{
\mathrm{I1}
\;\Longleftrightarrow\;
\exists \lambda \ \exists j : V_{\lambda+1} \prec V_{\lambda+1} \
\bigl( j \text{ 非平凡},\ \mathrm{crit}(j)<\lambda \bigr).
}
$$

此时嵌入作用在 $V_{\lambda}$ 的完整幂集上，而不只是作用在 $V_{\lambda}$ 上，这就是 I1 与 I3 的分界。

性质。

1. I1 蕴含 I3，只需把 $j$ 限制到 $V_{\lambda}$。
2. I1 与选择公理兼容；它是集合大小的陈述。
3. I1 蕴含 $\lambda$ 是可测基数的极限，并且在 $\lambda$ 之下存在许多 I3 嵌入。
4. I1 不蕴含 I0，两者是阶梯上不同的两级。

**所处位置。** 在 I2 之上，在 I0 之下。

## 13. I0

**I0** 是 $L(V_{\lambda+1})$ 到自身的非平凡初等嵌入。

$$
\boxed{
\mathrm{I0}
\;\Longleftrightarrow\;
\exists \lambda \ \exists j : L(V_{\lambda+1}) \prec L(V_{\lambda+1}) \
\bigl( j \text{ 非平凡},\ \mathrm{crit}(j)<\lambda \bigr).
}
$$

这是四条秩入秩公理中最强的一条，也是秩入秩家族中已知与选择公理兼容的最强大基数概念。

性质。

1. I0 蕴含 I1：$V_{\lambda+1}$ 是 $L(V_{\lambda+1})$ 的元素，因此 $j$ 固定它，并限制为具有相同临界点的初等嵌入 $V_{\lambda+1}\to V_{\lambda+1}$。
2. I0 尚未已知与 $\mathrm{ZFC}$ 不一致，且没有从下方给出的一致性证明；它本身就是一条无穷公理。
3. 模型 $L(V_{\lambda+1})$ 是 $\mathrm{ZF}$ 的模型，而该嵌入是这个模型的类嵌入。这就是该公理能在 Kunen 定理下存活的技术原因：该嵌入不是 $V$ 的嵌入。
4. Kunen 不一致性从上方界定该家族：不存在非平凡的初等 $j : V_{\lambda+2} \to V_{\lambda+2}$，因此 I0 是秩入秩阶梯的最后一级。

**所处位置。** 秩入秩阶梯的顶端，在 I1 之上，在莱因哈特基数之下。

## 14. 伊卡洛斯基数（Icarus）

**状态：下面的定义是本文档可用的最佳表述，已被标记为待核实。** *伊卡洛斯集*是子集 $X \subseteq V_{\lambda+1}$，使得由 $V_{\lambda+1}$ 与参数 $X$ 构建的模型具有临界点低于 $\lambda$ 的非平凡自嵌入。

$$
\boxed{
X \text{ 是伊卡洛斯集}
\;\Longleftrightarrow\;
X \subseteq V_{\lambda+1}
\ \wedge\
\exists j : L(X, V_{\lambda+1}) \prec L(X, V_{\lambda+1}) \
\bigl( j \text{ 非平凡},\ \mathrm{crit}(j)<\lambda \bigr),
}
$$

其中 $L(X,V_{\lambda+1})$ 是 $\mathrm{ZF}$ 的、包含 $V_{\lambda+1}$、集合 $X$ 与所有序数的最小模型。基数 $\lambda$ 称为**伊卡洛斯基数**，若存在这样的 $X$。

无参数情形恰好就是 I0：$L(\varnothing, V_{\lambda+1}) = L(V_{\lambda+1})$，因此每个 I0 嵌入都是带平凡参数的伊卡洛斯嵌入。反过来，较大模型 $L(X,V_{\lambda+1})$ 的嵌入限制为 $L(V_{\lambda+1})$ 的嵌入，因为 $V_{\lambda+1}$ 是该模型的元素，从而被 $j$ 固定。所以伊卡洛斯位于 I0 处或之上，而参数 $X$ 正是嵌入必须额外尊重的对象。

性质。

1. 带参数 $X$ 的伊卡洛斯蕴含 I0，由刚才描述的限制得到。
2. 伊卡洛斯是秩入秩风格的概念：嵌入是由集合构建的类模型的嵌入，而不是 $V$ 的嵌入，因此 Kunen 不一致性不适用，该公理也尚未已知与选择公理冲突。
3. 在文献中，伊卡洛斯集的强度对照 sharp 来校准：$(V_{\lambda+1})^{\sharp}$ 被引述为蕴含 $V_{\lambda+1}$ 是伊卡洛斯的，而 $(V_{\lambda+1})^{\sharp\sharp}$ 被引述为蕴含这一点，因此伊卡洛斯假设在 I0 之上构成它们自己的阶梯。
4. 该概念是对*集合*参数 $X \subseteq V_{\lambda+1}$ 陈述的；对所有这样的 $X$ 量化，才把它变成 $\lambda$ 的基数性质。

**所处位置。** 在 I0 之上，在莱因哈特基数之下。这一位置是依约定给出的：本文档没有定理把伊卡洛斯与 I0 分开，而相对于 I1 的位置继承自 I0。

**注意。** 该表述由二手来源拼合而成，*未*对照原始定义核实；见最后一节。特别地，$L(X,V_{\lambda+1})$ 究竟需要何种闭包，以及该基数性质是“存在某个 $X$”还是“对每个 $X$”，是两处需要核查的地方。

## 15. 莱因哈特基数（Reinhardt）

*本条在 $\mathrm{ZF}$ 中陈述，或在 $\mathrm{ZF}+\mathrm{DC}$ 中陈述；它被选择公理否证。*

基数 $\kappa$ 是**莱因哈特基数**，当且仅当它是宇宙到自身的非平凡初等嵌入的临界点。

$$
\boxed{
\kappa \text{ 是莱因哈特基数}
\;\Longleftrightarrow\;
\exists j : V \to V \ \text{非平凡初等嵌入，且 } \mathrm{crit}(j)=\kappa .
}
$$

等价地：存在非平凡的初等 $j : V \to V$，而 $\kappa$ 是它的临界点；定义域与陪域是同一个类，这正是该概念与上面每个条目的区别。

**与选择公理不一致（Kunen）。** 不存在非平凡初等嵌入 $j : V \to V$（在 $\mathrm{ZFC}$ 中）。该证明本质上用到选择公理，通过临界序列上的 $\omega$-Jónsson 函数，而同样的论证排除了 $j : V_{\lambda+2}\to V_{\lambda+2}$。因此莱因哈特公理只在 $\mathrm{ZF}$ 或 $\mathrm{ZF}+\mathrm{DC}$ 中陈述，在那里其一致性是未决的。

性质。

1. 莱因哈特蕴含 I0，从而蕴含所有秩入秩公理：把 $j$ 限制到 $L(V_{\lambda+1})$（其中 $\lambda$ 是临界序列的极限）得到 I0 嵌入，限制到 $V_{\lambda+1}$ 得到 I1。
2. 莱因哈特在*概念上*是本文档中除伯克利之外最大的概念：该嵌入是整个宇宙到自身的嵌入，因此没有集合论结构在它的触及之外。
3. 然而它的一致性强度低于伯克利基数：后者要求对每个包含该基数的传递集都有这样的嵌入，而不仅是对 $V$ 本身，并且要求它们关于临界点一致地存在。这是本文档中唯一一处概念大小与一致性强度明显不一致的地方；所采用的顺序是一致性强度，正如所要求的那样。
4. 每个莱因哈特基数都是其下方可测基数与 I3 基数的极限。

**所处位置。** 在 I0 之上，在伯克利之下。与伊卡洛斯的比较是依约定给出的。

**注意。** 莱因哈特基数与 $\mathrm{ZF}$ 的一致性在标准文献中是未决问题。一篇声称证明了不一致性的 2026 年预印本在错误被指出后被撤回，因此未决状态仍然成立。

## 16. 伯克利基数（Berkeley）

*本条在 $\mathrm{ZF}$ 中陈述；它被选择公理否证。*

基数 $\delta$ 是**伯克利基数**，当且仅当每个包含 $\delta$ 的传递集都具有临界点任意接近 $\delta$ 的非平凡自嵌入。

$$
\boxed{
\delta \text{ 是伯克利基数}
\;\Longleftrightarrow\;
\forall M\ \forall \eta<\delta \
\Bigl( M \text{ 传递} \wedge \delta\in M
\;\Rightarrow\;
\exists j : M\to M \ \text{非平凡初等嵌入，且 } \eta<\mathrm{crit}(j)<\delta \Bigr).
}
$$

用文字说：对每个传递 $M$（满足 $\delta \in M$），以及对每个 $\eta<\delta$，都存在非平凡初等 $j : M \to M$，其临界点严格位于 $\eta$ 与 $\delta$ 之间。因此临界点在 $\delta$ 中共尾，而 $\delta$ 本身不必是临界点。

**与选择公理不一致。** 把定义用于 $M = V_{\delta+2}$：它给出非平凡初等 $j : V_{\delta+2}\to V_{\delta+2}$，而 Kunen 定理在 $\mathrm{ZFC}$ 中禁止这样的嵌入。因此伯克利基数与莱因哈特基数一样，存在于 $\mathrm{ZF}$ 或 $\mathrm{ZF}+\mathrm{DC}$ 中。

性质。

1. 伯克利蕴含莱因哈特强度的概念：取 $M = V_{\delta+1}$ 得到 $\delta$ 处的 I1 嵌入，而取 $M$ 为 $V_{\lambda+1}$ 的形式（对适当的 $\lambda$）则得到 I0 嵌入。
2. 伯克利在一致性强度上严格高于莱因哈特，因为它要求对*每个*包含 $\delta$ 的传递集都具有该嵌入性质，而不只是对 $V$。
3. 该定义是关于传递集的概括，因此它不是一个单一的嵌入，而是一个一致的嵌入供应；这就是它在无选择环境下才能在 Kunen 定理下存活的原因。
4. 在 $\mathrm{ZF}$ 中，伯克利基数的存在性尚未已知不一致，而这是无选择层级的核心未决问题。

**所处位置。** 在莱因哈特之上，在 Ord-伯克利与 club 伯克利之下。

## 17. Ord-伯克利基数（Ord-Berkeley）

*本条在 $\mathrm{ZF}$ 中陈述；它被选择公理否证。*

这里采用的定义是无选择大基数文献中给出的定义：$\kappa$ 是**Ord-伯克利基数**，当且仅当它对每个 $\lambda$ 都是 $\lambda$-伯克利的。

$$
\boxed{
\kappa \text{ 是序数伯克利基数}
\;\Longleftrightarrow\;
\forall \lambda>\kappa \ \bigl( \kappa \text{ 是 } \lambda\text{-伯克利的} \bigr),
}
$$

其中对 $\lambda>\kappa$，$\kappa$ 是**$\lambda$-伯克利的**，当且仅当对每个传递 $N$（满足 $\kappa \subseteq N$ 且 $|N|<\lambda$），非平凡初等 $j : N \to N$ 的临界点集合在 $\kappa$ 中无界。展开大小限制即得等价形式

$$
\boxed{
\kappa \text{ 是序数伯克利基数}
\;\Longleftrightarrow\;
\forall N\ \Bigl( N \text{ 传递} \wedge \kappa\subseteq N
\;\Rightarrow\;
\{\, \mathrm{crit}(j) : j : N\to N \ \text{非平凡初等} \,\} \text{ 无界于 } \kappa \Bigr).
}
$$

因此 Ord-伯克利是做了两处改动的伯克利：假设是 $\kappa \subseteq N$ 而非 $\kappa \in N$，而对临界点的要求是*无界性*，而非“任意接近 $\kappa$”的表述。$\lambda$-伯克利中的参数 $\lambda$ 是大小界限，而“Ord-伯克利”通过对所有 $\lambda$ 量化来去掉该界限。

性质。

1. 该定义是一般伯克利概括的直接特化：伯克利把传递集限制为那些以 $\delta$ 为元素的集合，并要求临界点高于任意给定的 $\eta<\delta$；$\lambda$-伯克利限制它们的大小，并要求无界多个临界点；Ord-伯克利去掉大小界限。
2. 按这种读法，Ord-伯克利至少与伯克利一样强，因为传递 $N$（满足 $\kappa\subseteq N$）之类包含满足 $\kappa\in N$ 的类。
3. 该性质的显然模型是秩初始段：取 $N = V_{\kappa}$ 就给出 $\kappa$ 处的 I3 嵌入，因此 Ord-伯克利蕴含 $\kappa$ 处的 I3（其实远不止于此）。
4. 与伯克利一样，Ord-伯克利因同样的理由与 $\mathrm{ZFC}$ 不一致：把定义用于 $N = V_{\delta+2}$ 会给出 Kunen 定理所禁止的嵌入。

**所处位置。** 依约定，位于伯克利与 club 伯克利之间。本文档没有定理把 Ord-伯克利与伯克利分开。

**注意（名称的两种读法）。** 该名称在文献中有两种不同用法，本条不应被读作已经确定了其中哪一种。

1. *无界读法*，即上面采用的读法：Ord-伯克利 $=$ 对每个 $\lambda$ 都是 $\lambda$-伯克利的。这是无选择大基数文献中引用的定义的读法，也是本文档采用的读法。
2. *累积层级读法*，为本条目提出的请求将其描述为“对每个 $\alpha$，存在非平凡 $j : V_{\alpha}\to V_{\alpha}$，其临界点为 $\kappa$”。在文献中，该概念称为**秩伯克利**（rank-Berkeley，Goldberg–Schlutzenberg），其已核实的形式是

$$
\boxed{
\lambda \text{ 是秩伯克利的}
\;\Longleftrightarrow\;
\forall \alpha<\lambda\ \forall \beta\ge\lambda\ \exists j : V_{\beta}\to V_{\beta} \
\bigl( j \text{ 非平凡初等},\ \alpha<\mathrm{crit}(j)<\lambda \bigr).
}
$$

   秩伯克利被明确引入为伯克利的一种*减弱*，因为它只检验秩初始段；它正是用来定义精确基数的概念。如果本项目中的“Ord-伯克利”意指那个意义，那么本条应重新指向上面的框，而阶梯中的位置就移到伯克利之下而非之上。

由于两种读法给出不同的强度与不同的位置，本条在最后一节被标出。

## 18. 极限 club 伯克利基数（limit club Berkeley）

*本条在 $\mathrm{ZF}$ 中陈述；它被选择公理否证。*

**状态：下面的定义是重建的，已被标记为待核实。** 已核实的成分是参数化的 club $\lambda$-伯克利基数概念：对基数 $\kappa<\lambda$，$\kappa$ 是**club $\lambda$-伯克利的**，当且仅当对每个传递集 $N$（其大小 $<\lambda$ 且 $\kappa \subseteq N$），都存在 club $C \subseteq \kappa$，使得每个 $\eta \in C$ 都是某个初等嵌入 $N \to N$ 的临界点；而 $\kappa$ 是**$\nu$-club $\lambda$-伯克利的**，只要该 club 可以取为 $\nu$-club；而 $\kappa$ 是 $\lambda$-伯克利的，若临界点集合只是在 $\kappa$ 中无界。

$$
\boxed{
\kappa \text{ 是闭无界伯克利基数}
\;\Longleftrightarrow\;
\forall M\ \Bigl( M \text{ 传递} \wedge \kappa\subseteq M
\;\Rightarrow\;
\exists C\subseteq\kappa \ \text{是闭无界的}\ \forall \eta\in C\ \exists j : M\to M \
\bigl( j \text{ 非平凡初等},\ \mathrm{crit}(j)=\eta \bigr) \Bigr).
}
$$

基数 $\delta$ 是**极限 club 伯克利基数**，当且仅当它是 club 伯克利基数，并且是其下方 club 伯克利基数的极限。

$$
\boxed{
\delta \text{ 是极限闭无界伯克利基数}
\;\Longleftrightarrow\;
\delta \text{ 是闭无界伯克利基数}
\;\wedge\;
\{\, \gamma<\delta : \gamma \text{ 是闭无界伯克利基数} \,\} \text{ 无界于 } \delta .
}
$$

其预期内容是伯克利的两步加强：先把无界多个临界点换成一个*club*（club 伯克利），再要求该基数是具有该性质的基数的极限（极限 club 伯克利）。“club”这一步正是无选择层级的紧致性刻画所附着的地方，因此它是一级自然的阶梯。

性质。

1. club 伯克利蕴含伯克利，因为临界点的 club 特别地无界；而 Ord-伯克利是每个大小上的无界版本，所以 club 伯克利蕴含 Ord-伯克利。
2. 极限 club 伯克利蕴含 $\delta$ 处的 club 伯克利，并在 $\delta$ 之下给出充足的 club 伯克利基数；它是本文档中最强的条目。
3. 再次取 $M = V_{\delta+2}$ 会给出 Kunen 所禁止的嵌入，因此该概念与伯克利一样被 $\mathrm{ZFC}$ 否证。
4. 在 $\mathrm{ZF}$ 中，club 伯克利与极限 club 伯克利都尚未已知不一致。

**所处位置。** 阶梯的顶端，在 club 伯克利之上，从而在 Ord-伯克利与伯克利之上。

**注意。** 两个展示出的框都是重建：参数化的 club $\lambda$-伯克利定义已核实，而无参数的“club 伯克利”形式与“club 伯克利基数的极限”条款则未核实。见最后一节。

## 一致性强度阶梯表

表格是顺序的权威；节号恰在一处偏离它，因为第 7 节同时涵盖固定 $\alpha$ 的层级（在武丁之下）与所有 $\alpha$ 的情形（在武丁之上）。`依据` 列记录与上一行的比较是定理、本文档的约定，还是未知。

| # | 基数 | 小节 | 依据 |
|---|------|------|------|
| 1 | 马洛基数 | 1 | 阶梯的基底 |
| 2 | $\alpha$-马洛基数，固定 $\alpha\ge 1$ | 2 | 定理 |
| 3 | 超马洛基数 | 3 | 定理 |
| 4 | 弱紧致基数（$\Pi^{1}_{1}$-不可描述） | 4 | 定理 |
| 5 | $\Pi^{1}_{n}$-不可描述基数，$n\ge 2$，以及完全不可描述基数 | 5 | 定理 |
| 6 | 可测基数 | 6 | 定理 |
| 7 | $\alpha$-强基数，固定 $\alpha\ge 1$ | 7 | 定理 |
| 8 | 武丁基数 | 8 | 定理 |
| 9 | 强基数（所有 $\alpha$） | 7 | 定理 |
| 10 | 超紧致基数 | 9 | 定理 |
| 11 | I3 | 10 | 定理 |
| 12 | I2 | 11 | 定理（$\mathrm{I2}\Rightarrow\mathrm{I3}$） |
| 13 | I1 | 12 | 约定（秩入秩公理的标准顺序） |
| 14 | I0 | 13 | 定理（$\mathrm{I0}\Rightarrow\mathrm{I1}$） |
| 15 | 伊卡洛斯基数 | 14 | 约定 |
| 16 | 莱因哈特基数 | 15 | 约定 |
| 17 | 伯克利基数 | 16 | 约定 |
| 18 | Ord-伯克利基数 | 17 | 约定；在秩伯克利读法下未知 |
| 19 | 极限 club 伯克利基数 | 18 | 未知（定义是重建的） |

第 1–14 行在标准文献中由一致性强度线性排序。第 15–19 行是阶梯的无选择端；那里的比较是该学科所报告的比较，本文档如实标记它们，而不是推导它们。有两点具体提醒：

1. **伯克利与莱因哈特。** 一致性强度把伯克利排在莱因哈特之上；概念大小则把莱因哈特排在伯克利之上，因为莱因哈特嵌入的定义域是 $V$，而伯克利基数只嵌入传递集。约定是按一致性强度排序并说明这一点，第 16 与第 17 行正是这样做的。
2. **Ord-伯克利与伯克利。** 在第 17 节采用的无界读法下，Ord-伯克利至少与伯克利一样强；在累积层级读法（秩伯克利）下，它是一种减弱，应位于伯克利之下。表格列出的是第一种读法；若翻转它，就会交换第 17 与第 18 行，并把极限 club 伯克利的依据从 `未知` 改为 `约定`。

## 来源

1. A. Kanamori，*The Higher Infinite*，第 2 版，Springer 2003 —— 从可测性到秩入秩公理的标准参考（第 4、5、9、26 章）。
2. J. Bagaria, P. Koellner, W. H. Woodin，*Large cardinals beyond choice*，Bulletin of Symbolic Logic 25 (2019) —— 伯克利、club 伯克利、极限 club 伯克利，以及无选择层级。
3. D. Blue, G. Sargsyan，*$\mathsf{AD}^{+}$ implies that $\omega_{1}$ is a club $\Theta$-Berkeley cardinal*，[arXiv:2402.01329](https://arxiv.org/abs/2402.01329) —— 第 18 节引用的参数化 club $\lambda$-伯克利定义。
4. V. Dimonte，*I0 and rank-into-rank axioms*，[arXiv:1707.02613](https://arxiv.org/abs/1707.02613) —— I0 文献以及伊卡洛斯的 sharp 校准。
5. J. Osinski，*Compactness beyond choice and HOD*，[arXiv:2609.25291](https://arxiv.org/abs/2609.25291) —— 秩伯克利、伯克利与 club 伯克利汇于一处，并给出紧致性刻画。
6. J. P. Aguilera, J. Bagaria, P. Lücke，*Large cardinals, structural reflection, and the HOD Conjecture*，[arXiv:2411.11568](https://arxiv.org/abs/2411.11568) —— 作为伯克利之减弱的秩伯克利，以及精确基数。

## 待对照来源核实的定义

本文档中有三个条目与其他十五个不在同一立足点上。它们连同精确陈述的不确定性列在此处，以便后续修订时可以在不触动其余部分的情况下修复它们。

1. **伊卡洛斯（第 14 节）。** 给出的定义是：$X\subseteq V_{\lambda+1}$，且存在非平凡的初等 $j:L(X,V_{\lambda+1})\prec L(X,V_{\lambda+1})$ 与 $\mathrm{crit}(j)<\lambda$，并且 $\lambda$ 是伊卡洛斯的，若存在这样的 $X$。不确定之处：(a) 标准定义量化的是“存在 $X$”还是“对每个 $X$”；(b) 模型是 $L(X,V_{\lambda+1})$ 还是闭包方式不同的 $L(V_{\lambda+1},X)$；(c) 临界点必须满足 $\mathrm{crit}(j)<\lambda$ 还是只需 $\mathrm{crit}(j)\le\lambda$；(d) 与 $(V_{\lambda+1})^{\sharp}$ 的确切关系，后者仅引自二手来源。置于 I0 与莱因哈特之间是一个猜测，依据是参数是 $V_{\lambda+1}$ 的子集，而非依据定理。
2. **Ord-伯克利（第 17 节）。** 流通中有两种读法，本条采用第一种。不确定之处：本项目中的“Ord-伯克利”是指 (i) 对每个 $\lambda$ 都是 $\lambda$-伯克利的，如无选择大基数文献中那样，还是 (ii) 累积层级性质“对所有 $\alpha<\lambda\le\beta$ 都存在 $j:V_{\beta}\to V_{\beta}$ 满足 $\alpha<\mathrm{crit}(j)<\lambda$”，后者在文献中是 Goldberg–Schlutzenberg 的*秩伯克利*，是对伯克利的减弱而非加强。两种读法把本条放在伯克利的相反两侧，因此在该术语被确定之前，阶梯位置确实未定。同样未核实的是：$\lambda$-伯克利在带 $\kappa\subseteq N$ 与带 $\kappa\in N$ 时是否等价，这决定 Ord-伯克利是否严格强于伯克利。
3. **极限 club 伯克利（第 18 节）。** 参数化的 club $\lambda$-伯克利定义已对照文献核实。不确定之处：(a) club 伯克利的确切无参数形式——量词是对所有传递 $M$（满足 $\kappa\subseteq M$），还是只对大小为 $<\lambda$ 的那些（$\lambda$ 固定），以及是否要求该 club 是 $\nu$-club；(b) 极限 club 伯克利是指“club 伯克利，且是 club 伯克利基数的极限”，还是“club 伯克利基数的极限”而不另设 club 伯克利要求，还是通过 $\nu$-club 细化来定义的某种东西；(c) 该概念在一致性强度上是否严格高于 club 伯克利。“极限”条款是最可能出错的部分。

另有两项记录在此，尽管其框内容被认为正确，因为它们在文献中常有不同陈述。

4. **I2（第 11 节）。** 闭包条件是 $V_{\lambda}\subseteq M$，其中 $\lambda=\sup_{n}j^{n}(\kappa)$。流通中的变体要求在 $V_{j(\kappa)}$ 处闭包（即超强性，严格更弱），或要求 $j$ 是某个特定 extender 的超幂；本条采用标准表述，但这些变体不可互换。
5. **$\alpha$-马洛（第 2 节）。** 基础情形是 $0$-马洛 $=$ 不可达，这正是使 $1$-马洛 $=$ 马洛的原因。以 $0$-马洛 $=$ 马洛为起点的来源会把每个下标移一位；本条陈述自己的约定，阶梯表使用它。
