<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# Apache Groovy — reassessment pool defaults

Named JQL pools for `issue-reassess` sweep campaigns.

## Pool: `open-eol`

Open issues filed against an end-of-life major version. Surfaces:
- Long-fixed-but-never-closed issues (silent fixes since the 3.0
  split-packages refactor and the 4.0 series).
- Real bugs that fell through the cracks at EOL.

```jql
project = GROOVY AND resolution = Unresolved AND status = Open AND
  affectedVersion in (
    "1.0", "1.5.6", "1.6.0", "1.6.5", "1.7.0", "1.8.0",
    "1.8.6", "2.0", "2.0.6", "2.1.0", "2.2.0-beta-1", "2.3.0",
    "2.4.0", "2.5.0", "2.5.7", "2.5.10"
  )
ORDER BY created ASC
```

## Pool: `reopened`

Issues that were closed and later reopened. Surfaces:
- Persistent wishlists the team has resisted (often classified
  `feature-request-disguised-as-bug`).
- True regressions where a fix didn't stick.

```jql
project = GROOVY AND status = Reopened
ORDER BY updated ASC
```

## Pool: `stale-unresolved`

Open issues with no activity in the last 2 years. Useful for
periodic hygiene sweeps.

```jql
project = GROOVY AND statusCategory != Done AND
  updated < -730d
ORDER BY updated ASC
```

## Pool: `no-component`

Open issues lacking a `Component/s` assignment. Triage-then-reassess
candidates — assigning a component often clarifies the disposition
in one step.

```jql
project = GROOVY AND statusCategory != Done AND
  component is EMPTY
ORDER BY created DESC
```

## Pool-selection guidance for Apache Groovy

- **First sweep against the new framework**: `open-eol` first — the
  Groovy 3.0 split-packages refactor and the 4.0 series moved a lot
  of code; silent-fix density is high.
- **Wishlist hygiene**: `reopened` — long-lived feature requests
  that have churned through close/reopen cycles typically classify
  `feature-request-disguised-as-bug` and warrant re-typing to
  Improvement.
- **Periodic hygiene**: rotate `stale-unresolved` and `no-component`
  each quarter.

## Cross-references

- [`issue-tracker-config.md`](issue-tracker-config.md) — the
  default-triage pool query (distinct from these reassess pools).
- [`reproducer-conventions.md`](reproducer-conventions.md) —
  evidence layout for each candidate.
