# Booster CLI integration tests (`java/cli-integration-tests`)

The Java port of the CLI integration suite of Booster
(`packages/framework-integration-tests/integration/provider-unaware/cli`, the npm script `integration/cli`), plus
end-to-end checks the TypeScript suite does not have. Every test runs the **real launcher**
`java/cli/bin/boost` (which runs the CLI jar built by the reactor) in a sandbox project, and the projects are built
by the CLI with Maven against the framework artifacts of **this reactor**, installed into an isolated repository.

- Module: `java/cli-integration-tests` (artifact `com.boostercloud:booster-cli-integration-tests`), package
  `com.boostercloud.cli.integration`. It has test code only and is not published.
- The tests are failsafe `*IT` classes that run only in the Maven profile `e2e`. A plain `mvn verify` compiles them
  and runs nothing (surefire is skipped in this module).

## Running

```bash
cd java
export JAVA_HOME=<a JDK 27>                      # used by Maven, by bin/boost and for the applications
mvn -B -Pe2e -pl cli-integration-tests -am verify  # builds and tests the upstream modules, then runs the suite

# Only the suite, or one class (skips the unit tests of the upstream modules):
mvn -B -Pe2e -pl cli-integration-tests -am verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false
mvn -B -Pe2e -pl cli-integration-tests -am verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=CliStartIT

# Keep the sandbox projects (target/booster-it/sandboxes) to inspect them:
mvn ... -Dbooster.it.keepSandboxes=true
```

Requirements: JDK 27, Maven 3.9 or newer (the suite relies on `MAVEN_ARGS`), `git` on the `PATH`, and a free
range of local TCP ports. Network access is only needed for third-party artifacts that are not already in the local
repository of the Maven running the build (`~/.m2/repository`).

Every CLI invocation (arguments, directory, exit code, duration, both output streams) is appended to
`target/booster-it/logs/cli.log`; it is the first place to look when a test fails.

### Durations

On an Apple M-series laptop with a warm `~/.m2`: 75 to 85 s for the 82 tests, including the first fill of the
isolated repository from `~/.m2`, plus the build of the upstream modules — with their unit tests, about 4.5 minutes
for the whole `-am verify`. The slowest classes are `CliStartIT` and `CliDeployIT` (12 to 14 s each: two
builds and two application starts, or three application launches); each generator class takes about 3 s, most of it
one `boost build`.

## How the framework reaches the generated projects

The TypeScript suite rewrites the `@boostercloud/*` dependencies of its sandboxes to the `.tgz` files packed from the
monorepo (`overrideWithBoosterLocalDependencies`). Here:

1. In `pre-integration-test`, the profile `e2e` runs `maven-install-plugin:install-file` for the parent POM and the
   eight framework modules a project needs (`booster-metadata`, `-framework-types`, `-framework-common-helpers`,
   `-framework-core`, `-framework-provider-local`, `-provider-local-infrastructure`, `-provider-azure`,
   `-provider-azure-infrastructure`), from the jars the reactor has just built, into
   `target/booster-repo` (`localRepositoryPath`). Nothing is installed into `~/.m2`, which other builds share.
2. `IsolatedMavenRepository` writes `target/booster-it/maven/settings.xml`: its `localRepository` is
   `target/booster-repo`, and an active profile declares the local repository of the running build
   (`${settings.localRepository}`) as a `file://` remote repository for dependencies and plugins (read-only,
   checksums ignored), so third-party artifacts are copied from there; Maven Central stays the fallback.
3. Every CLI invocation gets `MAVEN_ARGS=-s <that settings.xml>`. The CLI passes its environment to Maven (and to
   the Maven Wrapper), so `new:project` (`mvn dependency:resolve`), `build`, `clean`, `start`, `deploy`... all
   resolve `com.boostercloud:*:4.1.0` from the isolated repository. No change to the CLI was needed.

## The environment of every CLI invocation (`Boost`)

The environment of the test JVM, without `BOOSTER_CLI_HOOK`, `BOOSTER_INTERNAL_LOCAL_PORT`, `BOOSTER_CLI_JAR`,
`BOOST_JAVA_OPTS`, `FORCE_COLOR`, `MAVEN_ARGS` and the `JAVA_TOOL_OPTIONS` family, plus:

| Variable | Value | Why |
| --- | --- | --- |
| `JAVA_HOME` | the JDK running the tests | `bin/boost`, Maven and the applications run on JDK 27 |
| `PATH` | the Maven and JDK running the build first | the CLI runs `mvn` from the `PATH` |
| `BOOSTER_ENV` | `test` | as the npm script `integration/cli` sets it |
| `MAVEN_ARGS` | `-s target/booster-it/maven/settings.xml` | the isolated repository |
| `GIT_CONFIG_GLOBAL`, `GIT_CONFIG_NOSYSTEM` | `target/booster-it/git/gitconfig`, `1` | git gets an identity without touching the machine's configuration (TypeScript runs `git config --global user.name ...`) |
| `LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT` | a free port | the local provider opens its websocket server whenever an application loads it (also for `deploy` and injected commands); the default 65529 may be taken by another build |

