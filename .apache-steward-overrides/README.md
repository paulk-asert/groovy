<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# apache-steward overrides — Apache Groovy

Agent-readable instructions that override specific steps or behaviours
of apache-steward framework skills, scoped to the Apache Groovy
adopter repo. Each override file is named after the framework skill
it modifies (e.g. `issue-reproducer.md` overrides the
`issue-reproducer` skill).

The framework skills consult this directory at run-time before
executing default behaviour. See
[`docs/setup/agentic-overrides.md`](https://github.com/apache/airflow-steward/blob/main/docs/setup/agentic-overrides.md)
in the framework for the full contract.

**Hard rule**: never modify the snapshot under
`.apache-steward/`. Local mods go here. Framework changes go via
PR to `apache/airflow-steward`.

## Active overrides

- [`issue-reproducer.md`](issue-reproducer.md) — Groovy-specific
  reproduction adaptations (3.0 split-packages, regex pitfalls).
- [`issue-triage.md`](issue-triage.md) — Groovy-specific triage
  emphases (nature taxonomy bias).
- [`issue-reassess.md`](issue-reassess.md) — Groovy-specific
  reassess pool preferences.
