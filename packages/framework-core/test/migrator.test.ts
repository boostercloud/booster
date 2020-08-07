/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */

import { expect } from './expect'
import { BoosterConfig, CommandEnvelope, Logger, MigrationMetadata } from '@boostercloud/framework-types'
import { Migrator } from '../src/migrator'

const logger: Logger = {
  debug() {},
  info() {},
  error() {},
}

class TestConceptV1 {
  public constructor(readonly field1: string) {}
}

class TestConceptV2 {
  public constructor(readonly field1: string, readonly field2: number) {}
}

class TestConceptV3 {
  public constructor(readonly field1: string, readonly field2: number, readonly field3: string) {}
}

class TestConceptMigration {
  public addField2(old: TestConceptV1): TestConceptV2 {
    return new TestConceptV2(old.field1, 2)
  }

  public addField3(old: TestConceptV2): TestConceptV3 {
    return new TestConceptV3(old.field1, old.field2, 'default')
  }
}

describe('Migrator', () => {
  const migrations = new Map<number, MigrationMetadata>()
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
  config.migrations['TestConcept'] = migrations
  const migrator = new Migrator(config, logger)

  describe('migrate', () => {
    it('throws when the version of the concept to migrate is lower than 1', () => {
      const toMigrate: CommandEnvelope = {
        requestID: 'requestID',
        typeName: 'TestConcept',
        version: 0,
        value: {} as any,
      }

      expect(() => migrator.migrate(toMigrate)).to.throw(/Received an invalid version value, 0, for TestConcept/)
    })

    it('throws when the version of the concept to migrate is higher than the current version', () => {
      const toMigrate: CommandEnvelope = {
        requestID: 'requestID',
        typeName: 'TestConcept',
        version: 4,
        value: {} as any,
      }

      expect(() => migrator.migrate(toMigrate)).to.throw(
        /The current version of TestConcept is 3, which is lower than the received version 4/
      )
    })

    it('does not migrate when the received version is the same as the current version', () => {
      const toMigrate: CommandEnvelope = {
        requestID: 'requestID',
        typeName: 'TestConcept',
        version: 3,
        value: {} as any,
      }

      expect(migrator.migrate(toMigrate)).to.equal(toMigrate)
    })

    it('migrates when the received version is lower than the current one', () => {
      const toMigrate: CommandEnvelope = {
        requestID: 'requestID',
        typeName: 'TestConcept',
        version: 1,
        value: {
          field1: 'test-field1',
        } as any,
      }

      const expected: CommandEnvelope = {
        requestID: 'requestID',
        typeName: 'TestConcept',
        version: 3,
        value: {
          field1: 'test-field1',
          field2: 2,
          field3: 'default',
        } as any,
      }

      const got = migrator.migrate(toMigrate) as CommandEnvelope
      const value = got.value as TestConceptV3
      expect(got).not.to.be.equal(toMigrate) // This checks the reference is not the same (i.e. a different object is returned)
      expect(got).to.be.deep.equal(expected)
      expect(value.constructor.name).to.be.equal('TestConceptV3')
    })
  })
})
