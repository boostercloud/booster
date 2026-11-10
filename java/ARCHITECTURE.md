# Booster for Java — architecture and porting rules

This document is the contract every module of the Java port follows. It explains how each
TypeScript concept maps to Java 27, so that code written in different modules fits together.

The TypeScript sources under `packages/` remain the source of truth for behaviour. The Java
port lives entirely under `java/` and mirrors the TypeScript package layout one-to-one.

## 1. Module map

| TypeScript package | Java module (directory = artifact suffix) | Base package |
| --- | --- | --- |
| `metadata-booster` | `metadata-booster` (`booster-metadata`) | `com.boostercloud.metadata` |
| `framework-types` | `framework-types` (`booster-framework-types`) | `com.boostercloud.framework.types` |
| `framework-common-helpers` | `framework-common-helpers` | `com.boostercloud.framework.common` |
| `framework-core` | `framework-core` | `com.boostercloud.framework.core` |
| `framework-provider-local` | `framework-provider-local` | `com.boostercloud.framework.provider.local` |
| `framework-provider-local-infrastructure` | `framework-provider-local-infrastructure` | `com.boostercloud.framework.provider.local.infrastructure` |
| `framework-provider-azure` | `framework-provider-azure` | `com.boostercloud.framework.provider.azure` |
| `framework-provider-azure-infrastructure` | `framework-provider-azure-infrastructure` | `com.boostercloud.framework.provider.azure.infrastructure` |
| `application-tester` | `application-tester` | `com.boostercloud.application.tester` |
| `cli` | `cli` (`booster-cli`) | `com.boostercloud.cli` |
| `rocket-webhook-*` (external repo) | `rockets/rocket-webhook` | `com.boostercloud.rocket.webhook.{types,core,local,azure}` |
| `rocket-file-uploads-*` (external repo) | `rockets/rocket-file-uploads` | `com.boostercloud.rocket.fileuploads.{types,core,local,azure}` |
| `framework-integration-tests` | `framework-integration-tests` | `com.boostercloud.integration` (sample app + end-to-end tests) |
| `framework-integration-tests/integration/provider-unaware/cli` | `cli-integration-tests` (`booster-cli-integration-tests`) | `com.boostercloud.cli.integration` (test code only: the `boost` CLI end to end) |

AWS packages are intentionally not ported.

Dependency direction (same as TypeScript):

```
metadata-booster  <-  framework-types  <-  framework-common-helpers  <-  framework-core
framework-types + framework-common-helpers  <-  provider-local, provider-azure      (providers do NOT depend on core)
provider-*  <-  provider-*-infrastructure
framework-core + providers  <-  rockets, cli (cli only needs types/helpers), framework-integration-tests
cli + framework jars (installed into an isolated test repository)  <-  cli-integration-tests
```

Inside each module, Java packages and class names mirror the TypeScript file tree:
`src/services/event-store.ts` → `…core.services.EventStore`; a TS module that only exports
functions becomes a `final` class with static methods named after the file
(`raw-events-parser.ts` → `RawEventsParser`).

## 2. Language and toolchain

* Java 27 (`--release 27`), Maven multi-module build, no preview features.
* Use modern Java where it fits: records, sealed interfaces, pattern matching for `switch`,
  text blocks, `var`, virtual threads.
* Third-party libraries are limited to the ones declared in the parent `pom.xml`
  (Jackson 2, graphql-java, Jetty 12, picocli, mustache, azure-functions-java-library; JUnit 6,
  AssertJ, Mockito and Awaitility for tests). Do not add others without a strong reason.
* Everything else uses the JDK: `java.net.http` for HTTP clients, `java.security` for JWT/RSA,
  `java.nio.file` for storage, `java.util.concurrent` for scheduling.

## 3. Asynchrony: Promises become blocking calls on virtual threads

TypeScript `async` functions returning `Promise<T>` become ordinary blocking Java methods returning
`T` (`Promise<void>` → `void`). Request handling, event dispatch and scheduled work run on virtual
threads, so blocking is cheap. `Promise.all([...])` becomes
`Promises.allSettledAndCheck(...)`/a virtual-thread executor (see `framework-common-helpers`);
keep the *ordering and failure semantics* of the original (`allSettled` vs fail-fast).

