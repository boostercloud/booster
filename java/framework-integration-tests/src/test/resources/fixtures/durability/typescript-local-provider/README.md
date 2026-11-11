# `.booster` directory in the TypeScript local provider's format

The data files a TypeScript Booster application (the `my-store` app of `packages/framework-integration-tests`) leaves
in `.booster` when it runs on `@boostercloud/framework-provider-local` (`@seald-io/nedb` 4.0.2): one JSON document per
line, `_id` last, a `$$indexCreated` line per persisted index, appended (not compacted) read-model updates sharing an
`_id`, and a `$$deleted` line for a removed read model. The documents have the shapes the TypeScript code writes:

* `events.json`: events as `RegisterHandler.wrapEvent` builds them plus `createdAt` (`events-adapter.ts#storeEvents`,
  `currentUser` only for signed-in users) and snapshots as `EventStore.entityReducer` builds them plus `createdAt` and
  `persistedAt` (`events-adapter.ts#storeSnapshot`). A counter with three increments and a snapshot after each, a cart
  of a signed-in user with two items, one run of the scheduled command `CheckCartCount`, and a second counter whose two
  events were stored but never projected (the TypeScript process stopped first; it has no recovery).
* `read_models.json`: the `uniqueKey` index (`read-model-registry.ts#loadDatabaseIfNeeded`) and the read models
  `{typeName, value, uniqueKey}` with `boosterMetadata` as `ReadModelStore.store` writes it (`version`,
  `schemaVersion`, `lastUpdateAt`, `lastProjectionInfo`); `CartReadModel` keeps its `testProperty` (an instance field
  JSON.stringify includes).
* `connections.json`, `subscriptions.json`: the TTL index of `web-socket-registry.ts#addIndexes` (2 days) and the rows of
  a websocket client that is gone.

Used by `TypeScriptDataFilesIT` (`com.boostercloud.integration.providerspecific.local.durability`).
