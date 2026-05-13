<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# Apache Groovy — issue-tracker configuration

## URL and project key

| Key | Value |
|---|---|
| `url` | `https://issues.apache.org/jira` |
| `project_key` | `GROOVY` |
| `tracker_type` | `jira` |
| `issue_url_template` | `https://issues.apache.org/jira/browse/<KEY>` |

The `<issue-tracker>` placeholder resolves to `url`; the
`<issue-tracker-project>` placeholder resolves to `project_key`.

## Authentication

| Key | Value |
|---|---|
| `anonymous_read` | `true` |
| `auth_method` | n/a (anonymous reads only; commenting requires manual posting via JIRA UI) |
| `auth_env_var` | n/a |

ASF JIRA permits anonymous read for the GROOVY project. The `issue-*`
skills are read-only by design — they never post comments or transition
state, so authentication is not needed for any phase of the skill flow.
A maintainer who wants to post the recommended comments does so manually
through the JIRA web UI.

## Default query templates

Default triage pool query (used by `issue-triage` when no explicit
selector is supplied):

```jql
project = GROOVY AND resolution = Unresolved AND
  status in (Open, "In Progress") AND
  created > -90d
ORDER BY created DESC
```

This surfaces issues filed in the last 90 days that are still
unresolved — the natural triage candidate set. Adjust the time
window per the project's triage cadence.

## Tracker-specific notes

- **Rate limits**: ASF JIRA does not publish a documented rate limit
  but is shared infrastructure. Cache aggressively; throttle bulk
  queries.
- **Anon-vs-auth differences**: not material for the issue-* family
  (read-only). Some fields (worklog, watchers) are auth-only but
  the skills don't read them.
- **Custom fields**: GROOVY uses standard JIRA fields. No custom
  field reads needed.
- **GitHub PRs**: code-change PRs live at `apache/groovy` on
  GitHub. Many JIRA issues link out to PRs; the `issue-*` skills
  detect linked PRs and factor them into classifications.

## Cross-references

- [`project.md`](project.md) — the manifest.
- [`reassess-pool-defaults.md`](reassess-pool-defaults.md) — named
  pools beyond the default-triage query above.
- [`runtime-invocation.md`](runtime-invocation.md) — how
  `issue-reproducer` runs the extracted code.
