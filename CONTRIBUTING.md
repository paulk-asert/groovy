<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Contributing

We welcome all contributors. This file covers code contributions to the
Groovy repository. For ways to contribute that don't involve changing the
code — helping on the mailing lists, reporting issues, writing blog posts,
or contributing to the reference documentation on the website — see the
[project contribute page](https://groovy.apache.org/).

## Building and testing

JDK 17 or later is required. The canonical build instructions live in
[`README.adoc`](README.adoc). The short form:

```
./gradlew clean dist                                # full build
./gradlew test                                      # run tests
./gradlew :<module>:test --tests <TestClassName>    # run a single test
```

Use the Gradle wrapper (`./gradlew` / `gradlew.bat`) rather than a
system `gradle` — the wrapper pins the version the build expects.
Most modern IDEs open the Gradle project directly.

Run `./gradlew test` locally before sending a pull request. All tests
should be green.

### Running your local build

To exercise a build with your changes applied — running scripts,
trying the REPL, or smoke-testing behaviour the test suite doesn't
cover — produce a local installation:

```
./gradlew :groovy-binary:installGroovy
```

The installation lands under
`subprojects/groovy-binary/build/install/`. Its `bin/` directory
contains the `groovy`, `groovysh`, `groovyc`, and `groovyConsole`
launchers, so you can invoke

```
subprojects/groovy-binary/build/install/bin/groovy <script>
```

to run a script against the build you just produced.

On Unix-like systems, if you have SDKMAN (or any other tool that
sets `GROOVY_HOME` to a fixed installation), `unset GROOVY_HOME`
before running the launchers — otherwise they pick up that
environment variable instead of using the local build, and your
changes appear not to take effect.

## Tests

For an overall map of the test layout, see
[`ARCHITECTURE.md`](ARCHITECTURE.md). This section covers the bits a
contributor most often needs to know.

### Targeted runs

Reach for the narrowest run that reproduces what you're working on
before falling back to the full suite:

```
./gradlew :test --tests <FullyQualifiedClassName>             # one class, core
./gradlew :test --tests <FQN>.<methodName>                    # one method
./gradlew :<subproject>:test --tests <FQN>                    # one class, subproject
./gradlew :<subproject>:test                                  # one whole module
./gradlew --rerun-tasks :test --tests <FQN>                   # bypass the up-to-date cache
```

The full `./gradlew test` is appropriate as a final check before a PR
but is the wrong feedback loop for development.

### Test framework

New tests use **JUnit 5**: `org.junit.jupiter.api.Test` with
`org.junit.jupiter.api.Assertions.*`. Older tests in the tree use a
mix of JUnit 3 (`extends GroovyTestCase`) and JUnit 4 — match the
surrounding file when adding a method to an existing test class, but
write new test classes in JUnit 5. Spock is bundled and available,
but the core repo's own tests are not generally Spock-based; reach
for it only when you have a specific reason.

Test method names in new classes drop the `test` prefix that older
JUnit required — JUnit 5 picks up methods by the `@Test` annotation,
not by name. So `void octalLiteral()` is preferred over
`void testOctalLiteral()`. When adding a method to an existing test
class that uses the older prefixed style, matching the surrounding
file is the better fit.

Static helpers worth knowing:

- `groovy.test.GroovyAssert.shouldFail(...)` — asserts a closure throws.
- `gls.CompilableTestSupport` — base class used by spec tests when a
  test needs to assert a snippet compiles.

### Regression tests for JIRA fixes

Every bug fix that has a JIRA needs a test that fails on `master`
before the fix and passes after. There are two shapes:

**Standalone class, when the bug doesn't fit naturally with existing
tests:**

- Class name: `Groovy<NNNN>` for newer tests
  (e.g. `src/test/groovy/bugs/Groovy11955.groovy`). Older tests in
  the tree end in `Bug` or `Test`; new tests use the unsuffixed form.
- Follow-on tests on the same JIRA: append `pt2`, `pt3`
  (e.g. `Groovy10122pt2.groovy`).
- Location: `src/test/groovy/bugs/` for general bugs;
  `src/test/groovy/<package-mirror>/` when the bug is scoped to a
  specific area (the existing `org/codehaus/groovy/tools/stubgenerator/`
  directory shows the pattern).
- Subproject bugs go under that subproject's `src/test/`.

**Method on an existing class, when the regression fits with similar
tests already there:**

- Add a `@Test` method on the appropriate existing class.
- Place a `// GROOVY-<NNNN>` comment on the line immediately above
  the method so a search for the JIRA still finds it.
- The surrounding file's naming style applies — if the existing class
  uses `testFoo` style, follow that for the new method; otherwise
  use the unprefixed style.

In both cases, the test should fail on `master` before the fix is
applied — that's the proof it actually reproduces the bug.

To find precedent for a similar past fix:

```
git log --grep='GROOVY-12345'                                 # commits referencing the JIRA
git log --grep='GROOVY-' -- src/test/groovy/bugs/             # all bug-fix commits in core regression tests
```

### Executable AsciiDoc examples

Examples in the user-facing documentation under `src/spec/doc/` and
`subprojects/<module>/src/spec/doc/` are not pasted snippets — they
are `include::`'d from real test files under a matching `src/spec/test/`
directory, so every example compiles and runs as part of the build.

The pattern in three pieces:

1. **AsciiDoc include** in `src/spec/doc/<topic>.adoc`:

   ```asciidoc
   include::../test/<TopicTest>.groovy[tags=octal_literal_example,indent=0]
   ```

2. **Tagged region** in `src/spec/test/<TopicTest>.groovy`:

   ```groovy
   @Test
   void octalLiteral() {
       // tag::octal_literal_example[]
       int xInt = 077
       assert xInt == 63
       // end::octal_literal_example[]
   }
   ```

   (Many existing spec tests still use the older `testFoo` method-name
   style; match the surrounding file when adding to one of those.)

3. **Tag name** matches between the two. The test class extends
   `CompilableTestSupport` (or any JUnit 5 test class) and runs as
   part of the normal test task.

A change to a documented example normally touches *both* files in
the same PR. A new tagged region should be referenced from at least
one `include::` — orphaned tagged regions silently rot.

### Tests-preview

[`subprojects/tests-preview/`](subprojects/tests-preview/) is for
tests that depend on a JDK preview feature. Anything that needs
`--enable-preview` to compile or run goes there, not in core
`src/test/`.

### For agents working on tests

The [`.agents/skills/groovy-tests/SKILL.md`](.agents/skills/groovy-tests/SKILL.md)
skill captures the recurring failure modes when adding or modifying
tests in this repo, and the procedure for landing a regression test
or a documented example cleanly.

## Documentation

Documentation is a first-class deliverable, not an afterthought. When
contributing code, please treat documentation as part of the change:

- **Docs live in the code repository.** AsciiDoc sources are under
  `src/spec/doc/` for cross-cutting material and
  `subprojects/<module>/src/spec/doc/` for module-specific material.
  Each large module should have at least one AsciiDoc file covering
  what it offers.
- **Examples are executable.** Code snippets in the AsciiDoc are
  `include::`'d from real Groovy files under a matching `src/spec/test/`
  directory, so every example compiles and runs as part of the build.
  When you add an example, make it executable the same way — see any
  existing `.adoc` file under `subprojects/*/src/spec/doc/` for the
  pattern.
- **Documentation changes ship with the code.** If a pull request adds,
  changes, or removes user-visible behaviour, the relevant AsciiDoc and
  test examples should change in the same pull request. Reviewers will
  ask.
- **Groovydoc is part of the public API.** Public classes and methods
  need accurate Groovydoc/Javadoc. Match the style of existing classes
  in the module you're editing.

Cross-version reference documentation, the GDK, and the website itself
live in the separate [`groovy-website`](https://github.com/apache/groovy-website)
repository; see the [project contribute page](https://groovy.apache.org/)
for how to contribute there.

## Submitting a pull request

1. Fork <https://github.com/apache/groovy> and create a feature branch.
2. Reference the JIRA issue in commits, for example
   `GROOVY-12345: short description`.
3. Keep commits focused. A bug fix, a refactor, and a formatting pass
   are three separate commits (or pull requests), not one.
4. Run `./gradlew test` locally and confirm it passes.
5. Open a pull request against `master`.

GitHub's [fork a repo](https://docs.github.com/en/get-started/quickstart/fork-a-repo)
and [creating a pull request](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request)
guides cover the generic git mechanics.

## Using AI tooling

Contributors using AI coding assistants (Claude Code, Codex, Cursor, Copilot,
Gemini, Aider, and similar) should read [AGENTS.md](AGENTS.md) for
project-specific guidance, and in particular follow the ASF's
[Generative Tooling guidance](https://www.apache.org/legal/generative-tooling.html).
If AI tooling assisted on a change, consider adding an
`Assisted-by: <tool name and version>` trailer to the commit message;
`AGENTS.md` covers when each of `Assisted-by:`, `Co-authored-by:`, and
`Generated-by:` is appropriate. The contributor remains responsible for
the licensing, correctness, and style of everything they submit.
