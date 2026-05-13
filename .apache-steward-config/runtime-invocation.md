<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# Apache Groovy — runtime invocation

How `issue-reproducer` invokes the Groovy runtime on extracted code
when reassessing a reported bug against current `master`.

## Build prerequisite

The reproducer runs against a **locally-built Groovy distribution**
from the working tree, not the operator's system Groovy. This
ensures the run exercises the actual `master`-state code under
investigation.

Build (from the `apache/groovy` working tree root):

```bash
./gradlew :groovy-binary:installDist
```

The built `groovy` binary lands at:

```text
subprojects/groovy-binary/build/install/bin/groovy
```

If the build is stale relative to the current rev (`git log`
between the build's manifest rev and `HEAD` is non-empty), the
build prerequisite re-runs before reproducer execution.

## Run a single file

| Setting | Value |
|---|---|
| Command | `subprojects/groovy-binary/build/install/bin/groovy <file>` |
| Working directory | the reproducer's scratch directory (per `reproducer-conventions.md`) |
| Timeout | 60s default |

The local `groovy` binary picks up its `JAVA_HOME` from the
operator's environment. For verdicts where JDK matters (silent-fix
verdicts on issues filed against older JDKs), retry on the
originally-affected JDK via Gradle toolchains.

## Capture conventions

| Stream | Convention |
|---|---|
| stdout | captured separately to `<scratch>/stdout.log` |
| stderr | captured separately to `<scratch>/stderr.log` |
| both, concatenated | written to `<scratch>/run.log` with the command + rev + JDK header |
| exit code | 0 = success; non-zero = failure |
| timeout | enforced via `timeout 60s …`; `timeout` classification on hit |

## Network and dependency handling

Groovy's `@Grab` annotation resolves dependencies from Maven Central
at runtime. For `@Grab`-using reproducers:

- The skill must check exit code AND stderr for `ResolveException`
  before classifying. A swallowed Grape resolution failure looks
  like a clean run but the body never executed.
- For campaign sweeps, isolate the Grape cache via
  `-Dgrape.root=<scratch>/grape` so the operator's everyday cache
  stays clean. See `runtime-recipes.md` in the issue-reproducer
  skill.

| Key | Value |
|---|---|
| `resolves_dependencies_at_runtime` | `true` (via `@Grab`) |
| `cache_isolation_flag` | `-Dgrape.root=<scratch>/grape` |

## Cross-references

- [`reproducer-conventions.md`](reproducer-conventions.md) — scratch
  directory layout the runtime writes into.
- [`issue-tracker-config.md`](issue-tracker-config.md) — the issue
  metadata the reproducer reads.