Rejections become unchecked exceptions. Never declare checked exceptions on framework APIs.

## 4. JSON and dynamic values

* The single shared `ObjectMapper` is `BoosterJson.mapper()` (framework-types). Use it everywhere.
* "Plain JSON objects" from TypeScript (values read from a database, request bodies, filters) are
  represented as untyped Java trees: `Map<String, Object>`, `List<Object>`, `String`, `Number`,
  `Boolean`, `null`. Call these **raw values**.
* Envelopes are Java records with exactly the TypeScript field names (`entityID`, `entityTypeName`,
  `requestID`, `createdAt`, …) so persisted documents and wire payloads have the same shape as the
  TypeScript implementation. `undefined` fields are omitted from JSON (`@JsonInclude(NON_NULL)`).
  Records expose a `toBuilder()`/`Builder` for copy-with-changes (the TS code uses object spread).
* An envelope `value` is `Object`: it is a user class instance when created by user code and a raw
  value when read from storage. Core turns raw values into instances with
  `Instances.createInstance(Class<T>, Object raw)` exactly where the TypeScript calls `createInstance`.
* Timestamps in envelopes stay ISO-8601 strings with millisecond precision and a `Z` suffix
  (`2024-01-31T10:20:30.123Z`), produced by `Dates.nowIso()`; providers rely on their lexical order.
* `UUID` is Booster's own value type `com.boostercloud.framework.types.concepts.UUID` (a string
  wrapper, any string is a valid id, `UUID.generate()` creates a random one). It serialises as a
  JSON string and maps to the GraphQL `ID` scalar. It is **not** `java.util.UUID`.
* `BoosterJson.mapper()` reproduces `JSON.stringify`: absent values (`null`, empty `Optional`) are
  omitted from objects (but kept inside raw maps), objects are serialised from their **fields**
  (any visibility, `transient` excluded; records from their components), never from getters;
  `double`/`float` are written like JavaScript numbers (`2.0` → `2`, non-finite → `null`); a
  `BoosterError` is written as `{"data", "code"}` and any other `Throwable` as `{"message"}`.
  Unknown properties are ignored when reading. `BoosterJson.toRaw(x)` is a real JSON round trip
  (numbers come back as `JSON.parse` would give them), so raw values compare equal regardless of how
  they were produced.
* `Instances.createInstance` **binds** recursively (TypeScript only does a shallow
  `Object.assign`): records through their canonical constructor, classes through the constructor
  whose parameter names match their fields (`-parameters`) or their no-argument constructor plus
  field assignment; nested types, collections, maps, enums, dates, `Optional` and sealed
  hierarchies are converted to the declared generic types; types with Jackson creator/value
  annotations go through `BoosterJson`. Missing properties become `null`/defaults/empty
  `Optional`s, unknown raw keys are dropped.
* Read models cross the provider SPI as raw documents (`Map<String, Object>` including the
  `boosterMetadata` key). `ProviderReadModelsLibrary.search` always returns a
  `ReadModelListResult` (TypeScript returns a bare array when not paginated); core returns its
  `items` for non-paginated searches.
* Environment variables are read through `EnvironmentVariables.get` (an in-process override table,
  then `System.getenv`), the replacement of reading and assigning `process.env`.

## 5. Decorators become annotations + reflection

TypeScript decorators (`@Command`, `@Event`, `@Entity`, `@Reduces`, `@ReadModel`, `@Projects`, …)
become Java annotations with the same names in `com.boostercloud.framework.core.decorators`.
There is no compile-time step: `Booster.start(AppClass.class)` scans the package of the given class
(the equivalent of importing every file under `dist/`), and each annotation's registrar writes the
same metadata into `BoosterConfig` that the TypeScript decorator writes.

Rules:

* Concept names are `Class.getSimpleName()` (TypeScript uses `class.name`).
* User classes may be **records or regular classes**. Property metadata comes from record
  components, or from the declared instance fields of a class (in declaration order).
* Handlers keep the TypeScript convention of **static methods**:
  `public static void handle(MyCommand command, Register register)`.
