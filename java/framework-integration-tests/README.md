# booster-framework-integration-tests (`com.boostercloud.integration`)

Java port of `packages/framework-integration-tests`: the sample application every integration suite runs against
("my-store", the cart demo of `src/`) and the end-to-end harness and suites that start it as a real process on the
local provider and talk to it over HTTP and WebSocket. The CLI generator suite (`integration/provider-unaware/cli`) is
not here: it tests the `boost` CLI, not this application.

```bash
cd java
mvn -B -pl framework-integration-tests -am verify            # build + unit tests (no application is launched)
mvn -B -Pe2e -pl framework-integration-tests -am verify      # + every *IT suite against launched applications
```

## The application (`src/main/java`)

A faithful port of the whole TypeScript `src/` tree (133 files), written with the user-facing API of
`java/ARCHITECTURE.md` §6b. Every GraphQL type, field, argument and operation name is the TypeScript one, so the
TypeScript GraphQL documents work unchanged; `ApplicationRegistrationTest` boots the application in-process and pins
the registered concepts and the GraphQL shapes the TypeScript suites rely on.

| TypeScript (`src/`) | Java (`com.boostercloud.integration`) |
| --- | --- |
| `index.ts` (`Booster.start(__dirname)` + the re-exported entry functions) | `Main` (`Booster.run(Main.class, args)`; core registers the entry points as `UserApp`) |
| `constants.ts`, `roles.ts` | `Constants`; `roles.*` (`Admin`, `UserWithEmail`, `UserWithPhone`, `SuperUser`, `SuperUserNoConfirmation`) |
| `config/config.ts`, `config/injectable.ts` | `config.Config` (a `BoosterConfigurator`), `config.CustomPublicKeyTokenVerifier`, `config.Injectables` |
| `common/*.ts` | `common.*` (`Address`, `CartItem`, `Money`, `Picture`, `AppErrorHandler`, `CustomLogger`, `CustomTracer`) + `JsonStringify`, `Nullable` |
| `commands/*.ts` (35) | `commands.*` |
| `events/*.ts` (25), `notifications/cart-abandoned.ts` | `events.*` (+ `ProductUpdateReason`), `notifications.CartAbandoned` |
| `entities/*.ts` (17 files, 18 entities) | `entities.*` (+ `MigratedCart`, `OrderItem`, `ProductType`) |
| `read-models/*.ts` (19) | `readmodels.*` (+ `MediaValue`) |
| `queries/*.ts` (4) | `queries.*` |
| `event-handlers/*.ts`, `scheduled-commands/*.ts`, `sensor/health/*.ts` | `eventhandlers.*`, `scheduledcommands.CheckCartCount`, `sensor.health.*` |
| `migrations/{commands,entities,events,read-models,data}/**` | `migrations.{commands.updateproduct,entities.product,events.productupdated,readmodels.*,data}.*` |

Environments: `local` (local provider, `com.boostercloud.framework.provider.local`) and `azure` (Azure provider,
`com.boostercloud.framework.provider.azure`, app name `my-store-<BOOSTER_APP_SUFFIX ?? default>`, assets `assets`,
`assetFile.txt`, `host.json`). The TypeScript `development` and `production` environments use the AWS provider, which
is not ported, so they are not configured. No rockets are configured (as in TypeScript), so the module depends on no
rocket. The TypeScript app declares no sequenced read model (no `@sequencedBy` in `src/`), so neither does this one.
The token verifiers read `assets/certs/public.key` from the project directory (`config.userProjectRootPath`, the
module directory), the counterpart of the application tester's signing key.

The magic ids of `Constants` drive the before hooks, the global error handler and the failing handlers exactly as in
TypeScript (spec 16 §3); their behaviour is pinned by the unit tests of `src/test/java` named after each package.

## The harness (`src/test/java/.../harness`, `.../helper`)

| Type | TypeScript | What it does |
| --- | --- | --- |
| `harness.AppProcess` | `local/start/setup.ts`, `local/stop/setup.ts`, `cli-helper.start` | launches the application as a separate JVM exactly like the CLI launch contract (`java -cp target/classes:target/lib/* com.boostercloud.integration.Main start --port <N>`, working directory = the project directory, the test JVM's environment plus `BOOSTER_ENV=local`, `BOOSTER_INTERNAL_LOCAL_PORT=<N>`, `LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT=<M>`, `BOOSTER_APP_SUFFIX`); both ports are free ports; waits until `POST /graphql` answers 200 and the websocket port accepts connections; stores the PID in `<project>/local_provider.pid`; output to `target/e2e-logs/<name>.log` |
| `harness.ApplicationUnderTest` | `applicationUnderTest` of `end-to-end/setup.ts` | `graphql()`, `token()`, `http()`, `count()`, `query()` (the application tester's `ApplicationTester`), `outputs()`, `process()` |
| `harness.LocalProviderTestHelper`, `AzureProviderTestHelper` | `getProviderTestHelper()` results | the tester's `ProviderTestHelper` over `LocalTestHelper` (counters and queries read the application's `.booster` files read-only) or `AzureTestHelper` |
| `harness.EndToEnd`, `EndToEndExtension`, `SharedApplication` | `.mocharc.yml` `file:` setups (root hooks) | the JUnit extension that gives suites the running application |
| `harness.E2eSettings`, `FreePorts`, `Faker` | — / — / `faker` 5 | build locations and system properties; free ports; random data with faker's call names |
| `helper.AppHelper`, `AuthHelper`, `FileHelper`, `Sandbox` | `integration/helper/app-helper.ts`, `auth-helper.ts`, `file-helper.ts`, `cli/src/common/sandbox.ts` | `applicationName()`, `setEnv()`, `checkAndGetCurrentEnv()`, `getProviderTestHelper()`; `createPassword()`; fixtures, files, PID files; sandbox projects |

`waitForIt`/`sleep` are the application tester's `Sleep.waitForIt(...)`/`Sleep.sleep(ms)` (same semantics and timeout
messages as `integration/helper/sleep.ts`); tokens are `applicationUnderTest.token().forUser(email[, role[,
TokenOptions]])`.

