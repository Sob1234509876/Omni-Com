[English](README.md) | [简体中文](README_zh_CN.md)

# Omni Com (project group)

Modules of the `projects:oc` group.

> **Rewrite in progress (2026-11-20).** Everything except the two googology modules was deleted on
> this date by owner decision (`oc-rts-engine`, `oc-game-server`, `oc-game-server-classic`,
> `oc-game-server-debug`, `oc-game-server-hello`, and the `web-client/` pnpm project) — the whole
> stack is being rewritten from scratch. The previous implementation lives in git history at commit
> `636e2e4` (`git show 636e2e4:<path>` or `git checkout 636e2e4 -- <path>` brings any of it back).

## Modules

- `oc-googology` — small library of very large natural numbers: exact `BigInteger` values up to 4096 bits,
  hyperoperation (up arrow) notation beyond that, and transfinite hyperoperation levels up to `ε₀` through
  a pluggable ordinal notation. See [its README](oc-googology/README.md) and the Chinese
  [ordinal guide](oc-googology/help/ordinals.md).
- `oc-googology-ordinal` — a register of special renderings for named ordinals: an entry maps an
  ordinal's canonical text (what its own `toString()` prints, e.g. `(0)(1,1,1)(2,2)`) to an aligned
  chain of ASCII and LaTeX names (`BMS(0)(1,1,1)(2,2)=p(psd. Pi(w))=SSO` and
  `\mathrm{BMS}(0)(1,1,1)(2,2)=\psi(\mathrm{psd.}\ \Pi_\omega)=\mathrm{SSO}`). The mechanism ships
  with its self check; the table of entries is deliberately empty and filled in separately.
