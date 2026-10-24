# Inaccessible Cardinal Extension

> **Convention (GCH).** Every definition in this document is stated assuming the Generalized
> Continuum Hypothesis: for every infinite cardinal $\kappa$, one has $2^{\kappa} = \kappa^{+}$.
> Under GCH the several notions of *limit of powers* below all coincide, so "strongly inaccessible",
> "weakly inaccessible" and "inaccessible" name the same cardinals, and we simply say *inaccessible*.

## Strongly inaccessible cardinals

A cardinal $\kappa$ is **strongly inaccessible** iff all of the following hold.

1. $\kappa$ is uncountable: $\kappa > \omega$.
2. $\kappa$ is regular: $cf(\kappa) = \kappa$.
3. $\kappa$ is a strong limit: for every $\alpha < \kappa$, $2^{\alpha} < \kappa$.

$$
\boxed{
\kappa \text{ is strongly inaccessible}
\;\Longleftrightarrow\;
\kappa > \omega \;\wedge\; cf(\kappa) = \kappa \;\wedge\; \forall \alpha < \kappa \;\bigl(2^{\alpha} < \kappa\bigr).
}
$$

Equivalently, $\kappa$ is an uncountable regular cardinal that is a strong limit; equivalently, under
GCH, an uncountable regular limit cardinal. Since $\kappa$ is a strong limit of uncountable
cofinality, $\kappa$ is a fixed point of the $\beth$ function, and because it is regular it is not a
limit of fewer than $\kappa$ smaller cardinals.

### The class of inaccessible cardinals

The inaccessible cardinals are unbounded in the class $\mathrm{Ord}$ of all ordinals, but they do
**not** form a closed class. If $\lambda$ is a limit ordinal and every $\mathbb{I}_{\alpha}$ with
$\alpha < \lambda$ is inaccessible, then
$\sup_{\alpha < \lambda} \mathbb{I}_{\alpha}$ has cofinality at most $\lambda$, which is less than
the supremum itself, so that supremum is singular and is therefore not inaccessible. Consequently the
enumeration below is strictly increasing, and $\mathbb{I}_{\lambda}$ is the least inaccessible
cardinal that is strictly greater than every $\mathbb{I}_{\alpha}$ with $\alpha < \lambda$.

## Notation

For an ordinal $\alpha$ we write $\mathbb{I}_{\alpha}$ for the $\alpha$-th strongly inaccessible
cardinal, enumerated in increasing order and starting at index $0$.

$$
\boxed{
\mathbb{I}_{0} = \text{the least inaccessible cardinal},
\qquad
\mathbb{I}_{\alpha} < \mathbb{I}_{\beta}
\;\Longleftrightarrow\;
\alpha < \beta .
}
$$

Thus $\mathbb{I}_{0}$ is the least inaccessible cardinal, often written $I$ elsewhere, and
$\mathbb{I}_{\alpha}$ is the $(1+\alpha)$-th inaccessible cardinal when one counts from $1$.

**Convention.** All definitions that follow in this document are stated assuming GCH, as declared at
the top, and use the notation $\mathbb{I}_{\alpha}$ with the indexing fixed above.

## $\alpha$-inaccessible cardinals

Let $\kappa$ be a cardinal and let $\alpha$ be an ordinal. The property *$\kappa$ is
$\alpha$-inaccessible* is defined by transfinite recursion on $\alpha$.

### Base case: $\alpha = 0$

$$
\boxed{
\kappa \text{ is } 0\text{-inaccessible}
\;\Longleftrightarrow\;
\kappa \text{ is (strongly) inaccessible.}
}
$$

### Inductive step: $\alpha > 0$

$$
\boxed{
\kappa \text{ is } \alpha\text{-inaccessible}
\;\Longleftrightarrow\;
\kappa \text{ is inaccessible and, for every } \beta < \alpha,\;
\{\, \lambda < \kappa : \lambda \text{ is } \beta\text{-inaccessible} \,\} \text{ is unbounded in } \kappa .
}
$$