### `AppProcess` API

```java
AppProcess app = AppProcess.builder("my-suite")      // log target/e2e-logs/my-suite.log
    .projectDirectory(dir)                            // default target/e2e-projects/my-suite-integration-sandbox
    .env("NAME", "value").jvmOption("-Xmx512m")       // optional
    .assets(List.of("assets", "assetFile.txt"))       // default: what local/start/setup.ts copies
    .readinessTimeout(Duration.ofMinutes(2))          // default 120 s (-Dbooster.e2e.readinessTimeoutSeconds)
    .build();                                         // allocates the two free ports, starts nothing
app.start();          // first start recreates the project directory; later starts keep it (and .booster)
app.stop();           // SIGTERM, waits (30 s), fails unless the JVM ended through its graceful shutdown (143 or 0)
app.kill();           // SIGKILL: a crash, nothing flushed by the application
app.restart();        // stop() + start(): same ports, same directory, same data
app.awaitExit(timeout); app.isAlive(); app.pid(); app.exitCode(); app.starts(); app.output();
app.projectDirectory(); app.boosterDirectory(); app.logFile();
app.httpPort(); app.webSocketPort(); app.graphqlURL(); app.websocketURL(); app.healthURL();
app.close();          // stop (or kill) and delete the project directory (kept with -Dbooster.e2e.keepProjects=true)
ApplicationUnderTest applicationUnderTest = ApplicationUnderTest.of(app);
```

Every process still running when the test JVM exits is killed by a shutdown hook.

## Layout contract for the suites

**Classes and packages.** One `*IT` class per TypeScript `*.integration.ts` file, in the package of its TypeScript
directory; the class name is the file name in PascalCase without `.integration.ts` (dots dropped) plus `IT`.
Functionality suites add `Functionality` before `IT`, so that every simple name is unique (`-Dit.test` matches simple
names):

| TypeScript directory | Java package | Example |
| --- | --- | --- |
| `integration/provider-unaware/end-to-end` | `com.boostercloud.integration.endtoend` | `cart.integration.ts` → `CartIT`, `delete.events.integration.ts` → `DeleteEventsIT`, `read-model-concurrency.integration.ts` → `ReadModelConcurrencyIT` |
| `integration/provider-unaware/functionality` | `com.boostercloud.integration.functionality` | `errors.integration.ts` → `ErrorsFunctionalityIT`, `schema.filter.integration.ts` → `SchemaFilterFunctionalityIT` |
| `integration/provider-specific/local` | `com.boostercloud.integration.providerspecific.local` | `LocalStartStopIT`; Java-only restart/crash suites (`RestartIT`, ...) |
| `integration/provider-specific/azure` | `com.boostercloud.integration.providerspecific.azure` | not runnable here (needs a deployment) |

Unit tests (no process) are `*Test` classes in the package of the class they test. Only `*IT` classes run in the
`e2e` profile; only `*Test` classes run in a plain build.

**Getting the application.** Annotate the suite with `@EndToEnd` and take `ApplicationUnderTest` (or `AppProcess`,
or `ApplicationTester`) as a constructor or method parameter:

```java
@EndToEnd                                  // SHARED: the one application of the whole run
@DisplayName("Queries end-to-end tests")   // the mocha describe title, verbatim
class QueriesIT {
  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  QueriesIT(ApplicationUnderTest applicationUnderTest) { this.applicationUnderTest = applicationUnderTest; }

  @BeforeAll void before() { client = applicationUnderTest.graphql().client(); }        // mocha before

  @Nested @Order(1) @DisplayName("when ...")                                             // describe / context
  class When { @Test @Order(1) @DisplayName("accepts a query successfully") void ...() { ... } }
}
```

* `@EndToEnd` (= `SHARED`) mirrors TypeScript: one application process for the whole failsafe run (sandbox
  `local-project`, log `target/e2e-logs/local-project.log`), started before the first suite and stopped gracefully
  (SIGTERM, exit code checked) after the last one; a non-graceful stop fails the run. Its data accumulates across
  suites, as in TypeScript: use random ids, compare counts as deltas and never assume empty stores. The scheduled
  command `CheckCartCount` registers a `CartChecked` event for `the-checked-cart` every minute.
* `@EndToEnd(EndToEnd.Application.OWN)` gives the class a fresh application of its own (named after the class),
  started before its first test and stopped after its last one: for suites that restart, kill or otherwise disturb
  the process (`AppProcess` parameter: `restart()`, `kill()` + `start()`, `stop()`...), or that need an empty store.
* With `TESTED_PROVIDER=AZURE` nothing is launched: `AppHelper.getProviderTestHelper()` finds the deployed application
  (`AppProcess` parameters then fail to resolve).

**Mocha semantics** (`src/test/resources/junit-platform.properties`): one test instance per class
(`@BeforeAll`/`@AfterAll` are instance methods = `before`/`after`, fields hold the state of a `describe`), and
`@Order` gives the order of tests and nested classes (mocha runs in declaration order). `@BeforeEach` = `beforeEach`;
`@Nested` inner classes see the outer fields, which is how a `client` shared by several `describe` blocks is ported.
`@DisplayName` carries the verbatim mocha titles. Because of the per-class lifecycle, unit tests must reset mutable
fields in `@BeforeEach`.

