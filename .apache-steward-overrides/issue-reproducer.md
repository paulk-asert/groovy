<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

<!-- apache-steward agentic override
     Framework skill:    issue-reproducer
     Pinned to snapshot: see ../.apache-steward.lock for the spike rev. -->

# Overrides for `issue-reproducer`

## Why these overrides exist

Apache Groovy has accumulated specific reproducer-handling lessons
during the May 2026 pilot campaign (4 issues spanning shapes A, F,
A/multi-case, and E-precise). These overrides codify the
Groovy-specific patterns that surfaced — to avoid re-learning them
on every campaign.

## Overrides

### Override 1 — API-evolution adaptation for Groovy 3.0 split-packages

When extracting a reproducer from an issue filed before Groovy 3.0
(approx. 2019), check for imports referencing classes that moved
during the JPMS-driven split-packages refactor. The most common
case is `XmlParser` and `XmlSlurper`:

- `import groovy.util.XmlParser` (pre-3.0) → `import groovy.xml.XmlParser` (3.0+)
- `import groovy.util.XmlSlurper` (pre-3.0) → `import groovy.xml.XmlSlurper` (3.0+)
- `import groovy.util.Node` (pre-3.0) → `import groovy.xml.Node` (3.0+)

This is **mechanical adaptation**, not fabrication — the move is
documented in the Groovy 3.0 release notes. Cite the URL in
`verdict.json.notes`:

> Mechanical import adaptation: `groovy.util.XmlParser` → `groovy.xml.XmlParser` per Groovy 3.0 split-packages refactor (release notes: https://groovy-lang.org/releasenotes/groovy-3.0.html#Groovy3.0releasenotes-NewFeatures).

Other class moves that may surface (less common):
- `groovy.util.XmlNodePrinter` → `groovy.xml.XmlNodePrinter`
- `groovy.util.DOMBuilder` → `groovy.xml.DOMBuilder`

If the reproducer's behaviour changes after the import fix (not
just compilation), that's *not* mechanical — escalate to
`needs-info` or `still-fails-different`.

### Override 2 — Cross-type probes for range/index expressions

When the reproducer's central expression is a range-index on an
aggregate (`x[a..b]`, `x[a..<b]`, `x[-a..-b]`), default to running
the cross-type probe across the family:

- `List` (the reporter's typical input)
- `Object[]`
- `int[]` (and `long[]`, `double[]` where the value space matters)
- `String` (range-index resolves to substring)

Use the probe template at `tools/probe-templates/groovy/cross-type.groovy.template`
in the framework snapshot. The probe consistently surfaces project-
wide spec gaps even when the reporter's specific case classifies as
`fixed-on-master` — see the GROOVY-3974 evidence package from the
May 2026 pilot for an example where `int[]` `[0..-2]` returns
`[0, 0]` (a surprising non-empty result).

### Override 3 — Anchored regex for output verification

When verifying reproducer output against an expected substring, use
**anchored regex** instead of `String.contains()`. Groovy's XML
attribute output (`xmlns:xs="..."`) collides with substring matches
like `xs="`:

```groovy
// Bad — matches xmlns:xsi="..." as well as xmlns:xs="...":
if (output.contains('xs="')) { passed = true }

// Good — anchored match:
if (output =~ /\bxmlns:xs="/) { passed = true }
```

This trap silently produced a false `fixed-on-master` verdict
during the GROOVY-3905 pilot work before being caught. Apply the
"verify identifiers" discipline to the verification logic, not
just the code under test.

### Override 4 — Local Groovy build is the runtime

Per `<project-config>/runtime-invocation.md`, the reproducer runs
against the locally-built `subprojects/groovy-binary/build/install/bin/groovy`,
not the operator's system Groovy. Skip the system-Groovy fallback
entirely — verdicts produced against system Groovy do not reflect
`master`-state behaviour.

If the local build is stale (the build's rev differs from current
`HEAD`), rebuild before reproducer execution. The `--no-build`
flag is for the rare case where the operator has just run
`./gradlew :groovy-binary:installDist` and wants to skip a
redundant rebuild check.
