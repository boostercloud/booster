# Booster CLI for Java (`boost`)

The Java 27 port of `packages/cli` (`@boostercloud/cli`): the `boost` command that creates Booster projects,
generates resources and builds, runs, deploys and removes applications. It is built with
[picocli](https://picocli.info) and Mustache, and it talks to a user project **only through files and processes**
(Maven and the application's own main class): it has no dependency on the framework modules.

- Module: `java/cli` (artifact `com.boostercloud:booster-cli`), base package `com.boostercloud.cli`.
- Entry point: `com.boostercloud.cli.Main` (`bin/run` in TypeScript).
- Executable: `target/booster-cli-<version>.jar` (shaded) and the launchers `bin/boost` / `bin/boost.cmd`. The
  shaded jar is the main artifact, and the pom installed or deployed with it is the dependency-reduced one
  (`target/dependency-reduced-pom.xml`), without picocli and Mustache, which are inside the jar.

## Installing

```bash
cd java && mvn -B -q -pl cli -am package -DskipTests   # builds java/cli/target/booster-cli-4.1.0.jar
export PATH="$PWD/cli/bin:$PATH"                         # or: ln -s "$PWD/cli/bin/boost" /usr/local/bin/boost
boost --version                                          # booster-cli/4.1.0 darwin-aarch64 java-27.0.1
```

`bin/boost` follows symbolic links and looks for the jar in `$BOOSTER_CLI_JAR`, then `../lib/booster-cli.jar`
(an installed layout: copy `bin/` and the jar renamed to `lib/booster-cli.jar`), then `../target/booster-cli-*.jar`.
It runs the jar with `$JAVA_HOME/bin/java` (or `java` from the `PATH`), which must be Java 27 or newer; extra JVM
options go in `$BOOST_JAVA_OPTS`. New projects depend on `com.boostercloud` artifacts with the CLI's version, so
those artifacts must be resolvable (for the port: `mvn -B install` from `java/`).

## Commands

Every command accepts `-h`/`--help`. Flags and messages are those of the TypeScript CLI. `--fields` accepts
`name:type` pairs with TypeScript-style or Java types (see [field types](#field-types)); a multi-value flag consumes
the following arguments until the next flag, so the resource name goes first.

| Command | What it does |
| --- | --- |
| `boost new:project <name> [-d <description>] [-v <version>] [-a <author>] [-H <homepage>] [-l <license>] [-r <repository>] [-p <provider package>] [-g <group id>] [--default] [--skipInstall] [--skipGit]` | Creates a Maven project in `./<name>`. Missing values are asked interactively; `--default` uses the defaults (version `0.1.0`, MIT, the Azure provider, group `com.example`) without asking. Then resolves the dependencies (`mvn dependency:resolve`) and creates a git repository with an initial commit, unless skipped. |
| `boost new:command <Name> [-f <field>...]` | `commands/<Name>.java`: a `@Command` record with a static `handle(command, register)`. |
| `boost new:event <Name> [-f <field>...]` | `events/<Name>.java`: an `@Event` record with `entityID()`. |
| `boost new:entity <Name> [-f <field>...] [-r <Event>...]` | `entities/<Name>.java`: an `@Entity` record with an `id` and one `@Reduces` method per event. |
| `boost new:read-model <Name> [-f <field>...] [-p <Entity:joinKey>...]` | `readmodels/<Name>.java`: a `@ReadModel` record with an `id` and one `@Projects` method per entity. |
| `boost new:event-handler <Name> -e <Event>` | `eventhandlers/<Name>.java`: an `@EventHandler` class. |
| `boost new:query <Name> [-f <field>...]` | `queries/<Name>.java`: a `@Query` record with a static `handle(query, queryInfo)`. |
| `boost new:scheduled-command <Name>` | `scheduledcommands/<Name>.java`: a `@ScheduledCommand(minute = "0/30")` class. |
| `boost new:type <Name> [-f <field>...]` | `common/<Name>.java`: a plain record. |
| `boost add:reducer --entity <Entity> --event <Event>...` | Appends `@Reduces` methods (and the imports they need) to the existing entity class. |
| `boost add:projection --read-model <ReadModel> --entity <Entity:joinKey>` | Appends a `@Projects` method (and its imports) to the existing read model. |
| `boost stub:publish [-f]` | Copies the resource templates to `./stubs`; templates there override the built-in ones. |
| `boost build [--verbose]` | `mvn clean`, then `mvn package -DskipTests`. |
| `boost clean [--verbose]` | `mvn clean`. |
| `boost start [-e <env>] [-p <port>] [--verbose]` | Builds and runs the local server (port 3000 by default) until it stops; the output is streamed and Ctrl-C stops it. |
| `boost deploy [-e <env>] [--verbose]` | Builds and deploys the application to the provider of the environment. |
| `boost synth [-e <env>] [--verbose]` | Builds and generates the infrastructure templates without deploying them. |
| `boost nuke [-e <env>] [-f] [--verbose]` | Builds, asks for the application name and removes every resource of the environment (`-f` skips the question). |
| `boost <anything else> [-e <env>] ...` | Runs an injected command (rockets) of the already compiled application (the `command_not_found` hook). |
| `boost`, `boost --help`, `boost help [command]`, `boost new` | Help, in oclif's format. `boost --version` prints `booster-cli/<version> <platform>-<arch> java-<version>`. |

The environment is `-e <env>` or the `BOOSTER_ENV` variable (the flag wins). Resources are generated in packages
below the package of the main class (`com.example.mystore.commands`...).

### Field types

`string`→`String`, `number`→`double`, `boolean`→`boolean`, `UUID`→Booster's
`com.boostercloud.framework.types.concepts.UUID`, `Date`→`java.time.Instant`, `Array<T>`/`ReadonlyArray<T>`/`T[]`→`List<T>`,
`Record<K, V>`→`Map<K, V>`, `any`/`unknown`/`object`→`Object`. Primitives inside generic arguments are boxed
(`Array<number>`→`List<Double>`). Java types (`int`, `List<String>`, `BigDecimal`, `Instant`, `Optional<T>`...) and
any other identifier are kept as written; the needed imports are added, including classes of the project
(`--fields address:Address` imports `<base>.common.Address`).

### Generated project

```
my-store/
├─ pom.xml                     com.boostercloud:booster-framework-core, -provider-local, -provider-local-infrastructure
│                              (+ -provider-azure and -provider-azure-infrastructure for Azure), all ${booster.version}
├─ .gitignore, README.md
└─ src/main/java/com/example/mystore/
   ├─ Main.java                Booster.run(Main.class, args)
   ├─ config/Config.java       implements BoosterConfigurator: "local" (+ "production" with a cloud provider)
   └─ commands/ common/ entities/ eventhandlers/ events/ queries/ readmodels/ scheduledcommands/
```

The pom compiles with `--release 27` and `-parameters` (Booster reads handler parameter names), defines the
`booster.mainClass` property and copies the runtime dependencies to `target/lib` during `package`.

## Contract with the framework (`Booster.run`)

The CLI never loads user code. It recognises, builds and launches a project as follows; the framework must honour
this contract.

1. **Recognition.** A Booster project has a `pom.xml` that depends on `com.boostercloud:booster-framework-core`
   and defines the `booster.mainClass` property; the main class source (under `<build><sourceDirectory>`,
   `src/main/java` by default) contains `Booster.run(`. The core dependency's version (property references,
   `dependencyManagement`, ranges and `-qualifiers` are handled) is compared with the CLI's version: equal or
   differing only in the fix number (warning) is accepted; otherwise the command stops.
2. **Build.** `mvn -B -q clean` then `mvn -B -q package -DskipTests` in the project directory (`./mvnw` instead of
   `mvn` when the project has a Maven Wrapper). A failure stops the command with the Maven output in
   `./errors.log` (and on screen with `--verbose`).
3. **Launch.** `java -cp target/classes:target/lib/* <booster.mainClass> <action> [args]` (`;` on Windows), with
   `$JAVA_HOME/bin/java` when `JAVA_HOME` is set (otherwise the JVM running the CLI), working directory = project
   directory, and the CLI's environment plus `BOOSTER_ENV=<env>`. `Booster.run(Main.class, args)` must accept:
   - `start --port <N>`: start the local server on port N (the CLI also sets `BOOSTER_INTERNAL_LOCAL_PORT=<N>`)
     and keep running until the process receives SIGINT/SIGTERM. The CLI streams the output, considers the
     server started when the port accepts connections, forwards Ctrl-C (and kills the process if it has not stopped
     10 s later) and exits with the application's exit code.
   - `synth`, `deploy`, `nuke`: perform the operation through the infrastructure of the environment's provider and
     exit with 0 on success or a non-zero code on failure (for example when the provider does not support the
     operation); the CLI streams the output and exits with that code.
   - When `BOOSTER_CLI_HOOK=true` (unknown CLI commands): `<command> <args...>` or `--help`, to run the commands
     injected by rockets. The project is not rebuilt for these.
4. **Static checks before launching.** The CLI reads `Booster.configure("<env>", config -> { config.appName = "..."; })`
   calls in the sources: it fails early if no environment is configured or the selected one is not configured
   (skipped when an environment name is not a string literal), validates the literal `appName` with the project-name
   rules, and uses it (or the Maven artifact id) as the name `nuke` asks the user to type. `Booster.run` should still
   validate the environment itself, as TypeScript's `Booster` does.

The generated code uses these framework names (single place to change: `services/FrameworkApi.java`):
`com.boostercloud.framework.core.Booster`, `com.boostercloud.framework.core.BoosterConfigurator`,
`com.boostercloud.framework.core.decorators.{Command, Event, Entity, Reduces, ReadModel, Projects, Query, EventHandler, ScheduledCommand}`,
`com.boostercloud.framework.types.concepts.{Register, UUID, QueryInfo, ProjectionResult}` and the
`config.appName` / `config.providerPackage` fields of the configuration object.

## Source map (TypeScript → Java)

| TypeScript (`packages/cli/src`) | Java (`com.boostercloud.cli`) |
| --- | --- |
| `bin/run`, `index.ts` (`run`) | `Main`, `CliContext`, `CliVersion`, `help.HelpRenderer`, `help.ParseErrors` |
| `common/base-command.ts` | `common.BaseCommand` (`init`, `run`, `catchError` = `catch`), `commands.VerboseCommand` |
| `common/brand.ts` | `common.Brand`, `common.ColorSupport` (chalk) |
| `common/errors.ts` (`wrapExecError`, `guardError`, `unknownToError`) | `common.Errors` (+ `CliException`, `ProcessExitException`) |
| `common/filenames.ts` | `common.Filenames` |
| `common/provider.ts` | `common.Provider` |
| `common/script.ts` | `common.Script` (`catch` is `catchError`) |
| `common/sandbox.ts` | not ported (see below) |
| `commands/new/*.ts`, `commands/add/*.ts`, `commands/stub/publish.ts`, `commands/*.ts` | `commands.newcmd.*` (`new` is a Java keyword), `commands.add.*`, `commands.stub.Publish`, `commands.*` |
| `hooks/command_not_found/custom-parse.ts` | `hooks.commandnotfound.CustomParse` |
| `services/config-service.ts` | `services.ConfigService` (+ `services.project.*`) |
| `services/environment.ts` | `services.Environment` |
| `services/generator.ts`, `generator/target/*` | `services.Generator`, `services.Templates`, `services.generator.target.*` |
| `services/logger.ts` (`oraLogger`, `logger`, `appendOnErrorsFile`) | `services.OraLogger`, `services.Loggers`, `services.Logger` |
| `services/method-generator.ts` (ts-morph) | `services.MethodGenerator`, `services.java.*` |
| `services/project-checker.ts`, `semver.ts` | `services.ProjectChecker`, `services.Semver` |
| `services/project-initializer.ts`, `templates/project/*` | `services.ProjectInitializer`, `services.ProjectInitializerConfig`, `templates/project/*.mustache` |
| `services/provider-service.ts` | `services.ProviderService` |
| `services/stub-publisher.ts`, `templates/*.stub` | `services.StubPublisher`, `services.TemplateType`, `templates/*.stub` |
| `services/user-prompt.ts` | `services.Prompter` |
| `services/file-system/*`, `process/*`, `package-manager/*` | `services.filesystem.*`, `services.process.*`, `services.packagemanager.*` |

## Differences from TypeScript

Behaviour (messages, prompts, order of the steps, `errors.log`, exit codes) follows the TypeScript CLI. These are
the deliberate differences:

- **Java projects.** Generated projects are Maven projects; resources are Java records/classes named after the class
  (`ExampleCommand.java`, not `example-command.ts`) in packages under the main class's package. Package names cannot
  contain hyphens, so `read-models`, `event-handlers` and `scheduled-commands` are `readmodels`, `eventhandlers` and
  `scheduledcommands`; `queries` is created with the project (TypeScript creates it on demand). The
  `.eslint*`, `.prettierrc.yaml`, `.mocharc.yml`, `tsconfig*.json` and `index.ts` files have no Java counterpart; the
  project gets `pom.xml`, `Main.java`, `config/Config.java`, `.gitignore` and a `README.md`. The `.gitignore`
  ignores the build directory `/target/` (for `dist/`, `lib/`, `node_modules`) and anchors `/tmp/` and `/coverage`
  to the project root, which TypeScript leaves unanchored: Java packages are directories, so a package named
  `target`, `tmp` or `coverage` (or the group `com.target`, i.e. the whole source tree) would otherwise be left out of
  git, initial commit included.
- **Templates.** The stubs produce Java with the same placeholders (`authorize = /* Specify authorized roles here... */`,
  `return /* NEW X HERE */;`), which, as in TypeScript, must be completed before the code compiles. In projections
  (`new:read-model --projects`, `add:projection`) the placeholder is wrapped:
  `return ProjectionResult.of(/* NEW X HERE */);`. TypeScript's `ProjectionResult<T>` is `T | ReadModelAction`, so a
  projection may return the read model itself; Java needs the wrapper. In both, writing `new X(...)` in place of the
  placeholder compiles (`ProjectionResult.delete()` and `ProjectionResult.nothing()` are the other results). The view gives
  stubs `packageName`, one `imports` entry per class (`fqn`), Java field types (`type`; the original is `rawType`) and
  `last`/`hasFields`/`hasImports` flags. Imports are sorted and de-duplicated, and include the field types' classes;
  `type.stub` has imports too. `add:projection` writes `@Projects(entity = X.class, joinKey = "id")`.
- **Providers.** AWS is not ported. The provider choices are Azure, local only and other; `--default` uses Azure
  (TypeScript: AWS). `-p` takes a Java package (`com.boostercloud.framework.provider.azure`); the npm names of the
  Azure and local providers are accepted and mapped. A local-only project has no `production` environment. A new
  `-g/--groupId` flag (and prompt, before the provider) sets the Maven group and the root package; it is honoured
  with `--default`. The group and provider must be valid Java package names, and project names may only contain
  lower-case letters, digits, `-` and `.` (a valid artifact id), checked after the TypeScript rules.
- **No in-process loading.** TypeScript compiles the project and `require`s it to read its `BoosterConfig` and call
  the provider; Java launches the application (see the contract). Environment and `appName` checks are therefore
  static. `nuke` compares the typed name with the literal `appName` of the environment or, when unknown, the Maven
  artifact id.
- **No deployment sandbox.** `deploy`/`synth` build in place instead of copying the project to `.deploy` and
  installing production dependencies there: Maven already separates the runtime class path (`target/lib`), and the
  application reads `config.assets` itself. Hence no "Cleaning up deployment files" step; `common/sandbox.ts` and
  `createDeploymentSandbox`/`cleanDeploymentSandbox`/`DEPLOYMENT_SANDBOX` have no counterpart.
- **Package managers.** npm/pnpm/yarn/Rush become Maven and the Maven Wrapper (inferred from `mvnw` in the current
  directory). `runScript(name)` runs a phase/goal, `build` is `package -DskipTests` (there is no `compile`-script
  lookup), dependency installation is `dependency:resolve`. Failed commands include their output in the error
  (execa 2 does not), so compiler errors reach `errors.log`/`--verbose`.
- **Prompts** read lines from the standard input: list prompts are numbered (inquirer uses arrow keys) and an
  exhausted input answers with the default. Double quotes in answers are not escaped (TypeScript escapes them for
  `package.json`; the templates escape for XML/Java). `confirmPrompt` is an instance method.
- **Parsing (picocli instead of oclif).** Messages are mapped to oclif's (`Flag --x expects a value`,
  `Nonexistent flag: --x`, `Unexpected argument: X`, `Missing required flag x`). All parse errors exit with code 2
  without writing `errors.log` (in TypeScript some commands route them through `catch`, exiting with 1), and they
  are detected before the version check. `-h` and `--help` always show help without checking the project. The
  `usage` lines of `add:*`/`stub:publish` include the full command id.
- **Exit codes.** `deploy`, `synth`, `nuke`, `start` and the hook exit with the application's exit code.
  `new:project` keeps the TypeScript behaviour of exiting with 0 after printing an error, and prints it without
  stack frames.
- **Effect-ts services** (`Layer`, tags) are plain interfaces passed through `CliContext`, which also replaces
  `process.cwd()`, `process.env`, the global ora spinner, inquirer and execa so that tests can fake them.
- **The `command_not_found` hook** removes exactly the first `-e <env>` pair (TypeScript filters `process.argv`
  by value) and prints a "command not found" error outside a Booster project or "not compiled yet" when
  `target/classes` is missing (TypeScript fails to `import dist/index.js`).
- Command descriptions say `pom.xml` / `BoosterConfigurator` classes instead of `index.ts`. Topics have
  descriptions in the root help.

## Tests

`mvn -B -q -pl cli -am verify` from `java/`. The suite ports the TypeScript tests (`*Test` classes named after the
`*.test.ts` files) using real temporary projects and a fake process runner instead of sinon stubs, and adds an
end-to-end test (`EndToEndTest`) that runs `new:project`, every generator, `add:*`, `stub:publish`, `build`,
`start`, `deploy`, `synth`, `nuke` and the hook, then compiles the generated sources against a minimal stand-in of
the framework API. Nothing needs network access or the framework artifacts.

The CLI integration suite of the TypeScript monorepo (`integration/provider-unaware/cli`) runs the real `bin/boost`
launcher against the framework jars of this reactor; it is the module
[`cli-integration-tests`](../cli-integration-tests/README.md) (`mvn -B -Pe2e -pl cli-integration-tests -am verify`).
