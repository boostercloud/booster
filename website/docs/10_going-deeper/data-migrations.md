---
description: Learn how to migrate data in Booster
---

# Migrations

Migrations are a mechanism for updating or transforming the schemas of events and entities as your system evolves. This allows you to make changes to your data model without losing or corrupting existing data. There are two types of migration tools available in Booster: schema migrations and data migrations.

* **Schema migrations** are used to incrementally upgrade an event or entity from a past version to the next. They are applied lazily, meaning that they are performed on-the-fly whenever an event or entity is loaded. This allows you to make changes to your data model without having to manually update all existing artifacts, and makes it possible to apply changes without running lenghty migration processes.

* **Data migrations**, on the other hand, behave as background processes that can actively change the existing values in the database for existing entities and read models. They are particularly useful for data migrations that cannot be performed automatically with schema migrations, or for updating existing read models after a schema change.

Together, schema and data migrations provide a flexible and powerful toolset for managing the evolution of your data model over time.

## Schema migrations

Booster handles classes annotated with `@SchemaMigration` as **schema migrations**. The migration functions defined inside will update an existing artifact (either an event or an entity) from a previous version to a newer one whenever that artifact is visited. Schema migrations are applied to events and entities lazyly, meaning that they are only applied when the event or entity is loaded. This ensures that the migration process is non-disruptive and does not affect the performance of your system. Schema migrations are also performed on-the-fly and the results are not written back to the database, as events are not revisited once the next snapshot is written in the database.

For example, to upgrade a `Product` entity from version 1 to version 2, you can write the following migration class:

```typescript
@SchemaMigration(Product)
export class ProductMigration {
  @ToVersion(2, { fromSchema: ProductV1, toSchema: ProductV2 })
  public async changeNameFieldToDisplayName(old: ProductV1): Promise<ProductV2> {
    return new ProductV2(
      old.id,
      old.sku,
      old.name,
      old.description,
      old.price,
      old.pictures,
      old.deleted
    )
  }
}
```

Notice that we've used the `@ToVersion` decorator in the above example. This decorator not only tells Booster what schema upgrade this migration performs, it also informs it about the existence of a version, which is always an integer number. Booster will always use the latest version known to tag newly created artifacts, defaulting to 1 when no migrations are defined. This ensures that the schema of newly created events and entities is up-to-date and that they can be migrated as needed in the future.

The `@ToVersion` decorator takes two parameters in addition to the version: `fromSchema` and `toSchema`. The fromSchema parameter is set to `ProductV1`, while the `toSchema` parameter is set to `ProductV2`. This tells Booster that the migration is updating the `Product` object from version 1 (as defined by the `ProductV1` schema) to version 2 (as defined by the `ProductV2` schema).

As Booster can easily read the structure of your classes, the schemas are described as plain classes that you can maintain as part of your code. The `ProductV1` class represents the schema of the previous version of the `Product` object with the properties and structure of the `Product` object as it was defined in version 1. The `ProductV2` class is an alias for the latest version of the Product object. You can use the `Product` class here, there's no difference, but it's a good practice to create an alias for clarity.

It's a good practice to define the schema classes (`ProductV1` and `ProductV2`) as non-exported classes in the same migration file. This allows you to see the changes made between versions and helps to understand how the migration works:

```typescript
class ProductV1 {
  public constructor(
    public id: UUID,
    readonly sku: string,
    readonly name: string,
    readonly description: string,
    readonly price: Money,
    readonly pictures: Array<Picture>,
    public deleted: boolean = false
  ) {}
}

class ProductV2 extends Product {}
```

When you want to upgrade your artifacts from V2 to V3, you can add a new function decorated with `@ToVersion` to the same migrations class. You're free to structure the code the way you want, but we recommend keeping all migrations for the same artifact in the same migration class. For instance:

