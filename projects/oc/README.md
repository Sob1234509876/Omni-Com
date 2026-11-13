English | [简体中文](README_zh_CN.md)

# Omni Com (project group)

Modules of the `projects:oc` group.

## Modules

- `oc-googology` — small library of very large natural numbers: exact `BigInteger` values up to 4096 bits,
  hyperoperation (up arrow) notation beyond that, and transfinite hyperoperation levels up to `ε₀` through
  a pluggable ordinal notation. See [its README](oc-googology/README.md) and the Chinese
  [ordinal guide](oc-googology/help/ordinals.md). The RTS engine depends on it for item amounts,
  capacities, health and damage.
- `oc-googology-ordinal` — a register of special renderings for named ordinals: an entry maps an
  ordinal's canonical text (what its own `toString()` prints, e.g. `(0)(1,1,1)(2,2)`) to an aligned
  chain of ASCII and LaTeX names (`BMS(0)(1,1,1)(2,2)=p(psd. Pi(w))=SSO` and
  `\mathrm{BMS}(0)(1,1,1)(2,2)=\psi(\mathrm{psd.}\ \Pi_\omega)=\mathrm{SSO}`). The mechanism ships
  with its self check; the table of entries is deliberately empty and filled in separately.
- `oc-rts-engine` — engine for the Omni Com real-time strategy game. See
  [its README](oc-rts-engine/README.md) and the task oriented
  [usage guides](oc-rts-engine/help/README.md) (Chinese, API names in English).