In words: for every $\beta < \alpha$, the $\beta$-inaccessible cardinals are cofinal below $\kappa$,
so $\kappa$ is a limit point of them; and this has to hold simultaneously for all smaller $\beta$.
Note that $\alpha$-inaccessibility is **not** the same as requiring the set to be *stationary*: an
unbounded set of singular cardinals below a regular $\kappa$ need not meet every club of $\kappa$.
The stationary version is strictly stronger and is not the notion used here.

**Convention.** $0$-inaccessibility means ordinary (strongly) inaccessible, and the recursion above
is the only convention used in this document.

### Strength: this hierarchy stays below Mahlo

The intended strength ceiling of this extension is **strictly below the Mahlo cardinals**, and the
choice of *unbounded* rather than *stationary* above is what keeps it there.

1. Let $M$ be the least Mahlo cardinal. $M$ is inaccessible and the regular cardinals are stationary
   in $M$; consequently $M$ is a limit of inaccessible cardinals, and by induction on $\alpha < M$ it
   is a limit of $\alpha$-inaccessible cardinals as well. So for every $\alpha < M$, the
   $\alpha$-inaccessible cardinals are unbounded below $M$, and every
   $\mathbb{I}(\alpha,\beta)$ with $\alpha, \beta < M$ is below $M$.
2. The stationary variant of the inductive step is **strictly stronger**: "every closed and
   unbounded subset of $\kappa$ contains a $\beta$-inaccessible cardinal" is essentially the Mahlo
   property one level up, and a single application of it already gives cardinals that are not
   obtained by the hierarchy above. That variant is therefore deliberately **excluded** from this
   document.
3. Consequently the extension built here is the **hyper-inaccessible** hierarchy: it is consistent
   with the non-existence of Mahlo cardinals, and every cardinal it produces is below every Mahlo
   cardinal. Mahlo cardinals belong to a later, strictly stronger stage.
4. If a definition below ever needs "stationary" to make an argument work, that is a signal that the
   argument has already left this extension and reached towards Mahlo; such a definition should not
   be added here.

## The inaccessible cardinal Veblen function

We write

$$
\boxed{
\mathbb{I}(\alpha,\beta)
\;=\; \text{the } (1+\beta)\text{-th } \alpha\text{-inaccessible cardinal,}
}
$$

counted in increasing order and starting at index $0$. The same letter carries both forms: the sequence $\mathbb{I}_{\alpha}$ is the unary case, and the binary form is the Veblen style function below. The function
$\mathbb{I} : \mathrm{Ord} \times \mathrm{Ord} \to \mathrm{Ord}$ is called the
**inaccessible cardinal Veblen function**.

Basic properties, immediate from the definition.

1. $\mathbb{I}(0,\beta) = \mathbb{I}_{\beta}$: $0$-inaccessible is inaccessible, and the
   two enumerations count from the same point.
2. For each fixed $\alpha$, the map $\beta \mapsto \mathbb{I}(\alpha,\beta)$ is strictly
   increasing and continuous, so it is a normal function.
3. $\mathbb{I}(1,0)$ is the least inaccessible cardinal that is a limit of inaccessible
   cardinals, and $\mathbb{I}(\alpha,0)$ is the least $\alpha$-inaccessible cardinal.
4. The shape of the definition is exactly that of the classical Veblen function $\varphi(\alpha,\beta)$
   with "inaccessible" in place of "fixed point of $\xi \mapsto \omega^{\xi}$", so the same
   limit-point and normal-function machinery applies with $\mathbb{I}$ as its base.

## Multi-variable inaccessible cardinals

The unary hierarchy extends to any finite tuple $(\alpha_1,\dots,\alpha_n)$. The recursion always
reads the tuple from the right: the last coordinate is a ladder index, the coordinate before it is a
diagonal step.

### The recursion

1. **Empty prefix, index $\beta$.** $\kappa$ is $(\beta)$-inaccessible iff $\kappa$ is
   $\beta$-inaccessible in the sense of the previous section. The unary case is therefore the base
   case of the whole construction.
