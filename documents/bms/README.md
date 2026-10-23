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

Notes for whoever writes the expander:

- The file is UTF-8 without a byte order mark; read it as UTF-8, not as the platform default.
- The formulas are the original `<math>` blocks, so the superscripts and subscripts survive, unlike
  the rendered text a page fetch returns.
- The wiki does not state a licence on this page; check the wiki's terms before redistributing the
  article outside this repository. The file is kept here as a private reference.
- The three worked examples are the natural acceptance tests for an expander, and the strength table
  gives the expected ordinal for the expressions in it.