* Decorator arguments that are functions in TypeScript (custom authorizers, `before` hooks,
  join-key functions) become `Class<? extends FunctionalInterface>` annotation attributes; Booster
  instantiates them with their no-arg constructor.
* `authorize: 'all'` is written `authorize = All.class`; a list of roles is
  `authorize = {Admin.class, User.class}`; omitting it denies access (same default as TypeScript).
* Nullability: a property is optional (GraphQL nullable) when its type is `java.util.Optional<T>`
  or it carries an annotation whose simple name is `Nullable`. Everything else is required,
  matching TypeScript where properties are required unless marked `?`.
* Type mapping used for GraphQL and metadata: `String`→String; every Java numeric type →Number
  (GraphQL `Float`, as in TypeScript where all numbers are `Float`); `boolean`→Boolean; enums→Enum;
  `List`/`Set`/arrays→Array; `UUID`→`ID`; `java.time.Instant`/`java.util.Date`→`Date`;
  `Map`/`Object`→`JSON`; other classes/records→Class (object types).
* `com.boostercloud.metadata.ClassMetadata.of(cls)` (the replacement of
  `Reflect.getMetadata('booster:typeinfo', cls)`) gives each `TypeMetadata` a TypeScript-compatible
  `name` (`string`, `number`, `boolean`, `Date`, `UUID`, `Cart`, `string[]`, `Record<string, Cart>`,
  `Page<Cart>`, `Promise<boolean>`, `unknown`, `void`) and the TypeScript `typeGroup`/`typeName`
  rules, so the GraphQL generator ports its naming rules unchanged: dates are `Interface` with no
  `typeName` (matched by name), sealed interfaces/classes are `Union` (members = permitted
  subclasses, name = the sealed type), `Map`s are the `Type` alias `Record<K, V>`, instantiated
  generic classes are `Object`, futures are `Promise<T>`, functional interfaces are `Function`,
  other interfaces `Interface`, unresolved type variables `Other`.
* Field order is deterministic: record components, or declared fields root superclass first, in
  **class-file declaration order** (read with `java.lang.classfile`), then calculated fields.
  Calculated fields (TypeScript getters, `isGetAccessor = true`) are no-argument instance methods
  annotated with an annotation whose simple name is `CalculatedField`; its `dependsOn` attribute
  fills `PropertyMetadata.dependencies`. Methods are the class's own declared methods (static and
  instance) in declaration order, without record accessors and generated members.
* Nullability details: `@Nullable` on a property behaves like TypeScript's `?` and therefore also
  marks the type arguments nullable (`@Nullable List<String>` → `[String]`), `Optional<T>` behaves
  like `T | undefined` and does not (`Optional<List<String>>` → `[String!]`), and a type-use
  `@Nullable` on a type argument (`List<@Nullable String>`) marks only that argument.
* External types (exposed as `JSON`, not expanded): JDK classes and the packages in
  `ExternalTypes` (`com.boostercloud.framework.types.`, `com.boostercloud.framework.common.`,
  `com.boostercloud.metadata`, Jackson, graphql-java; applications can add more). This replaces
  TypeScript's "imported from a package" rule; `TypeMetadata.isExternal()` is the
  `isExternalType` predicate.
* `ProjectionResult<T>` is a sealed interface whose permitted types are `ProjectionResult.Upsert`
  and the enum `ReadModelAction`: `return ReadModelAction.DELETE;` from a method declared
  `ProjectionResult<CartReadModel>` compiles (with an unchecked-conversion warning);
  `ProjectionResult.delete()` / `ProjectionResult.nothing()` are the warning-free forms.

## 6. Configuration, start-up and entry points

* `Booster.configure("local", config -> { … })` works as in TypeScript. Because Java has no
  import-time side effects, configuration blocks live in classes implementing
  `BoosterConfigurator` (found by the package scan) whose `configure()` method calls
  `Booster.configure(...)`.
* The current environment comes from the `BOOSTER_ENV` environment variable (or the
  `booster.env` system property, for tests and embedded use).