**Porting rules.** Same scenarios, data, documents and assertions as TypeScript:

* GraphQL documents are Java text blocks with the exact TypeScript text (`${...}` interpolations with
  `.formatted(...)`); `client.query(doc, vars)` / `client.mutate(doc, vars)` return a `GraphQLResponse`
  (`response.get("CartReadModel.cartItems[0].quantity")`); `__typename` is added to every selection set like Apollo
  does, so deep-equal expectations keep their `__typename` keys. Errors are `GraphQLClientException`
  (`graphQLErrors().get(0).message()`, `.path()`); `rejectedWith(/re/)` is `hasMessageFindingMatch("re")`.
* `Promise.all` of concurrent requests is `Promises.runAll(List<Callable<T>>)` (framework-common-helpers);
  `random.number({min: 1})` is `Faker.random.number(1)`, and so on.
* Tests TypeScript skips stay skipped with the same reason: `xit`/`xdescribe` → `@Disabled("<reason>")`; a provider
  gate (`if (process.env.TESTED_PROVIDER === 'AWS') return`) → `Assumptions.assumeFalse(...)` with the TypeScript
  reason (AWS never runs here, so such gates only document). A TypeScript assertion that cannot hold on the local
  provider is skipped on `LOCAL` with an `assumeFalse` explaining why, and the local behaviour is pinned by an extra
  test (see `CommandsIT`). TypeScript assertions that pass silently (`try { ... } catch (e) { expect... }` without
  `fail`) are made strict, with a comment.
* Counters and raw queries (`applicationUnderTest.count()`, `.query()`) are real on the local provider (TypeScript's
  local helper returns `-1`/`[]`), so provider-gated assertions on them can run on `LOCAL`.

**Adding code.**

* Suite-specific documents and helpers stay inside the suite class (private constants/methods). A helper needed by
  several suites goes in a **new** class of `harness` or `helper`; existing harness classes only change to fix a bug,
  in a separate commit `fix(framework-integration-tests): ...`.
* The application (`src/main/java`) stays a faithful port of the TypeScript `src/`: never add concepts for a test.
  When a suite reveals a porting bug in it, fix that class minimally in a separate commit
  `fix(framework-integration-tests): ...` and keep `ApplicationRegistrationTest` and the unit tests green (update
  their pins only when TypeScript behaves that way). A framework bug is fixed in its module (`fix(<module>): ...`).
* No fixed ports and no `Thread.sleep` loops: launch with `AppProcess` (free ports) and poll with `Sleep.waitForIt`.

**Running.** From `java/` (`JAVA_HOME` must be a JDK 27):

```bash
mvn -B -Pe2e -pl framework-integration-tests -am verify                                   # everything
mvn -B -Pe2e -pl framework-integration-tests -am verify \
    -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=CartIT                  # one suite, no unit tests
mvn -B -Pe2e -pl framework-integration-tests -am verify \
    -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false '-Dit.test=CartIT,CommandsIT'     # several suites
```

`-Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false` skips the unit tests of every module of the reactor (`-am`
builds the framework modules from source; nothing is installed in `~/.m2`).

`-Dbooster.e2e.keepProjects=true` keeps the project directories (`target/e2e-projects`) for inspection;
`-Dbooster.e2e.appJvmOptions="-Xmx512m ..."` passes JVM options to the launched applications;
`-Dbooster.e2e.readinessTimeoutSeconds=N` changes the start timeout. The failsafe JVM runs with
`TESTED_PROVIDER=LOCAL` and `BOOSTER_ENV=local` (`npm run integration/local-end-to-end`) and a per-test timeout of
3600 s (the mocha `timeout`). The launched applications use the runtime class path the build copies to `target/lib`
during `package`, exactly like a project built by the CLI.

## Functionality suites (`functionality`)

The port of `integration/provider-unaware/functionality` (spec 17 §9), which TypeScript runs on AWS and Azure
(`aws-func`, `azure-func`) and locally only by hand (`integration/local-func`, where its raw-store assertions time out
because the local test helper returns `-1`/`[]`). Here every suite runs on `LOCAL` with the shared application:

| TypeScript | Java | What it checks |
| --- | --- | --- |
| `entities.integration.ts` | `EntitiesFunctionalityIT` | a cart and its payment projected into one stored `CartReadModel` |
| `errors.integration.ts` | `ErrorsFunctionalityIT` | the global error handler on command handlers, before hooks and query handlers |
| `event-handlers.integration.ts` | `EventHandlersFunctionalityIT` | `StockMoved` → `ProductAvailabilityChanged`, both stored with the Admin `currentUser` |
| `events.integration.ts` | `EventsFunctionalityIT` | `register.flush()`, stored event documents, 30 and 101 + 50 events in one command |
| `notifications.integration.ts` | `NotificationsFunctionalityIT` | flushed notifications and their handler, the `defaultTopic` event document |
| `scheduled-commands.integration.ts` | `ScheduledCommandsFunctionalityIT` | `CheckCartCount` checks `the-checked-cart` within 90 s |
| `schema.command.integration.ts` | `SchemaCommandFunctionalityIT` | `@NonExposed` command properties are not in the input type |
| `schema.filter/search/sort.integration.ts` | `SchemaFilterFunctionalityIT`, `SchemaSearchFunctionalityIT`, `SchemaSortFunctionalityIT` | the GraphQL filter, object and sort types of `SchemaReadModel` |
| `subscriptions.integration.ts` | `SubscriptionsFunctionalityIT` | unsubscribe, terminate, reconnect, filters with before hooks, authorization over the websocket |
| — (the `TODO` contexts of `errors.integration.ts`) | `LoggedErrorsFunctionalityIT` (own application, `LOCAL` only) | `onDispatchEventHandlerError`, `onReducerError`, `onProjectionError`: the rewritten errors in the application log and what the failures leave in the stores |

