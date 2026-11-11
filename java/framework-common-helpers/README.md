# booster-framework-common-helpers (`com.boostercloud.framework.common`)

Java port of `packages/framework-common-helpers`: runtime helpers shared by core, the providers and the
CLI.

## Contents

| Java type | TypeScript file / symbol | Purpose |
| --- | --- | --- |
| `Instances` | `instances.ts`: `createInstance`, `createInstances`, `createInstanceWithCalculatedProperties` | bind raw values to records/classes; project selections with calculated fields; `convert(raw, Type)` |
| `Retrier` | `retrier.ts`: `retryIfError`, `retryWithBackoff`, `calculateRetryDelay` (+ `shouldRetryError`) | retries; injectable `Sleeper` and random source |
| `Promises`, `PromisesError` | `promises.ts` | `allSettledAndFulfilled` and friends on virtual threads |
| `SettledResult`, `PromiseUtils` | `promise_utils.ts`: `isRejected`, `isFulfilled` | outcome of a joined task |
| `Loggers` | `logger.ts`: `getLogger` | prefixed, level-gated loggers |
| `HttpService`, `HttpServiceError` | `http-service.ts`: `request`, `PostConfiguration`, `PostResult` | minimal GET/POST client |
| `RocketLoader`, `RocketBuilder` | `rocket-loader.ts` | load and validate infrastructure rockets |
| `RunCommand`, `RunCommandError` | `run-command.ts`: `runCommandAsync`, `runCommand` | run a command in a directory |
| `Groups` | `groups.ts`: `unique` | de-duplicate keeping order |

## Usage

```java
Logger logger = Loggers.getLogger(config, "EventStore#store");
logger.debug("Storing", envelope);                       // sink.debug("[Booster]|EventStore#store: ", "Storing", envelope)

Cart cart = Instances.createInstance(Cart.class, rawSnapshotValue);

List<EventEnvelope> stored = Retrier.retryWithBackoff(
    () -> config.provider().events().store(envelopes, config), config.eventStoreRetry, logger);

ReadModel updated = Retrier.retryIfError(
    tryNumber -> applyProjection(tryNumber), OptimisticConcurrencyUnexpectedVersionError.class, logger);

Promises.forEachAllSettledAndFulfilled(entityGroups, group -> process(group));   // waits for all, then throws PromisesError
```

## Differences from TypeScript

* **Instance creation is deep.** TypeScript's `createInstance` is `Object.assign(new Class(), raw)`:
  shallow, no conversion, unknown keys kept, nested values left as plain objects, dates left as
  strings. Java must build typed objects, so it binds recursively (nested records/classes,
  collections, maps, enums, `Optional`, ISO dates, sealed hierarchies, generic records). Unknown raw
  keys are dropped; missing ones become `null`/defaults. Classes are built through the constructor
  whose parameter names match their fields (`-parameters`) or their no-argument constructor;
  final fields that no constructor sets cannot be overwritten (an error explains how to fix the
  class). A raw value that already is an instance of the target class is returned unchanged.
* A number with a fraction (or `NaN`/infinite) bound to a whole-number property (`int`, `long`,
  `short`, `byte`, their boxes, `OptionalInt`/`OptionalLong`, `BigInteger`) fails with
  `Cannot convert 2.5 to int: the number is not an integer`; integral doubles such as `2.0` are
  accepted. TypeScript has one number type and keeps `2.5`; Jackson alone would truncate it silently,
  and every Java number is a GraphQL `Float` input. Classes bound by Jackson (`@JsonCreator`,
  `@JsonValue`, `@JsonTypeInfo`) keep Jackson's coercion.
* `createInstanceWithCalculatedProperties` returns an ordered `Map` (TypeScript a plain object) and
  follows the same rules for absent/`null` paths; since nested values are hydrated, calculated fields
  of nested objects are evaluated too (TypeScript sees plain objects there). Calculated fields
  returning `CompletionStage`/`Future` are waited for. `Optional`s (the Java form of `T | undefined`,
  also `OptionalInt`/`OptionalLong`/`OptionalDouble`) are read as their value, `null` when empty, at
  every level of the selection: nested objects, lists and leaves, like the plain values TypeScript
  reads (`Instances.unwrapOptional`). Malformed selections raise `IllegalArgumentException`s where
  TypeScript raises `TypeError`s.
* `Promises`: futures replace promises. `allSettledAndFulfilled` joins already started futures; the
  `run*`/`map*`/`forEach*` variants start each task on a virtual thread. They return the values
  instead of the settled-result records. Tasks run in parallel (JavaScript interleaves on one
  thread). `PromisesError.failedReasons()` are `Throwable`s; the message joins them with `". "`
  rendered as `Name: message`. Extra combinators: `allSettled`, `all`/`runAll` (fail fast), `start`.
* `Retrier`: errors are matched by simple class name (TypeScript `error.name`); `maxRetries <= 0`
  throws `IllegalArgumentException` (`retryWithBackoff`) or the "maximum number of retries" error
  ending in `undefined` (`retryIfError`) without running the logic; exhaustion of `retryIfError`
  keeps the last error as cause; `java.lang.Error`s are not retried by `retryWithBackoff`.
* `Loggers.getLogger` is named after the file but pluralised because `Logger` is the interface. A
  logger whose sink lacks a method cannot exist in Java, so there is no per-method console fallback.
* `HttpService` uses `java.net.http.HttpClient`: `Content-Length` is the UTF-8 byte length (TypeScript
  uses the string length), the timeout covers the time to the response headers, connection failures
  are `HttpServiceError`s with the cause.
* `RocketLoader` loads `<packageName>.Rocket`, a `RocketBuilder` with a public no-argument constructor,
  instead of the npm package's default export; the three error messages are TypeScript's (including
  "devDependencies", which means your Maven dependencies). The loading step is injectable for tests.
* `RunCommand.runCommand` throws `RunCommandError` (message = stderr, plus exit code and stdout)
  instead of rejecting with a string, and reports spawn failures as `UncheckedIOException`
  (TypeScript leaves them unhandled). When `runCommandAsync` echoes logs it consumes the process
  output.
* `Groups.unique` compares with `equals` (JavaScript compares objects by reference).