```typescript
@SchemaMigration(Product)
export class ProductMigration {
  @ToVersion(2, { fromSchema: ProductV1, toSchema: ProductV2 })
  public async changeNameFieldToDisplayName(old: ProductV1): Promise<ProductV2> {
    return new ProductV2(
      old.id,
      old.sku,
      old.name, // It's now called `displayName`
      old.description,
      old.price,
      old.pictures,
      old.deleted
    )
  }

  @ToVersion(3, { fromSchema: ProductV2, toSchema: ProductV3 })
  public async addNewField(old: ProductV2): Promise<ProductV3> {
    return new ProductV3(
      old.id,
      old.sku,
      old.displayName,
      old.description,
      old.price,
      old.pictures,
      old.deleted,
      42 // We set a default value to initialize this field
    )
  }
}
```

In this example, the `changeNameFieldToDisplayName` function updates the `Product` entity from version 1 to version 2 by renaming the `name` field to `displayName`. Then, `addNewField` function updates the `Product` entity from version 2 to version 3 by adding a new field called `newField` to the entity's schema. Notice that at this point, your database could have snapshots set as v1, v2, or v3, so while it might be tempting to redefine the original migration to keep a single 1-to-3 migration, it's usually a good idea to keep the intermediate steps. This way Booster will be able to handle any scenario.

### Migrating Multiple ReadModels at Once with "migrateAll"

ReadModelSchemaMigrator includes the method "migrateAll" that enables you to migrate all ReadModels of a specific type in a single step. 

To use the "migrateAll" method, simply pass the name of the ReadModel type you wish to migrate. The system will automatically migrate all ReadModels of that type that have a version less than the expected one.

In addition, the "migrateAll" method of Booster persists the changes made to the database. This ensures that the changes in the structure of the ReadModels are permanently stored in the database.

Furthermore, the "migrateAll" method also allows you to specify a second parameter that defines the size of the blocks in which the ReadModels are processed. If a number is passed, for example 10, the ReadModels will be read, migrated, and saved in blocks of 10.

Example:

```typescript
import { Booster, Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { ReadModelSchemaMigrator } from '@boostercloud/framework-core/dist/read-model-schema-migrator'

@Command({
  authorize: 'all',
})
export class MigrateAllReadModel {
  public constructor(readonly readModelName: string) {}

  public static async handle(command: MigrateAllReadModel, register: Register): Promise<string> {
    const readModelSchemaMigrator = new ReadModelSchemaMigrator(Booster.config)
    const result = await readModelSchemaMigrator.migrateAll(command.readModelName)
    return `Migrated ${result} ${command.readModelName}`
  }
}
```

## Data migrations

Data migrations can be seen as background processes that can actively update the values of existing entities and read models in the database. They can be useful to perform data migrations that cannot be handled with schema migrations, for example when you need to update the values exposed by the GraphQL API, or to initialize new read models that are projections of previously existing entities.

To create a data migration in Booster, you can use the `@DataMigration` decorator on a class that implements a `start` method. The `@DataMigration` decorator takes an object with a single parameter, `order`, which specifies the order in which the data migration should be run relative to other data migrations.

Data migrations are not run automatically, you need to invoke the `BoosterDataMigrations.run()` method from an event handler or a command. This will emit a `BoosterDataMigrationStarted` event, which will make Booster check for any pending migrations and run them in the specified order. A common pattern to be able to run migrations on demand is to add a special command, with access limited to an administrator role which calls this function.

Take into account that, depending on your cloud provider implementation, data migrations are executed in the context of a lambda or function app, so it's advisable to design these functions in a way that allow to re-run them in case of failures (i.e. lambda timeouts). In order to tell Booster that your migration has been applied successfully, at the end of each `DataMigration.start` method, you must emit a `BoosterDataMigrationFinished` event manually.

Inside your `@DataMigration` classes, you can use the `BoosterDataMigrations.migrateEntity` method to update the data for a specific entity. This method takes the old entity name, the old entity ID, and the new entity data as arguments. It will also generate an internal `BoosterEntityMigrated` event before performing the migration.

**Note that Data migrations are only available in the Azure provider at the moment.**