* `config.providerPackage` is a Java package name. Booster loads `<providerPackage>.Provider` and
  calls its static `provider(List<RocketDescriptor>)` (TypeScript: `require(pkg).Provider(rockets)`).
  A provider finds its infrastructure in `<providerPackage>.infrastructure.Infrastructure`
  via the static `infrastructure(List<RocketDescriptor>)` (TypeScript: `require(pkg + '-infrastructure')`).
* The functions a TypeScript app exports from `src/index.ts` (`boosterServeGraphQL`,
  `boosterEventDispatcher`, `boosterNotifySubscribers`, `boosterTriggerScheduledCommand`,
  `boosterHealth`, `boosterRocketDispatcher`, …) are the `UserApp` interface. Core implements it and
  registers the instance in `UserAppRegistry` (framework-types) during `Booster.start`; providers
  obtain it from there (TypeScript: `require(dist/index.js)`).
* A Booster application is a normal runnable program:
  `public static void main(String[] a) { Booster.run(Main.class, a); }`.
  `run` calls `Booster.start` and then executes the requested action through the provider's
  infrastructure: `start [--port N]`, `synth`, `deploy`, `nuke`. The `boost` CLI builds the user
  project with Maven and launches this main class.

## 6b. User-facing API (binding for core, CLI templates and the sample app)

This is how a Booster application looks in Java. Core must accept exactly this, the CLI
generators must emit exactly this, and the sample app uses it. Names mirror the TypeScript API.

```java
// Commands: annotation on a record (or class); static handle(command, register)
@Command(authorize = All.class, before = {ValidateInput.class})
public record ChangeCartItem(UUID cartId, UUID productId, double quantity, @NonExposed Double test) {
  public static void handle(ChangeCartItem command, Register register) {
    register.events(new CartItemChanged(command.cartId(), command.productId(), command.quantity()));
  }
}
// A command handler may also return a value (exposed as the mutation result; void => true).

// Events: entityID() names the entity instance the event belongs to
@Event
public record CartItemChanged(UUID cartId, UUID productId, double quantity) {
  public UUID entityID() { return cartId; }
}

// Notifications (events that do not belong to an entity)
@Notification(topic = "cart-abandoned")
public record CartAbandoned(@PartitionKey UUID cartId) {}

// Entities: static reducers; the current snapshot is null for the first event
@Entity(authorizeReadEvents = All.class)
public record Cart(UUID id, List<CartItem> cartItems, Optional<Address> shippingAddress, int checks) {
  @Reduces(CartItemChanged.class)
  public static Cart changeItem(CartItemChanged event, Cart currentCart) { ... }
}

// Read models: @Projects is @Repeatable; joinKey is the entity property name.
// A projection returns the new read model, or ReadModelAction.DELETE / ReadModelAction.NOTHING
// (declare the return type as ProjectionResult<T>; T itself implements nothing special).
@ReadModel(authorize = All.class, before = {CheckAccess.class})
public record CartReadModel(UUID id, List<CartItem> cartItems, int checks) {
  @Projects(entity = Cart.class, joinKey = "id")
  public static ProjectionResult<CartReadModel> updateWithCart(Cart cart, CartReadModel old) {
    return ProjectionResult.of(new CartReadModel(cart.id(), cart.cartItems(), cart.checks()));
  }
  // Join-key functions (TS: joinKey as a function) use joinKeyFunction = MyJoinKey.class
  // Un-projections: @Projects(entity = X.class, joinKey = "y", unProject = "methodName")
}

// Queries: static handle(query, queryInfo) returning any JSON-serialisable value
@Query(authorize = All.class)
public record CartTotalQuantity(UUID cartId) {
  public static double handle(CartTotalQuantity query, QueryInfo queryInfo) { ... }
}

// Event handlers: a class with a static handle(event, register)
@EventHandler(CartItemChanged.class)
public class LogCartItemChanged {
  public static void handle(CartItemChanged event, Register register) { ... }
}
@GlobalEventHandler // receives every event as EventInterface/raw value
public class LogAll { public static void handle(Object event, Register register) { ... } }

// Scheduled commands
@ScheduledCommand(minute = "0/5")
public class CheckCarts { public static void handle(Register register) { ... } }

// Roles
@Role(auth = @RoleAuth(signUpMethods = {"email"}))
public class Admin {}

// Configuration: any class implementing BoosterConfigurator in the scanned package
public class Config implements BoosterConfigurator {
  @Override public void configure() {
    Booster.configure("local", config -> {
      config.appName = "my-store";
      config.providerPackage = "com.boostercloud.framework.provider.local";
    });
  }
}

// Entry point
public class Main {
  public static void main(String[] args) { Booster.run(Main.class, args); }
}
```

