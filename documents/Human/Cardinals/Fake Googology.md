# Fake Googology

> **This file is fiction.** It is a literary exercise in the style of googology, written for fun.
> Nothing here defines a set, an ordinal or a cardinal; no statement below is a theorem, and no symbol
> in this file may be used in the real documents of this directory. The point is to write "ordinals"
> that are large by *wish*, and then to watch, with a straight face, what breaks.

## Rules of the joke

- The word "ordinal" stays in scare quotes: call the objects **joke-ordinals**.
- We pretend that proper classes have cardinalities, so that $|V|$ and $|\mathrm{Ord}|$ are comparable
  objects rather than nonsense.
- Membership is kept literally: if one joke-ordinal is "greater" than another, it contains it.
- A definition may be a wish ("there is a joke-ordinal such that ...") with no comprehension scheme
  behind it. That is exactly what makes the object fake.

## 1. The joke-ordinal of ordinal size: $\Omega$

**Wish.** There is a joke-ordinal $\Omega$ with $|\Omega| = |\mathrm{Ord}|$.

Consequences, played straight:

1. $\Omega$ contains every ordinal, because everything smaller than an object of size
   $|\mathrm{Ord}|$ has to fit inside it.
2. $\Omega$ is therefore the last ordinal of the ordinal line: nothing can follow it.
3. $\Omega$ has as many elements as there are ordinals, and its own initial segment below it has as
   many elements as well, so it is a fixed point of the size map at the very top of the line.

What breaks: this is **Burali-Forti**. The ordinals form a proper class, so there is no set $\Omega$
with $|\Omega| = |\mathrm{Ord}|$; the equation is wishful typing, not arithmetic.

## 2. The joke-ordinal of $V$-size: $\Omega_2$

**Wish.** There is a joke-ordinal $\Omega_2$ with $\Omega \in \Omega_2$ and $|\Omega_2| = |V|$.

Consequences, played straight:

1. $\Omega_2$ is strictly above $\Omega$, hence above every ordinal that $\Omega$ already contains.
2. $\Omega_2$ has as many elements as the whole universe of sets, so it is "the ordinal of
   everything".
3. It is not reachable from below by any set-sized iteration: no ordinal-indexed sequence of sets can
   have $\Omega_2$ as its limit.

**The interesting grain inside the joke.** With the Axiom of Choice one has $|V| = |\mathrm{Ord}|$,
because every set can be well-ordered and every well-order is an ordinal. So under Choice the wishes
of sections 1 and 2 describe the *same* size, and $\Omega_2$ cannot be strictly bigger than $\Omega$
unless Choice fails. The second wish is therefore as much a statement about the *failure* of Choice as
about size: only in a universe where some sets cannot be lined up against the ordinals does a second,
strictly larger level of the joke appear.

What breaks: **Cantor's paradox**. $V$ is not a set, so $|V|$ is not a cardinal, and again there is no
object to attach it to.

## 3. What the joke teaches

- Both "ordinals" above fail for the same reason: they are defined by a property of the whole class
  rather than by comprehension from below. Real large cardinals (see `Cardinals.md`) never do this.
  Each of them is a set, and its strength is measured by what it reflects, not by how big it claims to
  be.
- The two wishes differ by exactly the difference between $|\mathrm{Ord}|$ and $|V|$, which is the
  difference between well-orderability and its absence. That is a real question, and the joke
  accidentally points at it.
- A hierarchy that grows by "being bigger than everything so far" collapses at the first proper class
  it meets. A hierarchy that grows by *reflection* keeps going. That is why the real documents in this
  directory are full of elementary embeddings rather than of "the biggest ordinal".

## 4. If we kept going (teaser)

- $\Omega_3$ with $|\Omega_3| = |V \cup \{V\}|$.
- $\Omega_\Omega$, indexed by a joke-ordinal, to show that indexing does not save the construction.
- $\Omega_{\text{absolute}}$, which contains every joke-ordinal including itself, hence is a member of
  itself, hence is not a class, hence is nothing at all.

That last one is the standard way these games end.

## Disclaimer

None of this is mathematics. The objects do not exist, the definitions are wishes, and the
"theorems" are the classical paradoxes wearing costumes. Do not cite this file; cite Burali-Forti,
Cantor, and a real textbook.
