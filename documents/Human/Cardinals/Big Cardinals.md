# Big Cardinals

This document is the reference for the large cardinal notions above the inaccessible cardinals, in
increasing order of **consistency strength**. It continues the hierarchy of
[Inaccessible Cardinal Extension](Inaccessible%20Cardinal%20Extension.md), whose every cardinal lies
below the least Mahlo cardinal, and it uses the notation $\mathbb{I}_{\alpha}$ introduced there.

The organizing rule is the one demanded for this document: **where a cardinal has a characterization
by a nontrivial elementary embedding, that characterization is the primary definition**, and the
set-theoretic characterization (stationary set, indescribability, measure, partition property)
follows afterwards as *equivalently*. The entries below Mahlo therefore have no boxed embedding
definition, because no such definition exists for them: any nontrivial elementary embedding
$j : V \to M$ with critical point $\kappa$ already makes $\kappa$ measurable, and measurability is
strictly stronger than every notion up to and including total indescribability. The embedding-first
region begins exactly at the measurable cardinals, and the first entries say so explicitly.

## Conventions

1. **Ambient theory.** Everything is stated in $\mathrm{ZFC}$ unless the entry says otherwise. The
   choiceless entries — Reinhardt, Berkeley, Ord-Berkeley, limit club Berkeley — are stated in
   $\mathrm{ZF}$ (or $\mathrm{ZF} + \mathrm{DC}$), and each says so at the top of its own section.
2. **Embeddings.** An *elementary embedding* $j : V \to M$ means: $M$ is a transitive class with
   $\mathrm{Ord} \subseteq M$, and $j$ is a nontrivial elementary embedding of $\langle V,\in\rangle$
   into $\langle M,\in\rangle$. The *critical point* is
   $\mathrm{crit}(j) = \min\{\alpha : j(\alpha) \neq \alpha\}$; it exists exactly when $j$ is
   nontrivial, and then $j(\alpha) = \alpha$ for all $\alpha < \mathrm{crit}(j)$.
   For set models the same notation is used with $V$ replaced by $V_{\lambda}$ or $V_{\lambda+1}$.
3. **Ranks.** $V_{\lambda}$ is the set of sets of rank $< \lambda$, and
   $V_{\lambda+1} = \mathcal{P}(V_{\lambda})$. $L(V_{\lambda+1})$ is the least model of
   $\mathrm{ZF}$ containing $V_{\lambda+1}$ and all ordinals.
4. **Notation for enumerations.** $\mathbb{I}_{\alpha}$ is the $\alpha$-th inaccessible cardinal as in
   the companion document, and $\mathbb{M}_{\alpha}$ is the $\alpha$-th Mahlo cardinal, enumerated in
   increasing order from $0$.
5. **Ordering convention.** The sections are ordered by consistency strength. Where consistency
   strength does not decide a comparison, the order follows *conceptual size* — how much of the
   universe the axiom claims to see — and the ladder table at the end marks every comparison as
   `theorem`, `conventional`, or `unknown`. The order is not in general total, and two of its links are
   conventions of this document rather than theorems: Icarus against its neighbours, and Berkeley
   against Reinhardt. Both are flagged in place and again in the final section.
6. **One comparison needs a warning.** It is sometimes said that strongness and Woodinness are not
   linearly ordered. The correct statement, and the one used here, is: every strong cardinal is
   Woodin, so strongness lies *above* Woodinness by a theorem, while the converse fails: a Woodin
   cardinal need not be strong. The genuinely convention-laden comparison in this document is not
   strong/Woodin but Berkeley/Reinhardt, where consistency strength and conceptual size point in
   opposite directions.

## 1. Mahlo cardinals

A cardinal $\kappa$ is **Mahlo** iff it is inaccessible and the regular cardinals below it are
stationary in $\kappa$.

$$
\boxed{
\kappa \text{ is Mahlo}
\;\Longleftrightarrow\;
\kappa \text{ is inaccessible}
\;\wedge\;
\{\, \alpha<\kappa : \alpha \text{ is regular} \,\} \text{ is stationary in } \kappa .
}
$$

Equivalently: every club $C \subseteq \kappa$ contains a regular cardinal; equivalently, every club
contains an inaccessible cardinal (the two formulations agree because the set of limit points of a
club is again a club); equivalently, $\kappa$ is inaccessible and stationarily many cardinals below
$\kappa$ are inaccessible.

**No embedding definition.** Mahlo cardinals have no characterization by a single nontrivial
elementary embedding with domain $V$, because any such embedding has a critical point and therefore
witnesses measurability, which is far above Mahlo. The characterization here is a *reflection*
property of the club filter on $\kappa$, and this is the reason the embedding-first rule has nothing
to say until measurability.

Properties.

1. $\kappa$ Mahlo implies $\kappa$ inaccessible; the least Mahlo cardinal $\mathbb{M}_{0}$ lies above
   every cardinal of the companion document, including every term of its self-nesting hierarchy.
2. Every Mahlo cardinal is a limit of inaccessible cardinals, so
   $\mathbb{I}_{\alpha} < \mathbb{M}_{0}$ for every $\alpha < \mathbb{M}_{0}$.
3. The Mahlo cardinals are **not closed**: if $\langle \mathbb{M}_{\alpha} : \alpha<\omega\rangle$ is
   increasing, its supremum has cofinality $\omega$ and is singular, hence not Mahlo. The enumeration
   $\mathbb{M}_{\alpha}$ therefore has genuine gaps.
4. Stationarity may not be weakened to unboundedness. With *unbounded* in place of *stationary* the
   notion collapses into the hyper-inaccessible hierarchy and stays below every Mahlo cardinal; that is
   exactly what the companion document exploits.

**Where it sits.** Above everything in the companion document and below $\alpha$-Mahlo cardinals,
which is the weakest genuine strengthening.

## 2. $\alpha$-Mahlo cardinals

The property *$\kappa$ is $\alpha$-Mahlo* is defined by transfinite recursion on $\alpha$, with the
base case at $0$ being inaccessibility, so that $1$-Mahlo is exactly Mahlo.