`StoredDocuments` (raw document paths, chai's `deep.contain`, the event-store key) and `TheCheckedCart` (counters
without the scheduled command's work) are the package's helpers.

Differences from TypeScript:

* **Event-store key.** AWS and Azure add the partition key `entityTypeName_entityID_kind` to every event document; the
  local provider stores the envelopes as they are (TypeScript and Java alike). On `LOCAL` the suites check the three
  fields that key is made of (`entityTypeName`, `entityID`, `kind`) instead, and leave the field out of `deep.contain`
  expectations (`StoredDocuments.expectedEventDocument`/`assertEventStoreKey`).
* **Error-handler metadata.** The expected messages of `ErrorsFunctionalityIT` embed the Java
  `JSON.stringify(commandMetadata)` of `ChangeCartItem` (same shape, Java type information), the string
  `ApplicationRegistrationTest` pins.
* **Readonly arrays.** `For readonly array` of the filter and search suites is disabled with its reason, and
  `For readonly array as a Java list` pins the Java schema: the filter of `readonlyArray` is
  `StringArrayPropertyFilter` (TypeScript: `JSON`, Java has no read-only list type), and its type is `[String!]`
  (TypeScript: `[String]`, because the first string list registered decides the element nullability and the
  registration orders differ; see `ApplicationRegistrationTest`).
* **Order of stored events.** TypeScript's `expect(ids).to.be.sorted((prev, next) => prev > next)` always passes (a
  boolean comparator never reorders in V8); `EventsFunctionalityIT` asserts what it means, newest first.
* **Exact counts.** The two exact count deltas (one event, one cart read model) leave out the work of the scheduled
  command `CheckCartCount` (`TheCheckedCart`): the shared application is fresh, so the cart it checks is created and
  checked during the run, and a check between the two counts would make the delta two forever.
* **Subscriptions.** The two `the "terminate" operation` tests swallow every error in TypeScript; here their
  assertions count (clients disconnected in `finally`). Subscription results are awaited at most 2 minutes (TypeScript:
  the mocha timeout). `keeps the same subscriptions` also checks that a change made after the reconnection reaches
  every subscription (the TypeScript check after the reconnection is satisfied by the result received before it).
* **Introspection documents.** graphql-java rejected the nested `__Type.inputFields` of the filter and sort suites
  ("good faith introspection"); framework-core now validates with the specification rules only, like graphql-js.
* `notifications > should be persisted when flush is call` and the other suites print what TypeScript logs with
  `console.log` to the test output.

## Durability and recovery (`providerspecific.local.durability`)

Java-only suites, beyond TypeScript (whose local provider neither flushes, recovers nor validates its files), that
check what the application keeps across restarts and crashes on the local provider. Each suite runs against an
application of its own (`@EndToEnd(EndToEnd.Application.OWN)`, its own process and project directory) that it stops,
kills (`SIGKILL`), damages and starts again on the same `.booster` files.

### Guarantees

1. **Acknowledged means durable.** A mutation is answered after its events were written and flushed
   (`FileChannel.force`) and dispatched in-process, so everything acknowledged before a crash is in the store after
   it, exactly once.
2. **A command is all or nothing.** The events one command (event handler, scheduled command) registers are written
   with a single append: a crash leaves all of them or none, never part of a `ChangeMultipleCartItems`.
3. **Undispatched events are recovered once.** Events stored but whose dispatch had not started when the process
   died are re-dispatched at start-up, before the server serves (`Re-dispatched N event(s) ...`).
4. **At most once, and the gap closes.** An event whose processing core had already started (`storeDispatched`) when
   the process died is not processed again (core's dedup, as on the cloud providers; reported at start-up as
   `The processing of N event(s) was interrupted by the previous shutdown ...` with their ids). Its events are stored;
   the read models of its entity may miss it until the entity's next event, which reduces every stored event, so they
   converge then. It is never half applied.
5. **No double reduction or projection.** A counter's `amount` never exceeds its stored `CounterAdded` events and its
   read model is not stored more often (`boosterMetadata.version`) than there are events.
6. **Damaged files.** On start every existing data file is loaded: a torn last line (a write cut by a crash) is
   discarded and a few corrupt lines (at most 10 %) are skipped, each with a warning naming the file; with more than
   10 % the application refuses to start (exit code 1, `The 'start' operation failed`, NeDB's
   `N% of the data file is corrupt, more than given corruptAlertThreshold (10%). Cautiously refusing to start NeDB to
   prevent dataloss.`), leaving the file untouched, and starts again once it is repaired.
7. **Graceful stop.** `SIGTERM` ends with exit code 143 after in-flight requests and running scheduled commands
   finished and the websocket connections were closed (their `DISCONNECT` deletes their subscriptions). Subscription
   clients reconnect by themselves and re-send their subscriptions.
8. **Scheduled commands** are scheduled again by every start (runs that fell while the process was down are not
   replayed: the schedule is a cron).
9. **One format.** The files are NeDB's, so a `.booster` directory written by the TypeScript provider is served by
   the Java application, which keeps writing it in the same format.

### Suites