2. **Index $0$ with a prefix.** $\kappa$ is $(\alpha_1,\dots,\alpha_n,0)$-inaccessible iff $\kappa$ is
   inaccessible and, for **every** $\gamma < \kappa$, the set
   $\{\, \lambda < \kappa : \lambda \text{ is } (\alpha_1,\dots,\alpha_{n-1},\gamma)\text{-inaccessible} \,\}$
   is unbounded in $\kappa$. This is the diagonal step: $\kappa$ closes the previous variable over all
   smaller indices, so it is a limit point of the entire previous ladder. This is the "inaccessible
   point of the map $\alpha \mapsto (\alpha)$" the construction is built on, and requiring $\kappa$ to
   be inaccessible makes it a regular point of that map.
3. **Index $\beta > 0$.** $\kappa$ is $(\alpha_1,\dots,\alpha_n,\beta)$-inaccessible iff $\kappa$ is
   inaccessible and for every $\gamma < \beta$ the same set is unbounded in $\kappa$: the ordinary
   inductive step of the unary case, applied to the coordinate $\beta$.

Reading the recursion from the right reproduces the intended ladder: $(\alpha)$ is the base ladder,
$(1,0)$ closes it, $(1,1)$ climbs the $(1,0)$ ladder, $(2,0)$ closes that one, $(1,0,0)$ closes the
$(\alpha,0)$ ladder, and $(1,0,1)$, $(1,1,0)$, $(2,0,0)$ repeat the pattern one level up, with
$1,0,0,\dots$ as the limit of the finite tuples.

### The multi-variable inaccessible cardinal Veblen function

$$
\boxed{
\mathbb{I}(\alpha_1,\dots,\alpha_n,\beta)
= \text{the } (1+\beta)\text{-th } (\alpha_1,\dots,\alpha_n)\text{-inaccessible cardinal.}
}
$$

The same letter $\mathbb{I}$ is used for every arity. The arity of the tuple decides which case above
is meant, and the unary case is the sequence $\mathbb{I}_{\alpha_1}$ of the earlier section. The map
is called the **multi-variable inaccessible cardinal Veblen function**.

### Basic properties

1. $\mathbb{I}(\alpha_1,\dots,\alpha_n,0)$ is the least $(\alpha_1,\dots,\alpha_n)$-inaccessible
   cardinal, and for a fixed prefix the map
   $\beta \mapsto \mathbb{I}(\alpha_1,\dots,\alpha_n,\beta)$ is strictly increasing and continuous,
   hence a normal function.
2. $\mathbb{I}(0,\beta) = \mathbb{I}_{\beta}$, matching the unary section.
3. $\mathbb{I}(1,0)$ is the least inaccessible cardinal that closes the unary ladder over all smaller
   indices, $\mathbb{I}(1,\beta)$ enumerates the limit points of that operation, and
   $\mathbb{I}(2,0)$ closes the $(1,\beta)$ ladder.
4. The limit of the finite tuple hierarchy, written $1,0,0,\dots$ above, is the least cardinal closed
   under every finite tuple. It is the analogue of the small Veblen ordinal, taken over inaccessible
   cardinals instead of over $\xi \mapsto \omega^{\xi}$.
5. **Strength.** The full multi-variable hierarchy still stays below the Mahlo cardinals. The precise
   reason is that Mahlo is exactly the property that every normal function on the regulars has a
   closed unbounded set of closure points below it: for a **fixed** tuple the class of its
   inaccessible closure points is club below any Mahlo cardinal, and the intersection of club classes
   is again club. The condition in the diagonal step quantifies over $\gamma < \kappa$ but is still a
   property of $\kappa$, so the same argument applies to every finite tuple, and the limit of the whole
   finite hierarchy is the least point closed under all of them, which is the least element of a club
   and therefore below every Mahlo cardinal. What would leave this extension is not the quantification
   over $\gamma < \kappa$, but the **stationary** variant, which is excluded here.

