<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# Apache Groovy — project manifest

This is the **project configuration** for Apache Groovy under the
apache-steward framework. The `issue-*` skill family reads this
manifest to resolve project-specific identity, repositories,
mailing lists, and references to the other files in this directory.

## Identity

| Key | Value |
|---|---|
| `project_name` | `Apache Groovy` |
| `vendor` | `Apache Software Foundation` |
| `short_name` | `Groovy` |
| `product_family_url` | `https://groovy-lang.org/` |

## Repositories

| Key | Value | Purpose |
|---|---|---|
| `upstream_repo` | `apache/groovy` | Public codebase |
| `upstream_repo_url` | `https://github.com/apache/groovy` | |
| `upstream_default_branch` | `master` | Default PR target |
| `upstream_contributing_docs_url` | `https://github.com/apache/groovy/blob/master/CONTRIBUTING.md` | |
| `upstream_agents_md_url` | `https://github.com/apache/groovy/blob/master/AGENTS.md` | |

## Mailing lists

| Key | Value |
|---|---|
| `users_list` | `users@groovy.apache.org` |
| `dev_list` | `dev@groovy.apache.org` |
| `commits_list` | `commits@groovy.apache.org` |
| `notifications_list` | `notifications@groovy.apache.org` |
| `security_list` | `security@groovy.apache.org` |

## Tools enabled

| Capability | Tool | Adapter directory | Notes |
|---|---|---|---|
| Issue tracker | JIRA (anonymous read) | `tools/jira/` | See `issue-tracker-config.md` |
| Source control | GitHub | `tools/github/` | `apache/groovy` |
| Release voting | ASF dev list | — | via `dev_list` |

## Pointers to sibling files

- [`issue-tracker-config.md`](issue-tracker-config.md) — JIRA URL,
  project key, default queries.
- [`runtime-invocation.md`](runtime-invocation.md) — how to build
  + run Groovy code for reproducers.
- [`reassess-pool-defaults.md`](reassess-pool-defaults.md) — JQL
  for the project's reassess pools.
- [`reproducer-conventions.md`](reproducer-conventions.md) —
  evidence-package directory layout.
