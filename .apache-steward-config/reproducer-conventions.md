<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# Apache Groovy — reproducer evidence-package layout

Directory layout used by `issue-reproducer` when writing per-issue
evidence packages.

## Campaign directory layout

```text
~/work/groovy-reassess/<campaign-id>/GROOVY-<NNNN>/
├── description.md           (frozen copy of the JIRA description)
├── issue.json               (frozen JSON snapshot from JIRA REST)
├── original.groovy          (verbatim reporter's code)
├── reproducer.groovy        (adapted runnable form)
├── run.log                  (captured stdout + stderr + headers)
├── stdout.log               (separate stdout capture)
├── stderr.log               (separate stderr capture)
├── attachments/             (optional — when the issue had attachments)
└── verdict.json             (the structured verdict)
```

For the existing pilot work the campaign root is
`~/working/groovy-reassessment/pilot-2026-05-13/` (with
`GROOVY-2994/`, `GROOVY-3905/`, etc. underneath). The
re-validation campaign for the framework adoption uses
`~/working/groovy-reassessment/pilot-2026-05-13-via-magpie/`.

## Optional probe files

When a cross-family probe ran alongside the reproducer:

```text
├── cross-type-probe.groovy
├── cross-type-probe.log
├── safe-nav-variants-probe.groovy        (or another operator-variant probe)
└── safe-nav-variants-probe.log
```

A separate `cross-type-probe-findings.md` or
`safe-nav-variants-findings.md` is added when the probe surfaced
project-wide signal worth recording outside `verdict.json`.

## Frozen-copy discipline

`description.md` and `issue.json` are deliberately frozen at
extraction time. JIRA state may change between extraction and
re-verification — frozen copies keep the verdict auditable against
the exact input the agent reviewed.

## Cross-references

- [`runtime-invocation.md`](runtime-invocation.md) — how the
  reproducer is executed.
- [`reassess-pool-defaults.md`](reassess-pool-defaults.md) — named
  pools that surface candidates for evidence packages.
- [`issue-tracker-config.md`](issue-tracker-config.md) — JIRA URL
  and project key.