A bare closed-class condition would admit singular limits: if only closure under the lower ladder were required, the limit of an increasing sequence of such closure points would qualify, and that limit is singular. The definition therefore always requires $\kappa$ itself to be inaccessible, and it is exactly this requirement that keeps every member regular. Accordingly the hierarchy is built from limit points (closure points) of the lower ladders, not from fixed points of the underlying normal function.

## Ordinal-coefficient inaccessible cardinals

The finite tuple can be replaced by a finite list of *coefficient entries* $a \mathbin{@} b$: an
ordinal $a$ attached to a level $b$. Formally a list
$(a_1 \mathbin{@} b_1, \dots, a_n \mathbin{@} b_n)$ is admissible when $a_i > 0$ for every $i$ and
$b_1 > b_2 > \dots > b_n$, and comparison is lexicographic from the left: the entry with the larger
level $b$ is the larger one, and equal levels compare by the coefficient $a$. This is the same shape
as a Cantor normal form, with the level playing the role of the exponent and the coefficient the
role of the leading factor. A finite tuple $(\alpha_1,\dots,\alpha_n)$ is the special case where every
coefficient is $1$.

### The recursion

1. **Empty list.** $\kappa$ is $()$-inaccessible iff $\kappa$ is inaccessible.
2. **Successor step.** $\kappa$ is $(a_1 \mathbin{@} b_1, \dots, a_n \mathbin{@} b_n)$-inaccessible iff
   $\kappa$ is inaccessible and a limit point of the cardinals that are inaccessible for every
   strictly smaller admissible list. In the finite case this is the ordinary inductive step of the
   previous section; in general it is the successor step of a Veblen hierarchy indexed by ordinals.
3. **Limit step.** When the level of the first entry is a limit ordinal, or when the list itself is
   approached as a limit of admissible lists, the same closure over **all** strictly smaller lists is
   used, exactly as in the diagonal step of the tuple recursion.

So the recursion is the usual one for a Veblen hierarchy indexed by a well-order, and the tuples of
the previous section are the case of a finite index.

### The ordinal-coefficient inaccessible cardinal Veblen function

$$
\boxed{
\mathbb{I}(a_1 \mathbin{@} b_1, \dots, a_n \mathbin{@} b_n)
= \text{the least } (a_1 \mathbin{@} b_1, \dots, a_n \mathbin{@} b_n)\text{-inaccessible cardinal,}
}
$$

and with a trailing index $\beta$,

$$
\boxed{
\mathbb{I}(a_1 \mathbin{@} b_1, \dots, a_n \mathbin{@} b_n, \beta)
= \text{the } (1+\beta)\text{-th one,}
}
$$

so the trailing index behaves exactly as in the multi-variable section. This is called the
**ordinal-coefficient inaccessible cardinal Veblen function**.

### Basic properties

1. $\mathbb{I}(1 \mathbin{@} 0)$ is the least cardinal that is a simultaneous limit point of the whole
   finite tuple hierarchy, i.e. the inaccessible analogue of the **small Veblen ordinal**.
2. $\mathbb{I}(1 \mathbin{@} 1)$ is the least point closed under the $1 \mathbin{@} 0$ construction, the
   analogue of the **large Veblen ordinal**; more generally increasing $a$ repeats the construction
   and increasing $b$ nests it.
3. For a fixed list the enumeration in $\beta$ is a normal function, and
   $\mathbb{I}(0 \mathbin{@} b, \dots)$ reduces to the previous section because a zero coefficient is
   dropped.
4. **Strength.** The whole coefficient hierarchy, including the large Veblen analogue, is still below
   the Mahlo cardinals by the same club argument: a Mahlo cardinal is closed under every normal
   function on the regulars, so it is closed under this index as well, and the least closure point is
   the least element of a club below it. The stationary variant remains the only excluded step.

## Self-nesting levels (the last extension)

