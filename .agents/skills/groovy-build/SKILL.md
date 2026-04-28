<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->
---
name: groovy-build
description: Guidance for changes to the Apache Groovy Gradle build — convention plugins under build-logic/, root and subproject build files, dependency verification, ASM/ANTLR repackaging, OSGi metadata, binary-compatibility checks, and the ASF release pipeline. Use when editing build.gradle, build-logic/, gradle/*.gradle, gradle.properties, or gradle/verification-metadata.xml.
license: Apache-2.0
compatibility: claude, codex, copilot, cursor, gemini, aider
metadata:
  audience: contributors to apache/groovy
  scope: gradle-build-and-release
---

# Groovy build

Use this skill when the change touches the Gradle build itself —
convention plugins, root or subproject build files, dependency
verification, distribution assembly, or release pipeline. For changes
to the compiler/runtime *implementation*, use
[`groovy-internals`](../groovy-internals/SKILL.md). For test-task
selection during development, see [`groovy-tests`](../groovy-tests/SKILL.md).

## When to use this skill

**Use it for:**

- Convention plugin changes under `build-logic/src/main/groovy/org.apache.groovy-*.gradle`.
- Root `build.gradle` or `settings.gradle` edits.
- Subproject `build.gradle` edits where the change is build-shape, not source.
- `gradle.properties` (versions, target bytecode, build flags).
- Adding, removing, or upgrading a runtime or test dependency, including the matching `gradle/verification-metadata.xml` regeneration.
- ASM / ANTLR / picocli repackaging rules in `groovyLibrary { repackagedDependencies = ... }`.
- OSGi manifest, JaCoCo aggregation, license-report, dep-updates, Develocity / build-scans, and signing/publishing wiring.
- `subprojects/binary-compatibility/` configuration and accepted-API-changes files.
- Gradle wrapper version bumps (`gradle/wrapper/gradle-wrapper.properties`).

**Don't use it for:**

- Compiler, parser, type-checker, AST, or runtime source — use [`groovy-internals`](../groovy-internals/SKILL.md).
- Adding a regression test or executable AsciiDoc example — use [`groovy-tests`](../groovy-tests/SKILL.md).
- AsciiDoc prose under `src/spec/doc/` that has no build implication.

## Read first

Before editing, read the relevant section of
[`ARCHITECTURE.md`](../../../ARCHITECTURE.md) — the "Build
infrastructure" rows of the directory map, and the "What's generated
and what isn't" section — and the "Building" section of
[`README.adoc`](../../../README.adoc). The convention plugins under
`build-logic/src/main/groovy/` are the primary source of truth for
how a subproject is shaped; their names (`org.apache.groovy-base`,
`-common`, `-core`, `-library`, `-published-library`,
`-aggregating-project`, ...) describe their scope.

## Top failure modes to avoid

These are the recurring mistakes — both human and AI — on build work.
Each one is cheap to avoid and expensive to land:

1. **Editing a subproject `build.gradle` to do something that should live in a convention plugin.** If two or more subprojects need the same behaviour, it belongs in `build-logic/src/main/groovy/org.apache.groovy-*.gradle`, not duplicated. Conversely, *don't* push a one-off into a shared convention plugin — the ripple across every consumer is hard to review.
2. **Adding a dependency without regenerating `gradle/verification-metadata.xml`.** This repository uses Gradle dependency verification; an unverified artifact fails the build. Regenerate with `./gradlew --write-verification-metadata sha256,pgp help` and inspect the diff before committing.
3. **Hard-pinning versions in module build files.** Versions live in `gradle.properties` and the `Versions` / `SharedConfiguration` types under `build-logic/src/main/groovy/org/apache/groovy/`. Ad-hoc `'group:artifact:1.2.3'` literals in subproject builds drift and break the BOM.
4. **Touching `repackagedDependencies` without an installed-build smoke test.** ASM / ANTLR / picocli are jarjar-relocated into `groovyjarjar*` packages. A wrong rule produces a jar that compiles fine but blows up at runtime. After any repackaging change, run `./gradlew :groovy-binary:installGroovy` and exercise `groovy` / `groovyc` against a non-trivial script.
5. **Skipping the binary-compatibility check.** `subprojects/binary-compatibility/` runs as part of the build. Don't suppress it to get green CI — either justify the change in the accepted-changes file or revert the API breakage. See [`COMPATIBILITY.md`](../../../COMPATIBILITY.md).
6. **Adding a new runtime dependency without `NOTICE` / `LICENSE` review.** ASF rules apply to dependencies as well as code. New runtime dependencies need discussion on dev@ and may need `NOTICE` / `LICENSE` updates; a plain Gradle change is not enough.
7. **Breaking the configuration cache.** Custom build logic must not access mutable `Project` state at execution time. Prefer providers (`providers.gradleProperty`, `providers.environmentVariable`, `Provider` chains) and `tasks.register` / `configureEach` over eager realisation.
8. **Editing generated ANTLR sources to "fix" a parser problem.** `build/generated/sources/antlr4/...` is regenerated on every build; the grammar lives in `src/antlr/*.g4`. This is a build-side reminder of the same trap covered in `groovy-internals`.
9. **Wrapper version bumps without checking Develocity / plugin compatibility.** A wrapper bump can disable build scans or break a Gradle plugin pinned in the root `plugins {}` block. Cross-check the Develocity compatibility matrix linked in `build.gradle` before bumping.
10. **Reformatting a build script outside the change.** Same review-culture rule as code: drive-by reformatting hides intent and is rejected.

## Procedure

1. **Classify the change scope.** Convention plugin (cross-cutting, affects every subproject that applies it), single subproject (local), or root/`settings.gradle` (cross-cutting). State which one and stay there.
2. **Read the convention plugin first.** Skim the `org.apache.groovy-*` plugin(s) the affected subproject applies, plus `Services.groovy` / `SharedConfiguration` / `Versions` under `build-logic/src/main/groovy/org/apache/groovy/`. Don't guess at the DSL — read the actual extension types.
3. **Make the smallest correct change.** Prefer convention plugin updates over per-module duplication; prefer per-module overrides over convention-plugin churn for genuinely local concerns.
4. **For dependency changes, regenerate verification metadata.** After editing dependencies:

   ```
   ./gradlew --write-verification-metadata sha256,pgp help
   ```

   Inspect the diff in `gradle/verification-metadata.xml`; commit only the entries your change actually introduced.

5. **Run targeted, then aggregate, then full.** Don't go straight to `./gradlew build`:

   ```
   ./gradlew :<subproject>:<task>           # targeted (e.g. :groovy-json:jar)
   ./gradlew :<subproject>:check            # subproject aggregate
   ./gradlew build                          # full build incl. binary-compatibility
   ```

6. **For repackaging, distribution, or launcher-affecting changes, install and exercise.** Code-level tests don't catch a broken `groovy` / `groovyc` launcher:

   ```
   ./gradlew :groovy-binary:installGroovy
   ```

   See "Running your local build" in [`CONTRIBUTING.md`](../../../CONTRIBUTING.md) for the launcher path and the `GROOVY_HOME` caveat.

7. **For API-affecting changes, run binary compatibility explicitly.** `./gradlew build` already does this, but a focused run is faster feedback:

   ```
   ./gradlew :binary-compatibility:check
   ```

8. **For config-cache or caching investigations, capture a build scan.**

   ```
   ./gradlew --scan <task>
   ```

## ASF provenance reminders

These apply to every build change as much as to source changes:

- New runtime dependencies need dev@ discussion and may require `NOTICE` / `LICENSE` updates.
- ASF license header on every new build script (`*.gradle`) and convention plugin file. Copy from a sibling.
- Don't introduce build-time dependencies whose licensing is incompatible with Apache-2.0; check the [ASF 3rd Party Licensing Policy](https://www.apache.org/legal/resolved.html).
- If AI tooling assisted on the change, declare it in the commit trailer per [`AGENTS.md`](../../../AGENTS.md) (default form: `Assisted-by:`).

## Validation checklist

Before declaring the change ready:

- [ ] Change is in the right scope: convention plugin for cross-cutting behaviour, subproject build file for local concerns, root for project-wide settings.
- [ ] No hard-coded versions in subproject build files; versions flow through `gradle.properties` and the shared `Versions` type.
- [ ] Dependency changes are reflected in `gradle/verification-metadata.xml`; only the affected entries are touched.
- [ ] No edits to files under `build/generated/`.
- [ ] No formatting changes outside the lines that needed to change.
- [ ] If repackaging or distribution changed: `:groovy-binary:installGroovy` produced a working install, exercised against a non-trivial script.
- [ ] `./gradlew :<subproject>:check` passes locally.
- [ ] `./gradlew build` passes — including `:binary-compatibility:check` — or any API breakage is justified in the accepted-changes file.
- [ ] New runtime dependencies have had dev@ discussion and any required `NOTICE` / `LICENSE` updates are included in the same change.
- [ ] Commit message references `GROOVY-NNNNN` where applicable; AI provenance trailer added if AI tooling assisted.

## References

- [`ARCHITECTURE.md`](../../../ARCHITECTURE.md) — directory map (build-logic, buildSrc, generated trees) and the "what's generated" section.
- [`COMPATIBILITY.md`](../../../COMPATIBILITY.md) — stability tiers, what counts as a breaking change, the binary-compatibility check.
- [`CONTRIBUTING.md`](../../../CONTRIBUTING.md) — build, test, and submission process; "Running your local build".
- [`README.adoc`](../../../README.adoc) — canonical "Building" instructions.
- [`AGENTS.md`](../../../AGENTS.md) — overall AI-contributor guidance and ASF provenance rules.
- `build-logic/src/main/groovy/org.apache.groovy-*.gradle` — convention plugins (authoritative source of truth for subproject shape).
- `build-logic/src/main/groovy/org/apache/groovy/` — `SharedConfiguration`, `Versions`, and supporting types.
- `gradle/verification-metadata.xml`, `gradle/verification-keyring.keys` — dependency verification.
- `subprojects/binary-compatibility/` — binary compatibility check and accepted-changes files.
- `gradle/wrapper/gradle-wrapper.properties` — Gradle version pin.
- Gradle docs: best practices (<https://docs.gradle.org/current/userguide/best_practices.html>), configuration cache (<https://docs.gradle.org/current/userguide/configuration_cache.html>), dependency verification (<https://docs.gradle.org/current/userguide/dependency_verification.html>).
- `.agents/skills/groovy-internals/SKILL.md` — sister skill for compiler/runtime changes.
- `.agents/skills/groovy-tests/SKILL.md` — sister skill for test work.
