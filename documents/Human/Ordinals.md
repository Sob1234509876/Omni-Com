
# Ordinals

Ordinals that should be implemented into `:projects:oc:oc-googology`. The ordinals below are sorted in ascending order. The ordinal $SHO$ represents the ordinal of the limit of BMS not $\varepsilon_0$. $TBMS$ stands for Transfinite Bashicu Matrix System which allows transfinite BMS column height. $TTBMS$ stands for Transfinite Transfinite Bashicu Matrix System which allows non recursive BMS column height. $NRBMS$ stands for Non Recursive Bashicu Matrix System, the definition is over [here (Chinese GGG wiki link)](https://wiki.googology.top/index.php/%E9%9D%9E%E9%80%92%E5%BD%92BMS).

Note that for some ordinals that the Googology library can not represent, we use a register maintained by the Googology library to keep a comparison for all the ordinals. For some ordinals that can not represent a fundamental sequence, we assume it has a fundemental sequence that is strong enough that for the ordinal $\alpha$ and another ordinal $\beta<\alpha$ we have $10\{\beta\}n<10\{\alpha\}m$ where $n$ is a normal integer (not a big number) and $m>2$.

1. $0$~$SHO$ ($0\sim\lim(BMS)=TBMS(0)(1^\omega)$): Use $BMS$.
2. $SHO$~$LRPO$ ($\lim(BMS)=TBMS(0)(1^\omega)\sim TTBMS(0)(1^{\min\alpha\rightarrow TNRBMS(0)(1^\alpha)\space\mathrm{ Fix Point}})$): Use a simple notation $TBMS(0)(1^\alpha)$ when $\alpha<\omega_1^{CK}$ and $TTBMS(0)(1^\alpha)$ when $\alpha\geq\omega_1^{CK}$. Do note that $LRPO<\omega_1^{CK}$, it is because the non recursive ordinal column height on the superscript is more like the non recursive ordinal/cardinal in OCFs, thus making $LRPO$ a recursive ordinal. Do note that I do not wish you (The Agent) to implement $TTBMS$, instead I wish you to make an ordinal function $f:\mathrm{Ord}\rightarrow\mathrm{Ord}$ mapping $\alpha\in\mathrm{Ord}$ to $TTBMS(0)(1^\alpha)$, and you can assume that for any ordinal $\alpha<\omega_1^{CK}$ that $f(\alpha)>\alpha$ and $\omega_1^{CK}\leq\alpha<\min\alpha\rightarrow TNRBMS(0)(1^\alpha)\space\mathrm{ Fix Point}$ that $\psi(\alpha)<f(\alpha)<\alpha$ (Assuming that there exists an OCF for ordinals up to $\alpha$). After implementing $f:\mathrm{Ord}\rightarrow\mathrm{Ord}$, I wish the ordinal after invoking the function should be shown as the logic at the first sentence.
3. H projection and other projections. See [over here](Ordinals/Projection%20Ordinals.md) for definition.
4. Proof-theoretic ordinals. See [over here](Ordinals/PTOs.md) for some PTOs.
5. $\omega_1^{CK}$~$\psi_H(\varepsilon_{H+1})=\lim(NRBMS)=TNRBMS(0)(1^\omega)$: Use $NRBMS$.
6. $\psi_H(\varepsilon_{H+1})=\lim(NRBMS)=TNRBMS(0)(1^\omega)$~$\min\alpha\rightarrow TNRBMS(0)(1^\alpha)\space\mathrm{ Fix Point}$: Use the same way shown in $2$, but also changing the notation from $TBMS(0)(1^\alpha)$ and $TTBMS(0)(1^\alpha)$ to $TNRBMS(0)(1^\alpha)$.
7. The H projection and other projections' ordinal. See [over here](Ordinals/Projection%20Ordinals.md) for definition.
8. Gap ordinals and wordly ordinals.
9. $\omega_1$~$\min\alpha\rightarrow\omega_\alpha\space\mathrm{ Fix Point}=\psi_{\mathbb{I}}(\mathbb{I})$: Use the same way shown in $2$, but also changing the notation from $TBMS(0)(1^\alpha)$ and $TTBMS(0)(1^\alpha)$ to $\omega_\alpha$.
10. $\mathbb{I}=\min\mathrm{ Inaccessible Cardinal}$~$\mathbb{I}(1@(1@(1@(\dots))))$: See [over here](Cardinals/Inaccessible%20Cardinal%20Extension.md).
11. Mahlo Cardinal ($\mathbb{M}_\alpha$): See [over here](Cardinals/Big%20Cardinals.md).
12. $\alpha$-Mahlo Cardinal ($\mathbb{M}(\alpha,\beta)$): See [over here](Cardinals/Big%20Cardinals.md).
13. hyper-Mahlo Cardinal ($\mathbb{M}(1,0,\alpha)$): See [over here](Cardinals/Big%20Cardinals.md).
14. Weakly Compact Cardinal ($\mathbb{K}_\alpha$): See [over here](Cardinals/Big%20Cardinals.md).
15. Indescribable Cardinal ($\mathbb{K}^{\Pi^n_m}_\alpha$): See [over here](Cardinals/Big%20Cardinals.md).
16. Measurable Cardinal: See [over here](Cardinals/Big%20Cardinals.md), some how try to make a possible way of storing different sized Measurable Cardinal.
17. $\alpha$-Strong and Strong Cardinals: See [over here](Cardinals/Big%20Cardinals.md), some how try to make a possible way of storing different sized $\alpha$-Strong Cardinal.
18. Supercompact Cardinal: See [over here](Cardinals/Big%20Cardinals.md).
19. I3~I0 Cardinal: See [over here](Cardinals/Big%20Cardinals.md).
20. Icarus Cardinal: See [over here](Cardinals/Big%20Cardinals.md).
21. Reinhardt Cardinal: See [over here](Cardinals/Big%20Cardinals.md).
22. Berkeley Cardinal: See [over here](Cardinals/Big%20Cardinals.md).
23. Ord-Berkeley Cardinal: See [over here](Cardinals/Big%20Cardinals.md).
24. Limit club Berkeley Cardinal: See [over here](Cardinals/Big%20Cardinals.md).
25. $\Omega$~$\Omega_{\mathrm{Absolute}}$: See [over here](Cardinals/Fake%20Googology.md).
