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
* `AppHelper` supports `AZURE` and `LOCAL` (AWS is not ported); `getProviderTestHelper(LocalTestHelper.Options)` lets
  the harness pass the ports and directory of the application it launched.
* `Faker` is a small generator with faker 5's call names (the build allows no extra library): values have the
  formats the suites rely on, not faker's locale data.

## Not implemented

* The AWS environments (`development`, `production`) and every AWS-gated path (AWS is not ported).
* Suites other than the local start/stop lifecycle, `cart`, `commands` and the functionality suites: the remaining
  end-to-end and provider-specific suites are ported by the following stages on top of this layout. The Azure
  deploy/nuke suites and the load suite (disabled in TypeScript) need cloud resources.
* `onScheduledCommandHandlerError` and `onEventError` of the global error handler have no end-to-end test (TypeScript
  has none either): the application's scheduled command never fails, and `onEventError` needs a stored event whose
  type the application does not register, which no command can produce. `AppErrorHandlerTest` covers both hooks.

## Tests

* `mvn -B -pl framework-integration-tests -am verify`: `ApplicationRegistrationTest` (in-process boot: registry,
  configuration, GraphQL shapes, the error-handler metadata JSON) and the unit tests of the application's pure logic
  (`CommandHandlersTest`, `CartTest`, `StockTest`, `CartReadModelTest`, `ProjectionsTest`, `CartTotalQuantityTest`,
  `SchemaMigrationsTest`, `AppErrorHandlerTest`, `CustomLoggerTest`, `TokenVerifiersTest`) and of the harness
  (`HarnessTest`, `HelpersTest`) and of the functionality suites' helpers (`functionality.StoredDocumentsTest`).
* `-Pe2e`: `providerspecific.local.LocalStartStopIT` (spec 17 §3.1), `endtoend.CartIT` (§8.1) and
  `endtoend.CommandsIT` (§8.2), with the shared application; the functionality suites (§9.1–§9.9, see "Functionality
  suites") with the shared application, and `functionality.LoggedErrorsFunctionalityIT` with its own.