$$
\boxed{
\begin{aligned}
&\kappa \text{ is } 0\text{-Mahlo} &&\Longleftrightarrow\; \kappa \text{ is inaccessible},\\[2pt]
&\kappa \text{ is } (\alpha+1)\text{-Mahlo} &&\Longleftrightarrow\; \kappa \text{ is Mahlo and } \{\, \beta<\kappa : \beta \text{ is } \alpha\text{-Mahlo} \,\} \text{ is stationary in } \kappa,\\[2pt]
&\kappa \text{ is } \lambda\text{-Mahlo} &&\Longleftrightarrow\; \forall \alpha<\lambda \ (\kappa \text{ is } \alpha\text{-Mahlo}) \quad (\lambda \text{ a limit ordinal}).
\end{aligned}
}
$$

In words: at a successor step one demands that the previous level be stationary below $\kappa$, and at
a limit step one demands all lower levels at once. The stationary requirement is what separates this
from the companion document's unbounded hierarchy, and it is the only step that carries the strength.

Properties.

1. $\alpha$-Mahlo is monotone in $\alpha$: if $\kappa$ is $\alpha$-Mahlo and $\beta<\alpha$ then
   $\kappa$ is $\beta$-Mahlo.
2. $1$-Mahlo is Mahlo, $2$-Mahlo is "Mahlo and stationarily many Mahlo cardinals below", and so on;
   the convention $0$-Mahlo $=$ inaccessible is what makes the indexing come out right.
3. For each fixed $\alpha$ the $\alpha$-Mahlo cardinals are enumerated in increasing order as
   $\mathbb{M}(\alpha,\beta)$, in the same style as $\mathbb{I}(\alpha,\beta)$ in the companion
   document; $\mathbb{M}(1,\beta) = \mathbb{M}_{\beta}$.
4. Every $\alpha$-Mahlo cardinal is a limit of $\beta$-Mahlo cardinals for every $\beta<\alpha$, and
   the set of such limits is itself stationary.

**Where it sits.** Strictly between Mahlo and hyper-Mahlo for every fixed $\alpha$.

**Caveat.** Sources differ on whether $0$-Mahlo is inaccessibility or Mahlo itself. This document
fixes $0$-Mahlo $=$ inaccessible, as stated in the box, so that the convention "$1$-Mahlo $=$ Mahlo"
holds; under the other convention every index shifts down by one.

## 3. Hyper-Mahlo cardinals

A cardinal $\kappa$ is **hyper-Mahlo** iff it is $\alpha$-Mahlo for every $\alpha<\kappa$.

$$
\boxed{
\kappa \text{ is hyper-Mahlo}
\;\Longleftrightarrow\;
\forall \alpha<\kappa \ (\kappa \text{ is } \alpha\text{-Mahlo}).
}
$$

This is the exact analogue of *hyper-inaccessible* in the companion document: the diagonal form, where
the index runs over all ordinals below the cardinal itself rather than over a fixed ordinal.

Properties.

1. Hyper-Mahlo is stronger than $\alpha$-Mahlo for each fixed $\alpha$, since a hyper-Mahlo cardinal is
   $\alpha$-Mahlo by definition.
2. Hyper-Mahlo is weaker than "$\kappa$ is $\kappa$-Mahlo": the latter asks for one more stationary
   reflection step, at index $\kappa$ itself.
3. The hyper-Mahlo cardinals are stationary below every weakly compact cardinal, and every weakly
   compact cardinal is hyper-Mahlo.
4. "Hyper-hyper-Mahlo" and the higher iterates are defined by iterating the diagonal step, exactly as
   in the inaccessible case; they all remain below weakly compact.

**Where it sits.** Above every fixed $\alpha$-Mahlo level and below weakly compact.

## 4. Weakly compact cardinals

A cardinal $\kappa$ is **weakly compact** iff it is inaccessible and $\Pi^{1}_{1}$-indescribable,
i.e. iff for every $A \subseteq V_{\kappa}$ and every $\Pi^{1}_{1}$ sentence $\varphi$ of the
second-order language with a predicate for $A$, truth in $\langle V_{\kappa},\in,A\rangle$ is reflected
to some $\langle V_{\alpha},\in,A\cap V_{\alpha}\rangle$ with $\alpha<\kappa$.

$$
\boxed{
\kappa \text{ is weakly compact}
\;\Longleftrightarrow\;
\kappa \text{ is inaccessible} \;\wedge\; \kappa \text{ is } \Pi^{1}_{1}\text{-indescribable}.
}
$$

Equivalently: every tree of height $\kappa$ with levels of size $<\kappa$ has a cofinal branch (the
tree property); equivalently $\kappa \to (\kappa)^{2}_{2}$; equivalently, every
$A \subseteq V_{\kappa}$ is reflected by some $\alpha<\kappa$, which is the indescribability
condition of the box written out.

**No embedding definition.** There is no characterization of weak compactness by a nontrivial
elementary embedding $j : V \to M$ with critical point $\kappa$: any such embedding outright gives
measurability. The embedding-flavoured reformulations that exist are about embeddings of *set-sized*
models, and they are not of the form used in this document, so the box above is the primary definition
and the embedding-first rule does not yet apply.

Properties.

1. Weakly compact cardinals are Mahlo, hyper-Mahlo, and limits of hyper-Mahlo cardinals.
2. Weak compactness is exactly $\Pi^{1}_{1}$-indescribability, so it is the first rung of the
   indescribability ladder of the next section.
3. The least weakly compact cardinal is a limit of Mahlo cardinals, and
   $\mathbb{M}_{\alpha}$ is below it for every $\alpha < $ that cardinal.
4. The tree property and the partition relation are genuinely equivalent to the boxed statement, not
   merely consequences of it.

**Where it sits.** Above hyper-Mahlo and below $\Pi^{1}_{2}$-indescribability.

## 5. Indescribable cardinals

For $m \ge 1$, a cardinal $\kappa$ is **$\Pi^{1}_{m}$-indescribable** iff for every
$A \subseteq V_{\kappa}$ and every $\Pi^{1}_{m}$ sentence $\varphi$,

$$
\boxed{
\langle V_{\kappa},\in,A\rangle \models \varphi
\;\Longrightarrow\;
\exists \alpha<\kappa \ \bigl( \langle V_{\alpha},\in,A\cap V_{\alpha}\rangle \models \varphi \bigr).
}
$$

A cardinal is **totally indescribable** iff it is $\Pi^{1}_{m}$-indescribable for every $m<\omega$.

The hierarchy is cumulative in $m$: $\Pi^{1}_{m}$-indescribability implies
$\Pi^{1}_{n}$-indescribability for $n<m$, because a $\Pi^{1}_{n}$ sentence is, uniformly, a
$\Pi^{1}_{m}$ sentence. $\Pi^{1}_{1}$ is weakly compact by definition, so the ladder starts at the
previous section.

