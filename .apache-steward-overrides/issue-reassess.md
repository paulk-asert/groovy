<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

<!-- apache-steward agentic override
     Framework skill:    issue-reassess
     Pinned to snapshot: see ../.apache-steward.lock for the spike rev. -->

# Overrides for `issue-reassess`

## Why these overrides exist

Apache Groovy has run reassessment campaigns since 2026-05-13. The
default-pool choice for the first sweep produced high signal; these
overrides codify the preference order for subsequent sweeps.

## Overrides

### Override 1 — Default pool is `open-eol`

When no pool is specified, default to `open-eol` (the
`affectedVersion in (...EOL versions...)` query in
`<project-config>/reassess-pool-defaults.md`). The pool over-
represents silent fixes (the 3.0 split-packages refactor and the
4.0 series fixed many issues incidentally) and is the highest-
density first-sweep target for this project.

### Override 2 — Second pool is `reopened`

After `open-eol` clears, the next-highest-signal pool is
`reopened`. It over-represents wishlists the team has resisted —
these classify as `feature-request-disguised-as-bug` in the nature
analysis and warrant a `re-type as Improvement` recommendation.

### Override 3 — Always run cross-family probes

For Apache Groovy campaigns, default to enabling cross-family
probes on every candidate where the expression is family-typed
(don't pass `--no-probe`). The pilot found project-wide spec gaps
on several issues' probes, justifying the extra runtime.

The probe template path in the framework snapshot is
`tools/probe-templates/groovy/`. The skill picks the right
template per the expression's family (range/index, GPath,
operator-variants).

### Override 4 — Per-issue scratch root

Set the campaign scratch root to
`~/working/groovy-reassessment/<campaign-id>/`. The pilot from
2026-05-13 used `~/working/groovy-reassessment/pilot-2026-05-13/`;
the magpie re-validation campaign uses
`~/working/groovy-reassessment/pilot-2026-05-13-via-magpie/`.

This is the *campaign root*; individual issue subdirectories
(`GROOVY-<NNNN>/`) live underneath per
`<project-config>/reproducer-conventions.md`.