Reading from code: `Booster.readModel(CartReadModel.class).filter(Map.of("id", Map.of("eq", id))).search()`
(filters are the same JSON shape as TypeScript `FilterFor`), `Booster.entity(Cart.class, id)`,
`Booster.events(parameters)`, `Booster.entitiesIDs(...)`, `Booster.deleteEvent(...)`.

## 7. Errors and logging

* TypeScript error classes keep their names and messages and extend `BoosterError`
  (an unchecked exception with a `code` and optional `data`).
* `Logger` (`debug/info/warn/error`) and `getLogger(config, location, prefix)` are ported as-is;
  the default implementation writes to the console honouring `config.logLevel`. No SLF4J in
  framework code (Jetty's own logging is routed to a no-op binding).
* Concretely: `BoosterError extends RuntimeException`; `code()` defaults to the runtime class's
  simple name. `BoosterErrors.httpStatusCodeFor(error)` matches the **exact simple class name**
  (a subclass of `NotFoundError` maps to 500, as in TypeScript); `TypeLevel.toClassTitle(error)`
  builds HTTP error titles. Places where TypeScript throws a plain `Error` throw
  `IllegalStateException`/`IllegalArgumentException` with the same message.
* Error names (`retryWithBackoff`'s `nonRetryableErrors`/`retryableErrors`, log lines) are simple
  class names (`BoosterErrors.errorName`); TypeScript uses `error.name`, which Booster errors never
  set. `BoosterErrors.errorToString` renders errors like JavaScript (`"Name: message"`).
* `com.boostercloud.framework.common.Loggers.getLogger(config, location, prefix)` passes the prefix
  (`[Booster]|Location: `) as the first argument of the sink, followed by the caller's arguments
  unformatted; levels are a snapshot at creation and `error` is always on. The sink is
  `config.logger` or `ConsoleLogger` (debug/info to stdout, warn/error to stderr).

## 8. Persistence

* Local provider: an embedded, file-based document store that keeps the NeDB layout of the
  TypeScript provider (one append-only JSON-lines file per collection under `.booster/`), with
  crash-safe appends, recovery of a truncated tail on start-up, compaction and in-memory indexes.
* Azure provider: Cosmos DB, Web PubSub and Event Hubs are reached through small port interfaces.
  The production adapters use the Azure REST APIs over `java.net.http`; contract tests pin the
  requests they produce and the Terraform/function definitions the infrastructure module emits.
  Nothing is deployed by the test suite.

## 9. Testing

* Unit tests port the TypeScript unit tests of each package (JUnit 6 + AssertJ; Mockito replaces
  sinon). Test class names follow the TS test file: `event-store.test.ts` → `EventStoreTest`.
* `framework-integration-tests` contains the sample application (the cart demo used by the
  TypeScript integration suite) and end-to-end tests that start the real application process on
  the local provider and talk to it over HTTP and WebSocket, including restart and crash recovery.
* `cli-integration-tests` runs the real `boost` launcher in sandbox projects that the CLI builds with Maven
  against this reactor's framework jars (installed into `target/booster-repo`, never into `~/.m2`).
* `mvn verify` from `java/` builds everything and runs every unit and in-process test; the failsafe `*IT`
  suites of `framework-integration-tests` and `cli-integration-tests` run with the `e2e` profile
  (`mvn -Pe2e verify`).

## 10. What "faithful" means

Keep observable behaviour identical: GraphQL schema shape and naming, request/response payloads,
error messages and codes, persisted document shapes, resource names, ordering and retry rules.
Where Java forces a difference (annotations instead of decorators, static typing, blocking
calls), keep the difference at the edge and document it in the module README under
"Differences from TypeScript".