Properties.

1. $\Pi^{1}_{1}$-indescribable $=$ weakly compact; $\Pi^{1}_{2}$-indescribable is strictly stronger,
   and already implies that $\kappa$ is a limit of weakly compact cardinals.
2. Total indescribability is strictly stronger than $\Pi^{1}_{m}$-indescribability for each fixed $m$;
   it is the union of the ladder.
3. Total indescribability is strictly weaker than measurability: every measurable cardinal is totally
   indescribable, and every measurable cardinal is a limit of totally indescribable cardinals.
4. Indescribability is a reflection property of the structure $\langle V_{\kappa},\in,A\rangle$ with
   $A$ ranging over subsets of $V_{\kappa}$; it is not a property of embeddings.

**Where it sits.** Between weakly compact and measurable, and it is the last entry of the
embedding-free region.

**Caveat.** The definition quantifies existentially over the reflecting $\alpha$. Some sources give a
stationary version ("stationarily many $\alpha$ reflect $A$"); the two agree for $\Pi^{1}_{1}$, and
total indescribability can be stated either way, but the equivalence for intermediate $m$ is not
something this document relies on, so the existential form is used throughout.

## 6. Measurable cardinals

A cardinal $\kappa$ is **measurable** iff it is the critical point of a nontrivial elementary embedding
of the whole universe into a transitive class.

$$
\boxed{
\kappa \text{ is measurable}
\;\Longleftrightarrow\;
\exists\, j : V \to M \ \text{elementary},\ M \text{ transitive with } \mathrm{Ord}\subseteq M,\
\mathrm{crit}(j) = \kappa,\ j \text{ nontrivial}.
}
$$

Equivalently: there is a $\kappa$-complete nonprincipal ultrafilter on $\kappa$. From such an
ultrafilter $U$, the ultrapower $j_{U} : V \to \mathrm{Ult}$ is well founded by $\kappa$-completeness;
its transitive collapse is the $M$ of the box, and $\kappa$ is recovered as $\mathrm{crit}(j_{U})$.
Taking $U$ normal one may additionally arrange $M^{\kappa} \subseteq M$, hence
$V_{\kappa+1} \subseteq M$ and $j(\kappa) > \kappa$.

This is the first entry with a genuine embedding definition, and it sets the pattern for everything
above: the embedding is the definition, and the measure is a translation of it.

Properties.

1. Measurable cardinals are totally indescribable, Mahlo, and limits of all of the cardinals of the
   previous sections.
2. $V_{\kappa} \models \mathrm{ZFC}$ for measurable $\kappa$; the embedding fixes every ordinal below
   $\kappa$ and moves $\kappa$ itself.
3. The $\kappa$-complete ultrafilters on $\kappa$ are organized by the Mitchell order; that refinement
   is not needed here, except to note that measurability is the first level of a much longer
   measure-based hierarchy.
4. Measurability is exactly $1$-strongness in the sense of the next section.

**Where it sits.** The first entry with $j : V \to M$, above total indescribability and below every
strongness level.

## 7. $\alpha$-strong and strong cardinals

For an ordinal $\alpha$, a cardinal $\kappa$ is **$\alpha$-strong** iff it is the critical point of an
embedding that captures the first $\alpha$ many ranks above $\kappa$.

$$
\boxed{
\kappa \text{ is } \alpha\text{-strong}
\;\Longleftrightarrow\;
\exists\, j : V \to M \ \text{elementary},\ \mathrm{crit}(j)=\kappa,\ V_{\kappa+\alpha} \subseteq M .
}
$$

A cardinal is **strong** iff it is $\alpha$-strong for every $\alpha$.

$$
\boxed{
\kappa \text{ is strong}
\;\Longleftrightarrow\;
\forall \alpha \ (\kappa \text{ is } \alpha\text{-strong})
\;\Longleftrightarrow\;
\forall \lambda \ \exists j : V \to M \ \bigl( \mathrm{crit}(j)=\kappa \wedge V_{\lambda} \subseteq M \bigr).
}
$$

Equivalently, in words: for every $\lambda$ there is an embedding of $V$ into a transitive class that
agrees with $V$ at least up to rank $\lambda$ and has critical point $\kappa$. The two displayed forms
agree because $V_{\lambda} \subseteq V_{\kappa+\alpha}$ whenever $\lambda \le \kappa+\alpha$.

Properties.

1. $0$-strongness is automatic, since $V_{\kappa} \subseteq M$ for any nontrivial $j$ with
   $\mathrm{crit}(j)=\kappa$; $1$-strongness is exactly measurability; and $\alpha$-strongness is
   monotone in $\alpha$.
2. Strong cardinals are measurable, and a strong cardinal is a limit of strong cardinals below it.
3. Some authors add the clause $j(\kappa) > \kappa+\alpha$. It is redundant, but the proof of
   redundancy uses Kunen's inconsistency, so it is not a trivial omission.
4. For fixed $\alpha$ the notion is weaker than Woodinness; the case "for every $\alpha$" is stronger
   than Woodinness. The section therefore straddles the next entry, and the ladder table lists
   $\alpha$-strong before Woodin and strong after it.

**Where it sits.** For fixed $\alpha \ge 1$, between measurable and Woodin; the case of all $\alpha$
sits between Woodin and supercompact.

**Caveat.** The section deliberately covers the whole $\alpha$-hierarchy in one place, as requested,
even though the hierarchy is not monotone in position across Woodin. The table, not the section order,
is the authority on where each level sits.

## 8. Woodin cardinals

A cardinal $\kappa$ is **Woodin** iff for every function $f : \kappa \to \kappa$ there is a closure
point of $f$ below $\kappa$ that is the critical point of an embedding strong enough to see $j(f)$ at
that point.

$$
\boxed{
\kappa \text{ is Woodin}
\;\Longleftrightarrow\;
\forall f : \kappa\to\kappa\ \exists \alpha<\kappa
\Bigl( f''\alpha \subseteq \alpha \ \wedge\ \exists j : V\to M \
\bigl( \mathrm{crit}(j)=\alpha \wedge V_{j(f)(\alpha)} \subseteq M \bigr) \Bigr).
}
$$

