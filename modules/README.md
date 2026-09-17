# Kotlin module map

The `modules/` tree contains reusable Kotlin boundaries. A directory becomes a
production module only when it owns code, tests, and an enforceable dependency
direction; directories are not created as decorative placeholders.

## Current extraction

- [`domain/`](domain/) is a real multiplatform module for deterministic
  learning rules, conclusion-chain evaluation, bounded feedback, practice
  reducers, and entitlement identifiers.

## Planned boundaries

The target architecture reserves `core`, `application`, `data`, `features`,
and `design-system` for later extractions. They must be split incrementally
from the current working mobile module after an import graph and focused test
gate prove that the move is safe.