Here is an example of how you might use the `@DataMigration` decorator and the `Booster.migrateEntity` method to update the quantity of the first item in a cart (**Notice that at the time of writing this document, the method `Booster.entitiesIDs` used in the following example is only available in the Azure provider, so you may need to approach the migration differently in AWS.**):

```typescript
@DataMigration({
  order: 2,
})
export class CartIdDataMigrateV2 {
  public constructor() {}


  public static async start(register: Register): Promise<void> {
    const entitiesIdsResult = await Booster.entitiesIDs('Cart', 500, undefined)
    const paginatedEntityIdResults = entitiesIdsResult.items

    const carts = await Promise.all(
      paginatedEntityIdResults.map(async (entity) => await Booster.entity(Cart, entity.entityID))
    )
    return await Promise.all(
        carts.map(async (cart) => {
          cart.cartItems[0].quantity = 100
          const newCart = new Cart(cart.id, cart.cartItems, cart.shippingAddress, cart.checks)
          await BoosterDataMigrations.migrateEntity('Cart', validCart.id, newCart)
          return validCart.id
      })
    )

    register.events(new BoosterDataMigrationFinished('CartIdDataMigrateV2'))
  }
}
```

# Migrate from Previous Booster Versions

* To migrate to new versions of Booster, check that you have the latest development dependencies required:

```json
"devDependencies": {
    "rimraf": "^5.0.0",
    "@typescript-eslint/eslint-plugin": "4.22.1",
    "@typescript-eslint/parser": "4.22.1",
    "eslint": "7.26.0",
    "eslint-config-prettier": "8.3.0",
    "eslint-plugin-prettier": "3.4.0",
    "mocha": "10.2.0",
    "@types/mocha": "10.0.1",
    "nyc": "15.1.0",
    "prettier":  "2.3.0",
    "typescript": "4.5.4",
    "ts-node": "9.1.1",
    "@types/node": "15.0.2",
    "ts-patch": "3.1.2",
    "@boostercloud/metadata-booster": "0.30.2"
  },
```

## Migrate to Booster version 1.19.0

Booster version 1.19.0 requires updating your index.ts file to export the `boosterHealth` method. If you have an index.ts file created from a previous Booster version, update it accordingly. Example:

```typescript
import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterEventDispatcher,
  boosterServeGraphQL,
  boosterHealth,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
  boosterRocketDispatcher,
} from '@boostercloud/framework-core'

Booster.start(__dirname)

```

## Migrate to Booster version 2.3.0

Booster version 2.3.0 updates the url for the GraphQL API, sensors, etc. for the Azure Provider. New base url is `http://[resourcegroupname]apis.eastus.cloudapp.azure.com`

Also, Booster version 2.3.0 deprecated the Azure Api Management in favor of Azure Application Gateway. You don't need to do anything to migrate to the new Application Gateway.

Booster 2.3.0 provides an improved Rocket process to handle Rockets with more than one function. To use this new feature, you need to implement method `mountCode` in your `Rocket` class. Example:

```typescript
const AzureWebhook = (params: WebhookParams): InfrastructureRocket => ({
  mountStack: Synth.mountStack.bind(Synth, params),
  mountCode: Functions.mountCode.bind(Synth, params),
  getFunctionAppName: Functions.getFunctionAppName.bind(Synth, params),
})
```

This method will return an Array of functions definitions, the function name, and the host.json file. Example:

```typescript
export interface FunctionAppFunctionsDefinition<T extends Binding = Binding> {
  functionAppName: string
  functionsDefinitions: Array<FunctionDefinition<T>>
  hostJsonPath?: string
}
```

Booster 2.3.0 allows you to set the Azure App Service Plan used to deploy the main function app. Setting the `BOOSTER_AZURE_SERVICE_PLAN_BASIC` (default value false) environment variable to true will force the use of a basic service plan instead of the default consumption plan.

## Migrate to Booster version 2.6.0

Booster 2.6.0 allows you to set the Azure Application Gateway SKU used. Setting the `BOOSTER_USE_WAF` (default value false) environment variable to true will force the use of a WAF sku instead of the Standard sku.