Equivalently: for every $A \subseteq V_{\kappa}$ there is $\alpha<\kappa$ such that $\alpha$ is
$A$-strong, meaning that for every $\lambda$ there is $j : V \to M$ with $\mathrm{crit}(j)=\alpha$,
$V_{\lambda}\subseteq M$, and $j(A)\cap V_{\lambda} = A\cap V_{\lambda}$; equivalently, $\kappa$ is
inaccessible and the set of such $\alpha$ is cofinal in $\kappa$.

The definition is a *localized* strongness: instead of asking one cardinal to be strong for all
$\lambda$, it asks that below $\kappa$, for every target function, some $\alpha$ be strong up to the
level $j(f)(\alpha)$ that the embedding itself produces.

Properties.

1. Woodin cardinals are inaccessible and Mahlo, and $\kappa$ is a limit of measurable cardinals.
2. Every strong cardinal is Woodin; Woodinness does not imply strongness.
3. Woodinness is the standard hypothesis under which the theory of $\mathrm{HOD}$ and the
   $\Omega$-conjecture is developed, and it is the first entry whose definition quantifies over all
   functions on $\kappa$ rather than over a fixed measure or filter.
4. The definition is equivalent to the form "for every $A \subseteq V_{\kappa}$ the $A$-strong
   $\alpha$ are cofinal in $\kappa$", which is the form most often used in practice.

**Where it sits.** Above every fixed $\alpha$-strong level and below strong cardinals.

**Caveat (ordering).** Strongness implies Woodinness, so these two are ordered by a theorem, not by
convention; what fails is the converse. See item 6 of the conventions.

## 9. Supercompact cardinals

For $\lambda \ge \kappa$, a cardinal $\kappa$ is **$\lambda$-supercompact** iff it is the critical point
of an embedding whose target is closed under $\lambda$-sequences and which moves $\kappa$ past
$\lambda$.

$$
\boxed{
\kappa \text{ is } \lambda\text{-supercompact}
\;\Longleftrightarrow\;
\exists j : V\to M \
\bigl( \mathrm{crit}(j)=\kappa \ \wedge\ j(\kappa) > \lambda \ \wedge\ M^{\lambda}\subseteq M \bigr).
}
$$

A cardinal is **supercompact** iff it is $\lambda$-supercompact for every $\lambda \ge \kappa$.

$$
\boxed{
\kappa \text{ is supercompact}
\;\Longleftrightarrow\;
\forall \lambda\ge\kappa \ (\kappa \text{ is } \lambda\text{-supercompact}).
}
$$

Equivalently: for every $\lambda \ge \kappa$ there is a $\kappa$-complete, fine, normal ultrafilter on
$\mathcal{P}_{\kappa}\lambda$, i.e. a normal fine measure; equivalently, for every $\lambda$ there is
$j$ with $\mathrm{crit}(j)=\kappa$, $j(\kappa)>\lambda$ and $M$ containing all $\lambda$-sequences of
its own elements.

Properties.

1. $\kappa$ is $\kappa$-supercompact exactly when $\kappa$ is measurable; the hierarchy above $\kappa$
   is what carries the strength.
2. Supercompact cardinals are strong, hence Woodin, hence measurable; $\kappa$ supercompact is a limit
   of strong cardinals below $\kappa$.
3. The measure formulation is a genuine equivalence, not a consequence: fine normal measures on
   $\mathcal{P}_{\kappa}\lambda$ and $\lambda$-supercompact embeddings are intertranslatable.
4. Supercompactness is the first entry whose embedding demands closure of the target under sequences of
   length $\lambda$ for arbitrarily large $\lambda$, which is strictly more than strongness demands.

**Where it sits.** Above strong cardinals and below every rank-into-rank axiom.

## 10. I3

**I3** is the weakest of the rank-into-rank axioms: a nontrivial elementary embedding of a rank-initial
segment into itself.

$$
\boxed{
\mathrm{I3}
\;\Longleftrightarrow\;
\exists \lambda \ \exists j : V_{\lambda} \prec V_{\lambda} \
\bigl( \lambda \text{ a limit ordinal},\ j \text{ nontrivial},\ \mathrm{crit}(j)<\lambda \bigr).
}
$$

