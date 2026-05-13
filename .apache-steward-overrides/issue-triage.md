<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

<!-- apache-steward agentic override
     Framework skill:    issue-triage
     Pinned to snapshot: see ../.apache-steward.lock for the spike rev. -->

# Overrides for `issue-triage`

## Why these overrides exist

The Apache Groovy issue backlog has accumulated long-lived issues
where the reporter framed a wished-for behaviour as a bug. The May
2026 pilot found this pattern in 2 of 4 sampled issues (GROOVY-2994
and GROOVY-3905). The default classification logic doesn't
emphasise this — these overrides bias the triage toward correctly
identifying these.

## Overrides

### Override 1 — Aggressive `feature-request-disguised-as-bug` recognition

When an open issue describes a behaviour that the project's
documentation explicitly defines but the reporter expects to
behave differently, classify as `still-fails-same` × nature
`feature-request-disguised-as-bug` — not `INVALID` and not
`still-fails-same` × `bug-as-advertised`.

Common indicators:

- The reporter uses `==`, `+`, `?.`, or other operators in a way
  that contradicts the documented semantics, then expects the
  documented semantics to change.
- The reporter cites a workaround that requires a specific syntax
  variant (e.g., `GString.EMPTY +` to force conversion) but
  argues the workaround shouldn't be necessary.
- The issue's value comes from documenting the user's mental
  model gap, not from a documentation deficit.

For these, the proposal phrasing should explicitly say:
*"This is a feature request, not a bug — the current behaviour
matches the documented semantics. Re-typing to Improvement is
appropriate; the team can then decide whether to consider the
spec change."*

### Override 2 — Workaround documentation

When the issue or its comments mention a workaround, include the
workaround verbatim in the proposal's `notes` field. Many
long-lived Groovy issues have undocumented workarounds (e.g.,
`GString.EMPTY +` for non-commutative GString concatenation) that
would close the issue if documented. The triage proposal should
recommend documentation alongside the disposition.

### Override 3 — Cross-family scope

When the issue's expression is a member of a type or operator
family (range/index, GPath, safe-navigation variants), recommend
running the cross-family probe before final classification. The
probe lives in `issue-reproducer` (per its `probe-templates.md`
companion); for triage purposes, the recommendation is to invoke
`issue-reproducer` with the probe enabled before reaching a final
disposition.
