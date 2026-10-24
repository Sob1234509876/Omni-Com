# Reference material

Third party references kept here so that a later implementation (for example a Bashicu Matrix
System expander in `oc-googology`) does not have to re-fetch them.

## bms/ - Bashicu Matrix System

- `bms-zh.raw.wiki` - the raw MediaWiki source of the Chinese Googology Wiki article on BMS,
  fetched on 2026-10-24 from
  https://wiki.googology.top/index.php/BMS?action=raw
  It contains the definition, the ordinal notation formulation (parent, ancestor, lowermost non-zero,
  bad root, good part, bad part, ascension offset vector, the expansion rule and the Bm matrices), three
  worked examples including the case where an ancestor does not pass through the bad root, the history
  from BM1 to BM4 with the termination proofs, and the strength table that maps matrix expressions to
  ordinals from 0 up to psi of a projection.

- `non-recursive-bms-zh.raw.wiki` - the raw MediaWiki source of the Chinese Googology Wiki article on
  the non-recursive variant (非递归BMS), fetched on 2026-10-25 from
  https://wiki.googology.top/index.php/非递归BMS?action=raw
  It defines the variant: the first column is a run of ones, trailing zeros are free, a last column
  `(1)` is the successor, otherwise the BMS bad-root expansion is used when a bad root exists, and
  otherwise the article's non-recursive expansion appends an infinite column of ones.

- `non-recursive-bms-zh.page.html` - the saved rendered HTML page of the same article, fetched on
  2026-10-25 from https://wiki.googology.top/index.php/非递归BMS (the page chrome is kept).

Notes for whoever writes the expander:

- The files are UTF-8 without a byte order mark; read them as UTF-8, not as the platform default.
- The formulas are the original `<math>` blocks, so the superscripts and subscripts survive, unlike
  the rendered text a page fetch returns.
- The wiki does not state a licence on these pages; check the wiki's terms before redistributing the
  articles outside this repository. The files are kept here as private references. The attribution and
  licence status of both articles is recorded in `NOTICE.md`.
- The three worked examples are the natural acceptance tests for an expander, and the strength table
  gives the expected ordinal for the expressions in it.
- The non-recursive article's two non-recursive expansions are infinite columns
  (`(1,1,1)(2,2)(3,3,1,1,1,...)` and `(1,1)(2,2,1)(3)(4,1,1,1,...)`), so a finite matrix type cannot
  hold them as values; they are descriptions of the expansion, not new finite expressions.