Here $j$ is a set-sized embedding, $V_{\lambda}$ satisfies $\mathrm{ZFC}$ without the power set axiom,
and $\mathrm{crit}(j)$ exists because $j$ is nontrivial. One may always take $\lambda$ to be the limit
of the critical sequence: if $\kappa_{0} = \mathrm{crit}(j)$ and
$\kappa_{n+1} = j(\kappa_{n})$, then $\lambda' = \sup_{n}\kappa_{n}$ satisfies
$j(\lambda')=\lambda'$, so $j$ restricts to an embedding $V_{\lambda'}\to V_{\lambda'}$. After that
normalization $\mathrm{cf}(\lambda)=\omega$ and $\lambda$ is a strong limit cardinal.

Properties.

1. Each $\kappa_{n}$ of the critical sequence is measurable, and much more; $\lambda$ is a limit of
   measurable cardinals.
2. I3 is compatible with the Axiom of Choice: it is a statement about a set-sized structure, so
   Kunen's inconsistency does not apply to it.
3. I3 does not imply I2; it is the bottom of the rank-into-rank ladder.
4. I3 implies that there are unboundedly many measurables below $\lambda$, but it says nothing about
   $V$ beyond $\lambda$.

**Where it sits.** Above supercompact cardinals and below I2.

## 11. I2

**I2** is a nontrivial elementary embedding of the whole universe whose target captures the rank
$V_{\lambda}$, where $\lambda$ is the limit of the critical sequence.

$$
\boxed{
\mathrm{I2}
\;\Longleftrightarrow\;
\exists j : V \to M \ \text{elementary},\ M \text{ transitive},\ \mathrm{crit}(j)=\kappa,\
V_{\lambda}\subseteq M,\ \lambda = \sup_{n<\omega} j^{n}(\kappa).
}
$$

The closure condition is at the limit of the critical sequence, not at $\kappa$ itself. In particular
$j(\lambda)=\lambda$ and $V_{j(\kappa)} \subseteq M$ follows, so an I2 embedding is in particular a
**superstrong** embedding; superstrongness is the natural notion between strongness and I2.

Properties.

1. I2 implies I3: restricting $j$ to $V_{\lambda}$ gives an elementary
   $V_{\lambda}\to V_{\lambda}$ with critical point $\kappa$, because $j(\lambda)=\lambda$ and
   $V_{\lambda}\subseteq M$.
2. I2 embeddings are superstrong, hence strong, hence Woodin, hence measurable.
3. I2 is strictly stronger than I3 in consistency strength.
4. The closure of $M$ at $V_{\lambda}$ is what makes the restricted embedding of $V_{\lambda}$ possible;
   no such restriction is available for a merely superstrong embedding.

**Where it sits.** Above I3 and below I1.

**Caveat.** Sources differ in how much closure of $M$ they demand in the statement of I2. The
formulation here — closure at $\sup_{n} j^{n}(\kappa)$ — is the standard one; statements that close $M$
only at $V_{j(\kappa)}$ describe superstrongness and are strictly weaker.

## 12. I1

**I1** is a nontrivial elementary embedding of $V_{\lambda+1}$ into itself.

$$
\boxed{
\mathrm{I1}
\;\Longleftrightarrow\;
\exists \lambda \ \exists j : V_{\lambda+1} \prec V_{\lambda+1} \
\bigl( j \text{ nontrivial},\ \mathrm{crit}(j)<\lambda \bigr).
}
$$

The embedding now acts on the full power set of $V_{\lambda}$, not merely on $V_{\lambda}$, which is
what separates I1 from I3.

Properties.

1. I1 implies I3, by restricting $j$ to $V_{\lambda}$.
2. I1 is compatible with the Axiom of Choice; it is a set-sized statement.
3. I1 implies that $\lambda$ is a limit of measurable cardinals and that many I3 embeddings exist below
   $\lambda$.
4. I1 does not imply I0, and the two are distinct rungs of the ladder.

**Where it sits.** Above I2 and below I0.

## 13. I0

**I0** is a nontrivial elementary embedding of $L(V_{\lambda+1})$ into itself.

$$
\boxed{
\mathrm{I0}
\;\Longleftrightarrow\;
\exists \lambda \ \exists j : L(V_{\lambda+1}) \prec L(V_{\lambda+1}) \
\bigl( j \text{ nontrivial},\ \mathrm{crit}(j)<\lambda \bigr).
}
$$

This is the strongest of the four rank-into-rank axioms and the strongest large cardinal notion known
to be compatible with the Axiom of Choice in the rank-into-rank family.

Properties.

1. I0 implies I1: $V_{\lambda+1}$ is an element of $L(V_{\lambda+1})$, so $j$ fixes it and restricts to
   an elementary embedding $V_{\lambda+1}\to V_{\lambda+1}$ with the same critical point.
2. I0 is not known to be inconsistent with $\mathrm{ZFC}$, and no consistency proof from below is
   available; it is an axiom of infinity in its own right.
3. The model $L(V_{\lambda+1})$ is a model of $\mathrm{ZF}$, and the embedding is a class embedding
   of that model. This is the technical reason the axiom survives Kunen's theorem: the embedding is
   not an embedding of $V$.
4. Kunen's inconsistency bounds the family from above: there is no nontrivial elementary
   $j : V_{\lambda+2} \to V_{\lambda+2}$, so I0 is the last rung of the rank-into-rank ladder.

**Where it sits.** The top of the rank-into-rank ladder, above I1 and below Reinhardt cardinals.

## 14. Icarus cardinals

**Status: the definition below is the best formulation available to this document, and it is flagged
for verification.** An *Icarus set* is a subset $X \subseteq V_{\lambda+1}$ such that the model built
from $V_{\lambda+1}$ and the parameter $X$ has a nontrivial self-embedding with critical point below
$\lambda$.

$$
\boxed{
X \text{ is an Icarus set}
\;\Longleftrightarrow\;
X \subseteq V_{\lambda+1}
\ \wedge\
\exists j : L(X, V_{\lambda+1}) \prec L(X, V_{\lambda+1}) \
\bigl( j \text{ nontrivial},\ \mathrm{crit}(j)<\lambda \bigr),
}
$$

where $L(X,V_{\lambda+1})$ is the least model of $\mathrm{ZF}$ containing $V_{\lambda+1}$, the set $X$,
and all ordinals. A cardinal $\lambda$ is then called an **Icarus cardinal** if some such $X$ exists.

The parameter-free case is exactly I0: $L(\varnothing, V_{\lambda+1}) = L(V_{\lambda+1})$, so every I0
embedding is an Icarus embedding with the trivial parameter. Conversely, an embedding of the larger
model $L(X,V_{\lambda+1})$ restricts to one of $L(V_{\lambda+1})$, because $V_{\lambda+1}$ is an
element of that model and is therefore fixed by $j$. So Icarus lies at or above I0, and the parameter
$X$ is what the embedding must additionally respect.

Properties.

1. Icarus with parameter $X$ implies I0, by the restriction just described.
2. Icarus is a rank-into-rank style notion: the embedding is of a class model built from a set, not of
   $V$, so Kunen's inconsistency does not apply and the axiom is not known to fail with Choice.
3. In the literature the strength of an Icarus set is calibrated against sharps:
   $(V_{\lambda+1})^{\sharp}$ is quoted as implying that $V_{\lambda+1}$ is Icarus, and
   $(V_{\lambda+1})^{\sharp\sharp}$ as implying that, so the Icarus assumptions form a ladder of their
   own above I0.
4. The notion is stated for a *set* parameter $X \subseteq V_{\lambda+1}$; quantifying over all such
   $X$ is what turns it into a cardinal property of $\lambda$.

**Where it sits.** Above I0 and below Reinhardt cardinals. This placement is by convention: no theorem
of this document separates Icarus from I0, and the position relative to I1 is inherited from I0.

**Caveat.** The formulation is assembled from secondary sources and is *not* verified against a primary
definition; see the final section. In particular the exact closure required of $L(X,V_{\lambda+1})$, and
whether the cardinal property is "some $X$" or "every $X$", are the two points that need checking.

## 15. Reinhardt cardinals

*This entry is stated in $\mathrm{ZF}$, or in $\mathrm{ZF}+\mathrm{DC}$; it is refuted by the Axiom of
Choice.*

A cardinal $\kappa$ is **Reinhardt** iff it is the critical point of a nontrivial elementary embedding
of the universe into itself.

$$
\boxed{
\kappa \text{ is Reinhardt}
\;\Longleftrightarrow\;
\exists j : V \to V \ \text{nontrivial elementary with } \mathrm{crit}(j)=\kappa .
}
$$

Equivalently: there is a nontrivial elementary $j : V \to V$, and $\kappa$ is its critical point; the
domain and the codomain are the same class, which is what distinguishes the notion from every entry
above.

**Inconsistent with Choice (Kunen).** There is no nontrivial elementary embedding $j : V \to V$ in
$\mathrm{ZFC}$. The proof uses Choice in an essential way, through an $\omega$-Jónsson function on the
critical sequence, and the same argument rules out $j : V_{\lambda+2}\to V_{\lambda+2}$. Hence the
Reinhardt axiom is only stated in $\mathrm{ZF}$ or $\mathrm{ZF}+\mathrm{DC}$, where its consistency is
open.

Properties.

1. Reinhardt implies I0 and hence all of the rank-into-rank axioms: restricting $j$ to
   $L(V_{\lambda+1})$ for $\lambda$ the limit of the critical sequence gives an I0 embedding, and
   restricting to $V_{\lambda+1}$ gives I1.
2. Reinhardt is *conceptually* the largest notion in this document apart from Berkeley: the embedding
   is an embedding of the entire universe into itself, so no set-theoretic structure is beyond its
   reach.
3. Its consistency strength is nonetheless below that of Berkeley cardinals, which require such
   embeddings for every transitive set containing the cardinal and not merely for $V$ itself, and
   require them uniformly in the critical point. This is the one place in the document where conceptual
   size and consistency strength visibly disagree; the order used is consistency strength, as
   requested.
4. Every Reinhardt cardinal is a limit of measurable cardinals and of I3 cardinals below it.

**Where it sits.** Above I0, and below Berkeley. The comparison with Icarus is by convention.

**Caveat.** The consistency of Reinhardt cardinals with $\mathrm{ZF}$ is an open problem in the standard
literature. A 2026 preprint claiming an inconsistency was withdrawn after an error was identified, so
the open status stands.

## 16. Berkeley cardinals

*This entry is stated in $\mathrm{ZF}$; it is refuted by the Axiom of Choice.*

A cardinal $\delta$ is **Berkeley** iff every transitive set containing $\delta$ has nontrivial
self-embeddings with critical points arbitrarily close to $\delta$.

$$
\boxed{
\delta \text{ is Berkeley}
\;\Longleftrightarrow\;
\forall M\ \forall \eta<\delta \
\Bigl( M \text{ transitive} \wedge \delta\in M
\;\Rightarrow\;
\exists j : M\to M \ \text{nontrivial elementary with } \eta<\mathrm{crit}(j)<\delta \Bigr).
}
$$

In words: for every transitive $M$ with $\delta \in M$, and every $\eta<\delta$, there is a nontrivial
elementary $j : M \to M$ whose critical point lies strictly between $\eta$ and $\delta$. The critical
points are therefore cofinal in $\delta$, and $\delta$ itself is not required to be a critical point.

**Inconsistent with Choice.** Apply the definition to $M = V_{\delta+2}$: it gives a nontrivial
elementary $j : V_{\delta+2}\to V_{\delta+2}$, which Kunen's theorem forbids in $\mathrm{ZFC}$. So
Berkeley cardinals, like Reinhardt cardinals, live in $\mathrm{ZF}$ or $\mathrm{ZF}+\mathrm{DC}$.

Properties.

1. Berkeley implies Reinhardt-strength notions: taking $M = V_{\delta+1}$ gives an I1 embedding at
   $\delta$, and taking $M$ of the form $V_{\lambda+1}$ for suitable $\lambda$ gives I0 embeddings.
2. Berkeley is strictly above Reinhardt in consistency strength, since it requires the embedding
   property for *every* transitive set containing $\delta$ rather than for $V$ alone.
3. The definition is a scheme over transitive sets, so it is not a single embedding but a uniform
   supply of them; this is why it survives Kunen's theorem only in the choiceless setting.
4. In $\mathrm{ZF}$ the existence of a Berkeley cardinal is not known to be inconsistent, and this is
   the central open problem of the choiceless hierarchy.

**Where it sits.** Above Reinhardt, below Ord-Berkeley and club Berkeley.

## 17. Ord-Berkeley cardinals

*This entry is stated in $\mathrm{ZF}$; it is refuted by the Axiom of Choice.*

The definition used here is the one given in the choiceless large cardinal literature: $\kappa$ is
**Ord-Berkeley** iff it is $\lambda$-Berkeley for every $\lambda$.

$$
\boxed{
\kappa \text{ is Ord-Berkeley}
\;\Longleftrightarrow\;
\forall \lambda>\kappa \ \bigl( \kappa \text{ is } \lambda\text{-Berkeley} \bigr),
}
$$

where, for $\lambda>\kappa$, $\kappa$ is **$\lambda$-Berkeley** iff for every transitive $N$ with
$\kappa \subseteq N$ and $|N|<\lambda$, the set of critical points of nontrivial elementary
$j : N \to N$ is unbounded in $\kappa$. Unfolding the size restriction gives the equivalent form

$$
\boxed{
\kappa \text{ is Ord-Berkeley}
\;\Longleftrightarrow\;
\forall N\ \Bigl( N \text{ transitive} \wedge \kappa\subseteq N
\;\Rightarrow\;
\{\, \mathrm{crit}(j) : j : N\to N \ \text{nontrivial elementary} \,\} \text{ is unbounded in } \kappa \Bigr).
}
$$

So Ord-Berkeley is Berkeley with two changes: the hypothesis is $\kappa \subseteq N$ rather than
$\kappa \in N$, and the requirement on the critical points is *unboundedness* rather than the
"arbitrarily close to $\kappa$" formulation. The parameter $\lambda$ in $\lambda$-Berkeley is a size
bound, and "Ord-Berkeley" removes the bound by quantifying over all $\lambda$.

Properties.

1. The definition is a direct specialization of the general Berkeley scheme: Berkeley restricts the
   transitive sets to those containing $\delta$ as an element and asks for critical points above any
   given $\eta<\delta$; $\lambda$-Berkeley restricts their size and asks for unboundedly many critical
   points; Ord-Berkeley removes the size bound.
2. Ord-Berkeley is at least as strong as Berkeley on this reading, because the class of transitive
   $N$ with $\kappa\subseteq N$ contains the class with $\kappa\in N$.
3. The obvious models of the property are rank-initial segments: taking $N = V_{\kappa}$ exhibits an
   I3 embedding at $\kappa$, so Ord-Berkeley implies I3 (indeed much more) at $\kappa$.
4. Like Berkeley, Ord-Berkeley is inconsistent with $\mathrm{ZFC}$ for the same reason: the definition
   applied to $N = V_{\delta+2}$ gives an embedding forbidden by Kunen's theorem.

**Where it sits.** Between Berkeley and club Berkeley, by convention. No theorem of this document
separates Ord-Berkeley from Berkeley.

**Caveat (two readings of the name).** The name is used in two different ways in the literature and
this entry must not be read as settling which is meant.

1. *The unbounded reading*, used above: Ord-Berkeley $=$ $\lambda$-Berkeley for every $\lambda$. This is
   the reading of the definition quoted in the choiceless large cardinal literature, and it is the one
   this document adopts.
2. *The cumulative-hierarchy reading*, which the request for this entry described as "for every
   $\alpha$, a nontrivial $j : V_{\alpha}\to V_{\alpha}$ with critical point $\kappa$". In the
   literature that notion is called **rank-Berkeley** (Goldberg–Schlutzenberg), and its verified form is

$$
\boxed{
\lambda \text{ is rank-Berkeley}
\;\Longleftrightarrow\;
\forall \alpha<\lambda\ \forall \beta\ge\lambda\ \exists j : V_{\beta}\to V_{\beta} \
\bigl( j \text{ nontrivial elementary},\ \alpha<\mathrm{crit}(j)<\lambda \bigr).
}
$$

   Rank-Berkeley is explicitly introduced as a *weakening* of Berkeley, because it tests only
   rank-initial segments; it is the notion used to define exacting cardinals. If "Ord-Berkeley" in this
   project is meant in that sense, then the entry should be re-pointed at the box above and the position
   in the ladder moves below Berkeley rather than above it.

Because the two readings give different strengths and different positions, the entry is flagged in the
final section.

## 18. Limit club Berkeley cardinals

*This entry is stated in $\mathrm{ZF}$; it is refuted by the Axiom of Choice.*

**Status: the definition below is reconstructed, and it is flagged for verification.** The verified
ingredient is the parametrized notion of a club $\lambda$-Berkeley cardinal: for cardinals
$\kappa<\lambda$, $\kappa$ is **club $\lambda$-Berkeley** iff for every transitive set $N$ of size
$<\lambda$ with $\kappa \subseteq N$ there is a club $C \subseteq \kappa$ such that every
$\eta \in C$ is the critical point of an elementary embedding $N \to N$; $\kappa$ is
**$\nu$-club $\lambda$-Berkeley** if that club can be taken to be a $\nu$-club; and $\kappa$ is
$\lambda$-Berkeley if the set of critical points is merely unbounded in $\kappa$.

$$
\boxed{
\kappa \text{ is club Berkeley}
\;\Longleftrightarrow\;
\forall M\ \Bigl( M \text{ transitive} \wedge \kappa\subseteq M
\;\Rightarrow\;
\exists C\subseteq\kappa \ \text{a club}\ \forall \eta\in C\ \exists j : M\to M \
\bigl( j \text{ nontrivial elementary},\ \mathrm{crit}(j)=\eta \bigr) \Bigr).
}
$$

A cardinal $\delta$ is **limit club Berkeley** iff it is club Berkeley and is a limit of club Berkeley
cardinals below it.

$$
\boxed{
\delta \text{ is limit club Berkeley}
\;\Longleftrightarrow\;
\delta \text{ is club Berkeley}
\;\wedge\;
\{\, \gamma<\delta : \gamma \text{ is club Berkeley} \,\} \text{ is unbounded in } \delta .
}
$$

The intended content is a two-step strengthening of Berkeley: first replace unboundedly many critical
points by a *club* of them (club Berkeley), then require the cardinal to be a limit of cardinals with
that property (limit club Berkeley). The "club" step is the one the compactness characterizations of
the choiceless hierarchy attach to, which is why it is a natural rung.

Properties.

1. Club Berkeley implies Berkeley, since a club of critical points is in particular unbounded, and
   Ord-Berkeley is the unbounded version at every size, so club Berkeley implies Ord-Berkeley.
2. Limit club Berkeley implies club Berkeley at $\delta$ and gives a proper supply of club Berkeley
   cardinals below $\delta$; it is the strongest entry in this document.
3. Taking $M = V_{\delta+2}$ again gives a Kunen-forbidden embedding, so the notion is refuted by
   $\mathrm{ZFC}$ in the same way as Berkeley.
4. In $\mathrm{ZF}$ neither club Berkeley nor limit club Berkeley is known to be inconsistent.

**Where it sits.** The top of the ladder, above club Berkeley and hence above Ord-Berkeley and
Berkeley.

**Caveat.** Both displayed boxes are reconstructions: the parametrized club $\lambda$-Berkeley
definition is verified, the unparametrized "club Berkeley" form and the "limit of club Berkeley
cardinals" clause are not. See the final section.

## Consistency strength ladder

The table is the authority on order; the section numbering deviates from it at exactly one point,
because section 7 covers both the fixed-$\alpha$ levels (below Woodin) and the all-$\alpha$ case (above
Woodin). `Basis` records whether the comparison with the row above is a theorem, a convention of this
document, or unknown.

| # | Cardinal | Section | Basis |
|---|----------|---------|-------|
| 1 | Mahlo | 1 | base of the ladder |
| 2 | $\alpha$-Mahlo, fixed $\alpha\ge 1$ | 2 | theorem |
| 3 | hyper-Mahlo | 3 | theorem |
| 4 | weakly compact ($\Pi^{1}_{1}$-indescribable) | 4 | theorem |
| 5 | $\Pi^{1}_{n}$-indescribable, $n\ge 2$, and totally indescribable | 5 | theorem |
| 6 | measurable | 6 | theorem |
| 7 | $\alpha$-strong, fixed $\alpha\ge 1$ | 7 | theorem |
| 8 | Woodin | 8 | theorem |
| 9 | strong (all $\alpha$) | 7 | theorem |
| 10 | supercompact | 9 | theorem |
| 11 | I3 | 10 | theorem |
| 12 | I2 | 11 | theorem ($\mathrm{I2}\Rightarrow\mathrm{I3}$) |
| 13 | I1 | 12 | conventional (standard order of the rank-into-rank axioms) |
| 14 | I0 | 13 | theorem ($\mathrm{I0}\Rightarrow\mathrm{I1}$) |
| 15 | Icarus | 14 | conventional |
| 16 | Reinhardt | 15 | conventional |
| 17 | Berkeley | 16 | conventional |
| 18 | Ord-Berkeley | 17 | conventional, and unknown under the rank-Berkeley reading |
| 19 | limit club Berkeley | 18 | unknown (definition reconstructed) |

Rows 1–14 are linearly ordered by consistency strength in the standard literature. Rows 15–19 are the
choiceless end of the ladder; there the comparisons are the ones the subject reports, and this document
marks them honestly rather than deriving them. Two specific warnings:

1. **Berkeley versus Reinhardt.** Consistency strength orders Berkeley above Reinhardt; conceptual size
   orders Reinhardt above Berkeley, because a Reinhardt embedding has domain $V$ while a Berkeley
   cardinal only embeds transitive sets. The convention is to order by consistency strength and to say
   so, which is what rows 16 and 17 do.
2. **Ord-Berkeley versus Berkeley.** Under the unbounded reading adopted in section 17, Ord-Berkeley is
   at least as strong as Berkeley; under the cumulative-hierarchy reading (rank-Berkeley) it is a
   weakening and belongs below Berkeley. The table lists the first reading; flipping it would swap rows
   17 and 18 and move limit club Berkeley's basis from `unknown` to `conventional`.

## Sources

1. A. Kanamori, *The Higher Infinite*, 2nd edition, Springer 2003 — the standard reference for
   measurability through the rank-into-rank axioms (chapters 4, 5, 9, 26).
2. J. Bagaria, P. Koellner, W. H. Woodin, *Large cardinals beyond choice*, Bulletin of Symbolic Logic
   25 (2019) — Berkeley, club Berkeley, limit club Berkeley, and the choiceless hierarchy.
3. D. Blue, G. Sargsyan, *$\mathsf{AD}^{+}$ implies that $\omega_{1}$ is a club $\Theta$-Berkeley
   cardinal*, [arXiv:2402.01329](https://arxiv.org/abs/2402.01329) — the parametrized club
   $\lambda$-Berkeley definition quoted in section 18.
4. V. Dimonte, *I0 and rank-into-rank axioms*, [arXiv:1707.02613](https://arxiv.org/abs/1707.02613) —
   the I0 literature and the Icarus calibrations by sharps.
5. J. Osinski, *Compactness beyond choice and HOD*,
   [arXiv:2609.25291](https://arxiv.org/abs/2609.25291) — rank-Berkeley, Berkeley and club Berkeley in
   one place, with the compactness characterizations.
6. J. P. Aguilera, J. Bagaria, P. Lücke, *Large cardinals, structural reflection, and the HOD
   Conjecture*, [arXiv:2411.11568](https://arxiv.org/abs/2411.11568) — rank-Berkeley as a weakening of
   Berkeley, and exacting cardinals.

## Definitions to verify against a source

Three entries in this document are not on the same footing as the other fifteen. They are listed here
with the uncertainty stated precisely, so that a later pass can repair them without touching the rest.

1. **Icarus (section 14).** The definition given is $X\subseteq V_{\lambda+1}$ with a nontrivial
   elementary $j:L(X,V_{\lambda+1})\prec L(X,V_{\lambda+1})$ and $\mathrm{crit}(j)<\lambda$, and
   $\lambda$ is Icarus if some such $X$ exists. What is uncertain: (a) whether the standard definition
   quantifies "there is $X$" or "for every $X$"; (b) whether the model is $L(X,V_{\lambda+1})$ or the
   differently closed $L(V_{\lambda+1},X)$; (c) whether the critical point must satisfy
   $\mathrm{crit}(j)<\lambda$ or only $\mathrm{crit}(j)\le\lambda$; and (d) the exact relation to
   $(V_{\lambda+1})^{\sharp}$, which is quoted from secondary sources only. The placement between I0 and
   Reinhardt is a guess that follows from the parameter being a subset of $V_{\lambda+1}$, not from a
   theorem.
2. **Ord-Berkeley (section 17).** Two readings are in circulation and the entry adopts the first. What
   is uncertain: whether "Ord-Berkeley" in this project means (i) $\lambda$-Berkeley for every
   $\lambda$, as in the choiceless large cardinal literature, or (ii) the cumulative-hierarchy property
   "for all $\alpha<\lambda\le\beta$ there is $j:V_{\beta}\to V_{\beta}$ with
   $\alpha<\mathrm{crit}(j)<\lambda$", which in the literature is Goldberg–Schlutzenberg's
   *rank-Berkeley* and is a weakening of Berkeley rather than a strengthening. The two readings put the
   entry on opposite sides of Berkeley, so the ladder position is genuinely undecided until the term is
   pinned down. Also unverified: whether $\lambda$-Berkeley with $\kappa\subseteq N$ and with
   $\kappa\in N$ are equivalent, which decides whether Ord-Berkeley is strictly stronger than Berkeley.
3. **Limit club Berkeley (section 18).** The parametrized club $\lambda$-Berkeley definition is
   verified from the literature. What is uncertain: (a) the exact unparametrized form of club Berkeley —
   whether the quantifier is over all transitive $M$ with $\kappa\subseteq M$, or only over those of
   size $<\lambda$ for a fixed $\lambda$, and whether the club is required to be a $\nu$-club;
   (b) whether limit club Berkeley means "club Berkeley and a limit of club Berkeley cardinals", or
   "a limit of club Berkeley cardinals" with no separate club Berkeley requirement, or something
   defined through the $\nu$-club refinement; and (c) whether the notion is strict in consistency
   strength above club Berkeley. The "limit" clause is the part most likely to be wrong.

Two further items are recorded here although their boxes are believed correct, because they are
frequently stated differently in the literature.

4. **I2 (section 11).** The closure condition is $V_{\lambda}\subseteq M$ at
   $\lambda=\sup_{n}j^{n}(\kappa)$. Variants in circulation demand closure at $V_{j(\kappa)}$ (which is
   superstrongness, strictly weaker) or demand that $j$ be the ultrapower by a specific extender; the
   entry uses the standard formulation, but the variants are not interchangeable.
5. **$\alpha$-Mahlo (section 2).** The base case is $0$-Mahlo $=$ inaccessible, which is what makes
   $1$-Mahlo $=$ Mahlo. Sources that start at $0$-Mahlo $=$ Mahlo shift every index by one; the entry
   states its own convention and the ladder uses it.