| Suite | Scenario | What it proves |
| --- | --- | --- |
| `GracefulRestartIT` | commands, a product, a data migration and a live subscription; `SIGTERM`; start | 7, and that every event, snapshot and read model document, the data-migration state (finished migrations are not run again) and every GraphQL answer are identical after the restart; reduction continues from the stored snapshots; the subscription client reconnects and keeps receiving notifications |
| `CrashUnderLoadIT` | 16 clients send `IncrementCounter` (unique identifiers) and `ChangeMultipleCartItems` (8 events); `SIGKILL` after 150 acknowledgements; start | 1–5 (`LoadConsistency`): acknowledged mutations stored exactly once, in-flight ones all or nothing, nothing invented, read models converge to the stored events except the reported at-most-once gap, which the next event of each entity closes |
| `TornWriteIT` | `SIGKILL`; partial JSON line appended to `events.json` and `read_models.json`; then 5 % and more than 10 % of corrupt lines | 6: starts and serves the same data with a torn line, compacts it away and keeps later appends on their own lines; skips few corrupt lines with a warning; refuses to start with too many (exit code 1, file untouched, port released); starts once repaired |
| `RepeatedCrashCyclesIT` | 6 × (concurrent writes, `SIGKILL`, start), then a graceful restart | 1–5 over everything written since the first cycle after every restart: crashes do not accumulate damage |
| `TypeScriptDataFilesIT` | the application started on `fixtures/durability/typescript-local-provider` (NeDB files with this application's data shapes as the TypeScript provider writes them) | 9: read models (appended updates, removed ones), events with their users and the event search are served; entities continue from the TypeScript snapshots, events TypeScript never projected are reduced, read-model versions and the `uniqueKey` index continue; all of it survives a restart |
| `ScheduledCommandsAfterRestartIT` | `CheckCartCount` (every minute) before, after a `SIGTERM` restart and after a `SIGKILL` | 8, and that its earlier runs are kept and counted once (`checks` = stored `CartChecked` events) |

Shared code of the package: `CommandLoad` (concurrent mutations with a record of what was acknowledged),
`LoadConsistency` (rules 1–5 over the data files and the API), `StoreFiles` (read-only replay of `.booster` with
`LocalStoreReader`, raw bytes and lines, damaging writers), `RecoveryLog` (what the start-up recovery reported, per
start) and `Documents`. `ScheduledCommandsAfterRestartIT` waits for up to three scheduler minutes; the others take
seconds.

These suites found three framework gaps, fixed in `framework-provider-local` and
`framework-provider-local-infrastructure` (see their READMEs): the events of one command were written one by one (a
crash left 7 of 8 `CartItemChanged` stored), damaged files were only found by the first request touching them (the
application started on a corrupt store), and stopping the websocket server interrupted the `DISCONNECT` of open
connections (their subscriptions survived the restart and every later notification logged an error).

## Azure contract

The TypeScript `integration/azure` scripts deploy the app to a real subscription (`azure-deploy`), run the end-to-end
and functionality suites against it and nuke it (`azure-nuke`), spec 17 §3.3. Nothing here deploys: the suites of
`com.boostercloud.integration.azurecontract` check the same contract offline, with `BOOSTER_ENV=azure`,
`BOOSTER_APP_SUFFIX=contract` (app `my-store-contract`, resource group `mystorecontractazurerg`) and the production code
paths of `framework-provider-azure` and `framework-provider-azure-infrastructure`:

| Suite | What runs | What is asserted |
| --- | --- | --- |
| `AzureSynthesisContractIT` | `java -cp target/classes:target/lib/* com.boostercloud.integration.Main synth` in a sandbox laid out like a CLI-built project (`CliBuiltProject`: the assets plus copies of the module's `target/classes` and `target/lib`) | the files (`cdktf.out/manifest.json`, `stacks/my-store-contractazure/cdk.tf.json`, `functionApp.zip`, `consumerFunctionApp.zip`, `baseWebPubSubBinding.zip`, no `cdktf.json`, no rocket package); every Terraform resource of the stack with its TypeScript construct id, name and attributes (one container per store and per read model with partition keys, unique keys and TTLs, the event hub `test` with 3 partitions and 1 day, the consumer plan with 3 workers, both function apps and their app settings, Web PubSub and its hub, the gateway routes, the outputs); the exact `function.json` of every function (`graphql`, `sensor-health`, `eventProducer` because streaming is on, `connect`/`disconnect`/`messages`, the 19 `<ReadModel>-subscriptions-notifier`, `scheduleFunction-CheckCartCount` with `0 0/1 * * * *`), `eventConsumer` in the consumer package, the bootstrap function; the default `host.json` (the `host.json` asset is ignored, as `createZipV4` does); the package layouts (application jar with every compiled class, `lib/`, assets) |
| `AzureDeployNukeContractIT` | the app started in-process as `Booster.run` starts it, then `deploy` and `nuke` of `Infrastructure.infrastructure(config.rockets, ...)` with an `InfrastructureContext` whose command runner, HTTP transport and sleeper are a stateful fake subscription (`FakeAzureCloud`); sandboxes `deploy` and `nuke` with the assets of the TypeScript setups | the TypeScript assertions (`After deployment > the ARM template > has been created successfully`: `az group show` finds the resource group that `terraform apply` created; `After nuke > the resource group > is deleted successfully`: `ResourceGroupNotFound`), and the exact sequence: `terraform init -input=false`, `plan -input=false -out=plan`, `apply -input=false -auto-approve plan` in the stack directory with `TF_IN_AUTOMATION=true`; publishing credentials and Kudu `zipDeploy` of `functionApp.zip` then `consumerFunctionApp.zip` with basic authentication; the cold start, 30 s, the host keys, the hub `PUT` with the `webpubsub_extension` key; one Entra ID token; the failure message and no upload when `terraform apply` fails; the resource group `DELETE` and its long-running operation |
| `AzureRuntimeContractIT` | the entry points of `BoosterAzureFunctions` invoked in-process with the payloads of the Java worker (`FunctionsWorker`), the app settings Terraform gives the function apps pointing at `FakeAzureServices`, a stateful in-JVM Cosmos DB / Event Hubs / Web PubSub provisioned with the containers of the stack; the app boots from `BOOSTER_APP_CLASS` | a `ChangeCartItem` mutation through `graphql` stores its event in one transactional batch (headers, partition key, document); `eventProducer` with the change feed sends it to the event hub `test`; `eventConsumer` de-duplicates (`events-dedup`), marks it dispatched, reads the snapshot and the events with the TypeScript SQL, stores the snapshot and projects `CartReadModel` (schema version 2) and `SchemaReadModel`; a redelivery is dropped; a `CartReadModel` query reads the read model back with a point read; `scheduleFunction-CheckCartCount` with a timer payload registers `CartChecked` for `the-checked-cart`; `connect`, `messages` (`connection_init`, `start`), `CartReadModel-subscriptions-notifier` (the data message reaches the connection through Web PubSub) and `disconnect`; the Azure test helper of `TESTED_PROVIDER=AZURE` (`AzureTestHelper` + `harness.AzureProviderTestHelper` + `ApplicationTester`, with a fake `az`) counts and queries what was stored, and its Admin token makes `CreateProduct` succeed (the post-deployment check of `deployment.integration.ts`) |
| `AzureTerraformValidateIT` | opt-in (`BOOSTER_TERRAFORM_VALIDATE=true`): synth, then `terraform init -backend=false` and `terraform validate -json` in the stack directory (`BOOSTER_TERRAFORM_BINARY`, default `terraform`; set `TF_PLUGIN_CACHE_DIR` to reuse the azurerm provider) | Terraform and the azurerm 5.x provider accept the stack |

```bash
mvn -B -Pe2e -pl framework-integration-tests -am verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false \
    '-Dit.test=AzureSynthesisContractIT,AzureDeployNukeContractIT,AzureRuntimeContractIT'   # about 10 s
BOOSTER_TERRAFORM_VALIDATE=true mvn -B -Pe2e -pl framework-integration-tests -am verify -Dtest=NONE \
    -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AzureTerraformValidateIT          # downloads azurerm once
```

The expectations are written from the TypeScript sources, not from the Java output: `TypeScriptAzureApp` lists the
concepts of the TypeScript app that drive the synthesis (19 read models, `CheckCartCount`, subscriptions and streaming
on, no rockets, the azure assets), the names `helper/utils.ts` derives for `my-store-contract` + `azure`, and the
functions `functions-code-generator.ts` registers, in its order.

The fakes are strict, so a contract violation fails the suite instead of being absorbed: `FakeAzureServices` checks the
Cosmos DB master-key signature, the partition key header against the document, unique ids and unique keys per logical
partition, `If-Match`, the 400/1004 refusal of cross-partition queries the gateway cannot serve, the Event Hubs shared
access signature and the Web PubSub access-key JWT; it executes queries with `FakeCosmosSql` (the subset of the SQL
dialect Booster uses, with Cosmos DB semantics: undefined comparisons, type order, `IN`, `IS_DEFINED`, `ORDER BY`,
`OFFSET/LIMIT`, `TOP`, `VALUE COUNT`, `DISTINCT`) and answers query plans like the gateway (rewritten `ORDER BY` queries
with `orderByItems`/`payload`). Unknown endpoints, missing containers, unsupported queries and bad signatures are
recorded in `failures()`, which every test requires to be empty. `FakeAzureCloud` answers only for what `terraform apply`
created from the synthesized JSON (resource groups, function apps, Web PubSub) and creates the `webpubsub_extension` key
only after a package with a Web PubSub trigger was uploaded and the host was started. `FakeCosmosSqlTest` and
`FakeAzureServicesTest` (plain `verify`) pin the fakes themselves against the production REST client.

The in-process suites restore what they change (`InProcessAzureApplication`: the `booster.env` property, the
`EnvironmentVariables` overrides, `Booster.reset()`), so they share the failsafe JVM with the local suites. The Azure
provider creates its clients once per JVM from the app settings (`AzureClients.shared()`), hence one `FakeAzureServices`
per JVM (`shared()`, reset by each suite); a suite pointing the provider at another endpoint in the same JVM would not
be seen.

Differences from TypeScript in these suites (beyond the documented ones of the Azure modules):

* The TypeScript suites run `boost deploy`/`boost nuke` against Azure and assert two facts afterwards; here the same
  facts are asserted against the fake subscription, together with the command and request sequence that produces them.
  The functions run in-process instead of in the Azure Functions host: the binding of the `function.json` files by the
  Java worker (data types, the Web PubSub return value) is not exercised.
* The CLI launch is reproduced (`CliBuiltProject`) rather than going through `boost synth`, which would rebuild the
  project with Maven; the deploy and nuke run in the test JVM because their outside world must be injected.
* Container definitions are read once per container by the REST client (cached), so the request sequences ignore those
  `GET /dbs/<db>/colls/<container>` requests.

Not verifiable offline: that Azure accepts the deployment (`terraform apply`), the real Kudu, Functions host and Web
PubSub extension behaviour, and the live Cosmos DB gateway (query plans and continuation conventions follow the
documented formats, as the provider's contract tests do).

## Differences from TypeScript

Application:

* **Entry point and registration.** `Main` calls `Booster.run`; the TypeScript `index.ts` exports are core's `UserApp`.
  Decorator functions are classes: `before` hooks (`ChangeCartItem.BeforeFn`/`BeforeFnV2`, `CartReadModel.BeforeFn`/
  `BeforeFnV2`, `CartTotalQuantity.BeforeFn`), custom authorizers (`LogSomething.CanLogSomething`,
  `Stock.MagicWord`, `SpecialReportsReadModel.SpecialReportAccess`, which reject with `NotAuthorizedError(<the
  TypeScript string>)`) and the join-key functions of `CarPurchasesReadModel` (`OwnerJoinKey` returns `null` for
  `SKIP`). The hook methods keep their TypeScript names (`beforeFn`, `beforeFnV2`) because the command metadata is part
  of an error message.
* **Data types.** Concepts are records (reducers return new instances instead of mutating) except `Cart`, a class
  because `MigratedCart extends Cart`, whose reducers mutate like TypeScript's. Numbers are `double`. Interfaces and
  `Record`s (`Items`, `ProductIDWithPrice`, `productDetails`, `warehouses`) are maps; `ProductType` (a string-literal
  union, a GraphQL `JSON` scalar) is an `Object` with the constants in `entities.ProductType`; `Picture.baseUrl` is a
  `URI`; `MediaValue` is a sealed interface (a GraphQL union). TypeScript default parameters are applied by compact
  constructors to `null` (an explicit GraphQL `null` therefore also gets the default). `Stock` keeps JavaScript's `NaN`
  for a missing warehouse (`Double.NaN`, written as `null`). `CartReadModel.testProperty` (a `@NonExposed` instance
  field set to 1) is a record component forced to 1. `SchemaReadModel.func0` is a `Consumer<String>` (stored as
  nothing, like a function).
* **`JSON.stringify(metadata)`** in the error messages is `common.JsonStringify`, which reproduces the TypeScript
  shape (functions omitted, `null` in arrays, key order) over the Java metadata, whose contents differ: `UUID`'s
  `importPath` is its Java package and method types are the Java ones (`Record<string, unknown>`, `void` instead of
  `Promise<CommandInput>`, `Promise<void>`). `ApplicationRegistrationTest` pins the resulting message.
* **Logging.** `console.log` is `System.out.println`; `CustomLogger` renders the optional parameters (passed as one
  array, the TypeScript quirk) as JSON instead of `util.inspect`.
* **GraphQL schema.** Two differences come from the framework, not from this port, and are pinned by
  `ApplicationRegistrationTest`: (1) list types are cached by name (`StringList`, first creation wins, as in
  TypeScript), but Java registers read models in class-path order and TypeScript in import order, so string lists
  are `[String!]` everywhere (TypeScript: `[String]`, from `CartReadModel.cartItemsIds?` created first), e.g.
  `SchemaReadModel.readonlyArray`; (2) Java has no `ReadonlyArray`, so `SchemaReadModel.readonlyArray` is a list and
  its filter is `StringArrayPropertyFilter` (TypeScript: `JSON`). `func0`, `unionWithUndefined` and the other
  TypeScript-only types are mapped to the Java types that give the same GraphQL types (see the class Javadoc).

Harness and suites:

* The application is launched from the module's build output with the sandbox as working directory; the sandbox
  holds the assets and the `.booster` data but no copy of the sources or package files, and there is no
  `npm install`/local-dependency override step (`deps-helper.ts` has no counterpart).
* Free ports instead of `3000`/`65529`; a readiness probe instead of `sleep 180`/`sleep 10000`; the PID file is kept,
  and the stop is checked (graceful exit) instead of a fire-and-forget `process.kill`. Start and stop of
  `LocalStartStopIT` run in one class against an application of its own, plus checks of what the phases imply.
* `CommandsIT > fails when trying to migrate a non-existent read model` expects `Resource Not Found`, which only
  Cosmos DB (Azure) produces: the local provider (TypeScript and Java) searches the empty set and answers
  `Migrated 0 NonExistentReadModel`. It is skipped on `LOCAL` and an extra test pins the local answer.
* `cart.integration.ts > throws an exception when before hook throws` passes in TypeScript when nothing is thrown;
  `CartIT` requires the error.
* `HealthIT > root health returns all indicators` and `> database health returns the indicator` expect `503` (an
  indicator that is not `UP` in the tree). Only a provider that honours the `status-code` of `healthRequestResult`
  answers it (Azure): the local `HealthController` (TypeScript and Java, `framework-provider-local-infrastructure`
  PARITY Q13) answers `200` to every successful health request. Both are skipped on `LOCAL`, and two extra tests run
  the same body assertions with the local `200`.
* `DeleteEventsIT > then the first event is set as deleted` also requires `deletedAt` to be non-null (TypeScript's
  `not.to.be.undefined` accepts `null`). Two extra tests pin what TypeScript leaves unchecked: a deleted event cannot be
  deleted again and is returned with the defaults of its class only (`createInstance(ProductCreated, {})`:
  `{productDetails: {}, productType: 'Other'}`), and `EntitiesIT > after the data migrations` checks the two
  `DataMigrationsReadModel` rows behind `count >= 2` (`CartIdDataMigrateV1`/`V2`, status `"1"` = `FINISHED`).
* The AWS gates of `delete.events`, `entities > Data migration`, `events > Query events ids` and `health` are
  `assumeFalse` in a `@BeforeAll`, after the TypeScript warning lines (AWS never runs here). Chai's `undefined` checks
  on JSON fields are key checks (`containsKey`/`doesNotContainKey`), so a `null` value counts as present, as in chai.
* `ReadModelsIT` (`read-models.integration.ts`): `should return exceptions thrown by before functions` also only
  asserts in a `catch` block in TypeScript; the port requires the error. The searches without `limit` (`projecting
  fields > with paginatedVersion true`, `query with fragments > should retrieve list of items`, `projecting
  calculated fields > ... via code`) expect `cursor.id === '100'` on Azure and Local, but only Azure's default page
  size produces it: the local provider (TypeScript `read-model-adapter.ts` and Java alike) returns
  `(limit ?? 1) + offset`, i.e. `'1'`. Those cursor assertions run on Azure only, and one additional local test per
  context pins `{ id: '1' }`. The `query using pagination` context re-awaits, in TypeScript, the already settled
  promises of earlier `beforeEach` runs; the port only runs the new mutations. The regex/iRegex/sort tests that
  TypeScript ends with an early `return` on other providers are JUnit assumptions (reported as skipped).
* Mocha runs the tests of a `describe` before its nested `describe`s (`read-models.integration.ts > projections`
  declares its car-domain `it`s after two nested blocks); JUnit runs a class's methods before its `@Nested` classes
  too, so the order is the TypeScript one.
* Additional end-to-end tests not in TypeScript: `ReadModelConcurrencyIT > With concurrent events of the same entity`
  (every concurrent event bumps the read model version once, no write lost to the optimistic concurrency check) and
  `ReadModelsIT > Query read models > filters, sorting and pages the TypeScript suite leaves uncovered`
  (`beginsWith`, `contains`/`in` on plain strings, `not`, `gt`/`gte`/`lt`/`lte`/`in` on numbers, ascending nested
  sort, walking every page of a sorted search with its cursors, and the before hook rewriting a list filter).
* `AppHelper` supports `AZURE` and `LOCAL` (AWS is not ported); `getProviderTestHelper(LocalTestHelper.Options)` lets
  the harness pass the ports and directory of the application it launched.
* The durability and recovery suites (`providerspecific.local.durability`) have no TypeScript counterpart: the
  TypeScript local provider loses unflushed writes on a crash, has no start-up recovery and finds damaged files only
  when a request touches them. Their `.booster` fixture in the TypeScript format is hand-written from the TypeScript
  sources (the build has no Node/NeDB), with the shapes `events-adapter.ts`, `read-model-registry.ts` and
  `web-socket-registry.ts` write.
* `Faker` is a small generator with faker 5's call names (the build allows no extra library): values have the
  formats the suites rely on, not faker's locale data.

## Not implemented

* The AWS environments (`development`, `production`) and every AWS-gated path (AWS is not ported).
* Suites other than the local start/stop lifecycle, `cart`, `commands`, `delete.events`, `entities`, `events`, `health`,
  `queries`, `read-models`, `read-model-concurrency` and the functionality suites: the remaining provider-specific
  suites are ported by the following stages on top of this layout. The Azure deploy/nuke suites and the load suite
  (disabled in TypeScript) need cloud resources.
* `onScheduledCommandHandlerError` and `onEventError` of the global error handler have no end-to-end test (TypeScript
  has none either): the application's scheduled command never fails, and `onEventError` needs a stored event whose
  type the application does not register, which no command can produce. `AppErrorHandlerTest` covers both hooks.
* Durability: read models that missed an event whose processing a crash interrupted (guarantee 4 of "Durability and
  recovery") only catch up with the entity's next event. Re-processing such events would make event handlers run
  twice (at-least-once instead of core's at-most-once), a semantic change of core that is not made here.

## Tests

* `mvn -B -pl framework-integration-tests -am verify`: `ApplicationRegistrationTest` (in-process boot: registry,
  configuration, GraphQL shapes, the error-handler metadata JSON) and the unit tests of the application's pure logic
  (`CommandHandlersTest`, `CartTest`, `StockTest`, `CartReadModelTest`, `ProjectionsTest`, `CartTotalQuantityTest`,
  `SchemaMigrationsTest`, `AppErrorHandlerTest`, `CustomLoggerTest`, `TokenVerifiersTest`) and of the harness
  (`HarnessTest`, `HelpersTest`) and of the functionality suites' helpers (`functionality.StoredDocumentsTest`).
* `-Pe2e`: `providerspecific.local.LocalStartStopIT` (spec 17 §3.1), `endtoend.CartIT` (§8.1) and
  `endtoend.CommandsIT` (§8.2), with the shared application; the functionality suites (§9.1–§9.9, see "Functionality
  suites") with the shared application, and `functionality.LoggedErrorsFunctionalityIT` with its own.
* `-Pe2e`: `endtoend.DeleteEventsIT` (§8.3), `endtoend.EntitiesIT` (§8.4), `endtoend.EventsIT` (§8.5),
  `endtoend.HealthIT` (§8.6) and `endtoend.QueriesIT` (§8.7), with the shared application.
* `-Pe2e`: `endtoend.ReadModelConcurrencyIT` (§8.8) and `endtoend.ReadModelsIT` (§8.9: by-id and list queries,
  before hooks, every filter operator, sorting, limits and cursors, field and calculated-field projections from GraphQL
  and code, fragments, projections with entity, array and read-model join keys, read-model authorization), with the
  shared application.
* `-Pe2e`, Java-only: the durability and recovery suites of `providerspecific.local.durability` (`GracefulRestartIT`,
  `CrashUnderLoadIT`, `TornWriteIT`, `RepeatedCrashCyclesIT`, `TypeScriptDataFilesIT`,
  `ScheduledCommandsAfterRestartIT`), each with an application of its own.