The standard input is closed at once (the CLI then answers every prompt with its default) unless the test gives
prompt answers. Applications are started with `--port <free port>`; nothing uses the fixed ports 3000/65529. Every
process is tracked with the descendants it starts (Maven, the application JVM) and killed after the test class, or by
a shutdown hook of the test JVM as a last resort.

## Coverage

| TypeScript (`integration/provider-unaware/cli`) | Java | Tests |
| --- | --- | --- |
| `cli.build.integration.ts` | `CliBuildIT` | build of the test app (class files and `target/lib`); "compile fallback" with a Maven Wrapper; Java: compilation error (exit 1, compiler output in `errors.log`), outside a project |
| `cli.clean.integration.ts` | `CliCleanIT` | `build` then `clean` removes `target` |
| `cli.command.integration.ts` | `CliCommandIT` | `new:command` without and with fields, missing name; Java: existing resource is not overwritten, the completed commands compile |
| `cli.entity.integration.ts` | `CliEntityIT` | without fields, with fields, with a reducer (after `new:event`), missing name; Java: compile |
| `cli.event-handler.integration.ts` | `CliEventHandlerIT` | `-e CartItemChanged`, missing name and event, missing name, missing event; Java: compile |
| `cli.event.integration.ts` | `CliEventIT` | output, without fields (after deleting the first file), with fields, missing name; Java: compile |
| `cli.readmodel.integration.ts` | `CliReadModelIT` | without fields (replacing the test app's read model), `items:Array<Item>`, with `--projects Cart:id`, missing name; Java: compile |
| `cli.scheduled-command.integration.ts` | `CliScheduledCommandIT` | `new:scheduled-command`, missing name; Java: compile |
| `cli.type.integration.ts` | `CliTypeIT` | without and with fields, missing name; Java: compile |
| `cli.project.integration.ts` | `CliProjectIT` | short flags, long flags, `--default` (generated files, installation skipped, git with its initial commit, compiles, lint), git repo, prompts, custom provider through the prompts, flags and prompts, missing name, invalid provider; Java: unknown provider package, npm provider name, AWS provider, existing folder, invalid project name |
| — (`new:query` is not tested in TypeScript) | `CliQueryIT` | `new:query` with fields, missing name, compile |
| — | `CliStartIT` | `new:project` with dependency installation and git; generators; `boost start --verbose -e local --port <free>`; mutation and read-model query with the application tester; SIGTERM (exit 143, application stopped); restart keeps the read model and the entity state |
| — (`deploy`/`nuke` of `cli-helper.ts`) | `CliDeployIT` | `deploy`/`synth`/`nuke --force` on `local` fail with the TypeScript message and exit code 1; unconfigured environment; no environment (exit 0, as TypeScript) |
| — | `CliHookIT` | injected commands (`BOOSTER_CLI_HOOK`): not compiled yet, `test` prints `Hello, world`, arguments forwarded without `-e`, environment from `BOOSTER_ENV`, unknown command, help, outside a project |
| — | `HelpersIT` | the helpers' own rules (output pattern, source edits, fixtures, settings, ports) |

The file contents are compared byte for byte with the fixtures (`src/test/resources/fixtures`, the Java counterpart
of Appendix A of the specification): `commands/`, `common/`, `entities/`, `eventhandlers/`, `events/`, `queries/`,
`readmodels/`, `scheduledcommands/` and the new-project files `cart-demo/{pom.xml, pom-default.xml, Main.java,
Config.java, gitignore, README.md}` with the TypeScript placeholders (`project_name_placeholder`,
`description_placeholder`, ...) plus `base_package_placeholder` and `booster_version_placeholder`.

The sandboxes start from `src/test/resources/test-app`, a small cart application (command `ChangeCartItem`, event
`CartItemChanged`, entity `Cart`, read model `CartReadModel`, type `CartItem`, role `Admin`, a `local` environment and
the injected commands `test` and `echo`), the counterpart of the TypeScript test app that `createSandboxProject`
copies.

## Helpers (`com.boostercloud.cli.integration.helper`)

| TypeScript | Java |
| --- | --- |
| `execa.command(`${cliPath} ...`, { cwd })`, `child_process.exec`, `runCommandAsync` | `Boost.command`, `Boost.run`, `Boost.spawn` → `CliResult`, `Boost.RunningBoost` |
| `handlePrompt` (`cli.project.integration.ts`) | `Boost.runWithPrompts` + `PromptAnswer` |
| `file-helper.ts` (`loadFixture`, `readFileContent`, `writeFileContent`, `removeFiles`, `createFolder`, `removeFolders`, `fileExists`, `dirContents`, `sandboxPathFor`) | `FileHelper` |
| `createSandboxProject` / `removeSandboxProject` (`cli/src/common/sandbox.ts`) | `Sandbox` |
| `overrideWithBoosterLocalDependencies` (`deps-helper.ts`) | `IsolatedMavenRepository` + the profile `e2e` |
| `new RegExp([...steps].join('(.\|\n)*'))` | `Expectations` |
| `sleep.ts` (`waitForIt`) | `com.boostercloud.application.tester.Sleep` (application-tester) |
| `getProviderTestHelper()` → `LocalTestHelper.build(...)`, `new ApplicationTester(...)` | `LocalApplication.tester(...)` |
| — | `JavaSources` (placeholder edits and imports), `Ports`, `Processes`, `CliTestEnvironment` |

## Differences from TypeScript

- **Java projects and fixtures.** The fixtures are Java files named after the class, in packages without hyphens
  (`readmodels/CartReadModel.java` for `read-models/cart-read-model.ts`); the new-project fixtures are a `pom.xml`
  (two variants, because empty values omit `<url>`, `<developers>` and `<scm>`), `Main.java`, `Config.java`,
  `.gitignore` and `README.md`, compared exactly. The `package.json` assertion (dependency keys only, versions
  `^BOOSTER_VERSION`) becomes the exact `pom.xml` comparison, which includes `<booster.version>` = the CLI version.
  The `.eslint*`, `.prettierrc.yaml`, `.mocharc.yml` and `tsconfig*.json` fixtures have no counterpart.
- **Placeholders.** The completed placeholders are Java (`authorize = All.class` plus its import,
  `UUID.of("some-id")`, `postId`, `new PostWithReducer(event.postId(), ...)`, `new CartWithProjectionReadModel(...)`
  inside the generated `ProjectionResult.of(...)`). Where
  TypeScript prepends `import { Item } from '../common/item'` (to a file that then does not compile), the Java test
  adds the import and, before compiling, creates `Item` with `boost new:type`. Each generator class ends with a
  Java-only test that builds the completed sandbox against the framework (TypeScript never compiles them).
- **Providers.** AWS is not ported: `PROVIDER` is `com.boostercloud.framework.provider.azure`, which is also the first
  choice of the provider prompt, as AWS is in TypeScript. The flag tests also pass the new `-g/--groupId` flag; the
  prompt tests answer the new group-id question with the default (an extra `PromptAnswer`), and the custom provider is
  chosen as `3) Other` and then typed (inquirer: arrow down, then the package). `invalid-provider` is rejected by the
  Java CLI before anything is created (TypeScript writes it to `package.json` and fails when npm installs it); what the
  TypeScript test pins (an unknown provider is not checked while generating) is tested with the well-formed package
  `com.example.invalid.provider`.
- **`--default` project.** "installs dependencies" (empty in TypeScript) checks that `--skipInstall` skipped the
  step; the real installation is covered by `CliStartIT`. "compiles" runs `boost build` (TypeScript: `npm install`
  and `npm run build`); "passes linter" runs `javac -Xlint:all -Werror` on the generated sources (TypeScript:
  `npm run lint:check`), after "compiles" because it needs the dependencies in `target/lib`.
- **Compile fallback.** The Java CLI has no `compile`-script lookup; its counterpart is the project's Maven Wrapper,
  which the CLI runs instead of `mvn`. The sandbox wrapper writes `eureka` like the TypeScript script.
- **Build outputs.** `dist/index.js`, `dist/roles.d.ts`... become `target/classes/.../Main.class`, `roles/Admin.class`,
  `config/Config.class` and `target/lib/booster-framework-*.jar` (no declaration files in Java).
- **Arguments.** Commands are passed to the process as an argument list: TypeScript's shell quoting
  (`'items:Array<Item>'`, `"The Agile Monkeys"`) is not needed. `Boost.command` splits on spaces like
  `execa.command`.
- **Output pattern.** The steps are matched as literals with `[\s\S]*` in between instead of `(.|\n)*`, which overflows
  the stack of Java's regex engine on long outputs (and also accepts `\r`).
- **Validation errors** keep TypeScript's implicit contract (message on stderr, exit code 0) and the tests check the
  exit code explicitly.
- **Git identity** comes from a private `GIT_CONFIG_GLOBAL` instead of changing the machine's global git configuration.
- **Not ported:** the root hook `setEnv()` of `cli/setup.ts` (it sets `BOOSTER_APP_SUFFIX`, which no CLI test uses);
  `storePIDFor`/`readPIDFor` of `file-helper.ts` (the processes are tracked by `Processes`); `forceRepoRebuild`
  (unused in TypeScript).
- **Windows.** The launcher `bin/boost.cmd` is used on Windows, but the wrapper test (a POSIX script) and
  `CliStartIT` (SIGTERM) are disabled there; the suite has been run on macOS.
