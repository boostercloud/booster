/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Migrates, ToVersion } from '../../src/decorators'
import { Booster } from '../../src'
import { MigrationMetadata } from '@boostercloud/framework-types'

// Entities to test the annotations
class Product {}

class ProductV1 {}
class ProductV2 {
  public constructor(public field2: string, public field3: string) {}
}
class ProductV3 {
  public constructor(public field2: string, public field3: string, public field4: string) {}
}
class ProductV4 {
  public constructor(public field2: string, public field3: string, public field4: string, public field5: string) {}
}
class ProductV5 extends Product {} // This would be the current version

describe('the `ToVersion` decorator', () => {
  it('throws when a version smaller than 1 is specified', () => {
    expect(() => {
      // @ts-ignore: Unused class
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      class MigrateProduct {
        @ToVersion(1, { fromSchema: ProductV1, toSchema: ProductV2 })
        public changeField2(x: ProductV1): ProductV2 {
          return {} as any
        }
      }
    }).to.throw('Migration versions must always be greater than 1')
  })

  it('adds migrations as metadata to the migration class', () => {
    class MigrateProduct {
      @ToVersion(2, { fromSchema: ProductV1, toSchema: ProductV2 })
      public changeField2(x: ProductV1): ProductV2 {
        return {} as any
      }

      @ToVersion(3, { fromSchema: ProductV2, toSchema: ProductV3 })
      public changeField3(x: ProductV2): ProductV3 {
        return {} as any
      }
    }

    const expectedMetadata: Array<MigrationMetadata> = [
      {
        migrationClass: MigrateProduct,
        methodName: 'changeField2',
        toVersion: 2,
        fromSchema: ProductV1,
        toSchema: ProductV2,
      },
      {
        migrationClass: MigrateProduct,
        methodName: 'changeField3',
        toVersion: 3,
        fromSchema: ProductV2,
        toSchema: ProductV3,
      },
    ]

    const gotMetadata = Reflect.getMetadata('booster:migrationsMethods', MigrateProduct)

    expect(gotMetadata).to.be.deep.equal(expectedMetadata)
  })
})

describe('the `Migrates` annotation', () => {
  afterEach(() => {
    Booster.configure('test', (config) => {
      config.appName = ''
      for (const propName in config.migrations) {
        delete config.migrations[propName]
      }
    })
  })

  it('adds several migrations correctly', () => {
    @Migrates(Product)
    class MigrateProductFrom1To3 {
      @ToVersion(2, { fromSchema: ProductV1, toSchema: ProductV2 })
      public changeField2(x: ProductV1): ProductV2 {
        return {} as any
      }

      @ToVersion(3, { fromSchema: ProductV2, toSchema: ProductV3 })
      public changeField3(x: ProductV2): ProductV3 {
        return {} as any
      }
    }

    @Migrates(Product)
    class MigrateProductFrom3To5 {
      @ToVersion(5, { fromSchema: ProductV4, toSchema: ProductV5 })
      public changeField5(x: ProductV4): ProductV5 {
        return {} as any
      }

      @ToVersion(4, { fromSchema: ProductV3, toSchema: ProductV4 })
      public changeField4(x: ProductV3): ProductV4 {
        return {} as any
      }
    }

    Booster.configure('test', (config) => {
      expect(Object.keys(config.migrations).length).to.be.equal(1)
      const productMigrations = config.migrations[Product.name]
      expect(productMigrations.size).to.be.equal(4)
      expect(productMigrations.get(2)).to.be.deep.equal({
        migrationClass: MigrateProductFrom1To3,
        methodName: 'changeField2',
        toVersion: 2,
        fromSchema: ProductV1,
        toSchema: ProductV2,
      })
      expect(productMigrations.get(3)).to.be.deep.equal({
        migrationClass: MigrateProductFrom1To3,
        methodName: 'changeField3',
        toVersion: 3,
        fromSchema: ProductV2,
        toSchema: ProductV3,
      })
      expect(productMigrations.get(4)).to.be.deep.equal({
        migrationClass: MigrateProductFrom3To5,
        methodName: 'changeField4',
        toVersion: 4,
        fromSchema: ProductV3,
        toSchema: ProductV4,
      })
      expect(productMigrations.get(5)).to.be.deep.equal({
        migrationClass: MigrateProductFrom3To5,
        methodName: 'changeField5',
        toVersion: 5,
        fromSchema: ProductV4,
        toSchema: ProductV5,
      })
    })
  })

  it('throws when a migration is duplicated', () => {
    @Migrates(Product)
    // @ts-ignore: Unused class
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    class MigrateProductFrom1To3 {
      @ToVersion(2, { fromSchema: ProductV1, toSchema: ProductV2 })
      public changeField2(x: ProductV1): ProductV2 {
        return {} as any
      }

      @ToVersion(3, { fromSchema: ProductV2, toSchema: ProductV3 })
      public changeField3(x: ProductV2): ProductV3 {
        return {} as any
      }
    }

    expect(() => {
      @Migrates(Product)
      // @ts-ignore: Unused class
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      class MigrateProductFrom3To5 {
        @ToVersion(3, { fromSchema: ProductV2, toSchema: ProductV3 })
        public changeField5(x: ProductV2): ProductV3 {
          return {} as any
        }
      }
    }).to.throw(/There is an already defined migration for version 3/)
  })

  it('throws when no migration methods are found', () => {
    expect(() => {
      @Migrates(Product)
      // @ts-ignore: Unused class
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      class MigrateProductFrom3To5 {
        public changeField5(x: Record<string, any>): string {
          return {} as any
        }
      }
    }).to.throw(/No migration methods found in this class/)
  })
})
