/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */

import { expect } from './expect'
import { BoosterConfig, ReadModelInterface, SchemaMigrationMetadata, UUID } from '@boostercloud/framework-types'
import { ReadModelSchemaMigrator } from '../src/read-model-schema-migrator'
import 'mocha'
import { fake, replaceGetter } from 'sinon'

class TestConceptV1 {
  public constructor(readonly id: UUID, readonly field1: string) {}
}

class TestConceptV2 {
  public constructor(readonly id: UUID, readonly field1: string, readonly field2: number) {}
}

class TestConceptV3 {
  public constructor(readonly id: UUID, readonly field1: string, readonly field2: number, readonly field3: string) {}
}

class TestConceptMigration {
  public addField2(old: TestConceptV1): TestConceptV2 {
    return new TestConceptV2(old.id, old.field1, 2)
  }

  public addField3(old: TestConceptV2): TestConceptV3 {
    return new TestConceptV3(old.id, old.field1, old.field2, 'default')
  }
}

describe('ReadModelSchemaMigrator', () => {
  const migrations = new Map<number, SchemaMigrationMetadata>()
  migrations.set(2, {
    fromSchema: TestConceptV1,
    toSchema: TestConceptV2,
    methodName: 'addField2',
    migrationClass: TestConceptMigration,
    toVersion: 2,
  })
  migrations.set(3, {
    fromSchema: TestConceptV2,
    toSchema: TestConceptV3,
    methodName: 'addField3',
    migrationClass: TestConceptMigration,
    toVersion: 3,
  })
  const config = new BoosterConfig('test')

  describe('migrate', () => {
    config.schemaMigrations['TestConcept'] = migrations
    const migrator = new ReadModelSchemaMigrator(config)

    it('throws when the schemaVersion of the concept to migrate is lower than 1', async () => {
      const toMigrate: ReadModelInterface = {
        id: 'id',
        boosterMetadata: {
          version: 0,
          schemaVersion: 0,
        },
      }

      await expect(migrator.migrate(toMigrate, 'TestConcept')).to.be.rejectedWith(
        /Received an invalid schema version value, 0, for TestConcept/
      )
    })

    it('throws when the schemaVersion of the concept to migrate is higher than the current version', async () => {
      const toMigrate: ReadModelInterface = {
        id: 'id',
        boosterMetadata: {
          version: 0,
          schemaVersion: 4,
        },
      }

      await expect(migrator.migrate(toMigrate, 'TestConcept')).to.be.rejectedWith(
        /The current schema version of TestConcept is 3, which is lower than the received version 4/
      )
    })

    it('does not migrate when the received schemaVersion is the same as the current schemaVersion', async () => {
      const toMigrate: ReadModelInterface = {
        id: 'id',
        boosterMetadata: {
          version: 0,
          schemaVersion: 3,
        },
      }

      expect(await migrator.migrate(toMigrate, 'TestConcept')).to.equal(toMigrate)
    })

    it('migrates when the received schemaVersion is lower than the current one', async () => {
      const toMigrate: ReadModelInterface = {
        id: 'id',
        boosterMetadata: {
          version: 0,
          schemaVersion: 1,
        },
        field1: 'test-field1',
      }

      const expected: ReadModelInterface = {
        id: 'id',
        boosterMetadata: {
          version: 0,
          schemaVersion: 3,
        },
        field1: 'test-field1',
        field2: 2,
        field3: 'default',
      }

      const got = (await migrator.migrate(toMigrate, 'TestConcept')) as TestConceptV3
      expect(got).not.to.be.equal(toMigrate) // This checks the reference is not the same (i.e. a different object is returned)
      expect(got).to.be.deep.equal(expected)
    })
  })

  describe('migrateAll', () => {
    it('returns 0 when there are not ReadModels with schemaVersion less than expected', async () => {
      const config = new BoosterConfig('test')
      replaceGetter(config, 'provider', () => {
        return {
          readModels: {
            search: fake.returns({ items: [] }),
          },
        } as any
      })
      const migrator = new ReadModelSchemaMigrator(config)
      const number = await migrator.migrateAll('TestConcept')
      expect(number).to.be.deep.equal(0)
    })

    it('returns ReadModels with schemaVersion less than expected', async () => {
      const config = new BoosterConfig('test')
      replaceGetter(config, 'provider', () => {
        return {
          readModels: {
            search: fake.returns({ items: [{ id: 1 }, { id: 2 }, { id: 3 }], count: 3, cursor: { id: 10 } }),
            store: fake.resolves(''),
          },
        } as any
      })
      config.schemaMigrations['TestConcept'] = migrations
      const migrator = new ReadModelSchemaMigrator(config)
      const number = await migrator.migrateAll('TestConcept')
      expect(number).to.be.deep.equal(3)
    })
  })
})
