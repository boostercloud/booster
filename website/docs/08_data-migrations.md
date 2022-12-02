# Migrations

## Schema migrations

Booster handles classes annotated with `@Migrates` as **Schema migrations**. The migration process will update an existing object
from one version to the next one.

For example, to migrate a `Product` entity from version 1 to version 2 we need the following migration class:

```typescript
@Migrates(Product)
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

The `ProductV1` class is the old version of the `Product` object. You can keep your old clases in the same migration file, for example:

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

## Data migrations

The decorator `@DataMigration` will indicate **Booster** that this class contains data migration code.

```typescript
@DataMigration({
  order: 2,
})
```

When the method `BoosterDataMigrations.run()` is call by the user, a new `BoosterDataMigrationStarted` event is emitted and **Booster** 
will check if there are pending migrations and, if so, run them in the order specified by the `order` value.

User should emit `BoosterDataMigrationFinished` manually at the end of each `DataMigration.start` method.

In `@DataMigration` classes, you can use `Booster.migrateEntity` method. This method will generate an internal event `BoosterEntityMigrated` before migrating the entity data.

This method will receive the old entity name, the old entity id and the new entity that we will be persisted. This way, you can migrate an entity id or rename it.

Example:

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
          await Booster.migrateEntity('Cart', validCart.id, newCart)
          return validCart.id
      })
    )

    register.events(new BoosterDataMigrationFinished('CartIdDataMigrateV2'))
  }
}
```