The final step lets the **level** $b$ in an entry $a \mathbin{@} b$ be any index term of the notation
built so far, instead of only an ordinal: a tuple, another coefficient entry, or a term of the form
$a \mathbin{@} B$ with $B$ again an index term. The index notation stops being a set of tuples and
becomes a **term algebra closed under its own level construction**, and the hierarchy is extended to
that algebra by the same recursion.

### The rule

1. **Level from the tuple world.** $B$ may be a tuple $(\beta_1,\dots,\beta_m)$; then
   $a \mathbin{@} B$ means the coefficient $a$ attached to the tuple as a level, exactly as a tuple was
   a level for the multi-variable section.
2. **Closure over a family.** $\mathbb{I}(1 \mathbin{@} B)$ is the least inaccessible cardinal that is
   a simultaneous limit point of the whole family $\{\mathbb{I}(1 \mathbin{@} b) : b < B\}$, where
   $b < B$ runs over the index terms below $B$ in the term order.
3. **Nesting.** $B$ may itself have the form $1 \mathbin{@} B'$, so the level construction is applied
   to its own output. The term order is the lexicographic order on the term algebra, which is a
   well-order because each level is built from normal functions on the regulars.
4. **Repeating and nesting.** $a \mathbin{@} B$ with $a > 1$ repeats the construction, and increasing
   $B$ nests it, exactly as in the coefficient section.

### The listed terms

$$
\begin{aligned}
&\mathbb{I}(1 \mathbin{@} (1,0)) &&\text{closure of the family } \mathbb{I}(1 \mathbin{@} \alpha)\\
&\mathbb{I}(1 \mathbin{@} (1,0), 1) &&\text{the next level above that closure}\\
&\mathbb{I}(1 \mathbin{@} (1,0), 1, 0) &&\text{the diagonal one level above}\\
&\mathbb{I}(1 \mathbin{@} (1,0), 1 \mathbin{@} a) &&\text{a coefficient climb on top of it}\\
&\mathbb{I}(2 \mathbin{@} (1,0)) &&\text{repetition of the closure}\\
&\mathbb{I}(1 \mathbin{@} (1,1)) &&\text{closure taken at the } (1,1) \text{ level}\\
&\mathbb{I}(1 \mathbin{@} (2,0)) &&\text{closure taken at the } (2,0) \text{ level}\\
&\mathbb{I}(1 \mathbin{@} (1,0,0)) &&\text{closure at the tuple limit}\\
&\mathbb{I}(1 \mathbin{@} (1,0,0,0)) &&\text{the same one tuple arity higher}\\
&\mathbb{I}(1 \mathbin{@} (1 \mathbin{@} a)) &&\text{closure over the } 1 \mathbin{@} \text{ family itself}\\
&\mathbb{I}(1 \mathbin{@} (1 \mathbin{@} (1,0))) &&\text{a limit point of that nesting}
\end{aligned}
$$

The limit of the whole construction, written informally as
$1 \mathbin{@} (1 \mathbin{@} (1 \mathbin{@} \dots))$, is the least cardinal closed under the entire
self-nesting term algebra, and it is where this document stops.

### Strength

The self-nesting construction does not leave the extension either. The index is a term algebra
ordered as a well-order, every level is a normal function on the regulars, and a Mahlo cardinal is
closed under every such function, so all the closure points above are club below any Mahlo cardinal
and the least of them is below that cardinal. The rule of the earlier section still applies unchanged:
**the stationary step is the only one that would leave this extension**, and it remains excluded.
Mahlo cardinals are the next, strictly stronger stage and are not part of this document.

### What this extension is not

It is not a collapse: no term of this notation denotes an ordinal below the least Mahlo cardinal in a
way that can be compared with the countable ordinals used elsewhere in the project. It is a hierarchy
of **cardinal existence axioms** expressed in a notation, and the notation is only as strong as the
closure arguments above. In particular, every term here is still proved to lie below every Mahlo
cardinal by the same combinatorial argument, and none of them is proved to exist without assuming the
existence of the cardinals below it.
