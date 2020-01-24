/* eslint-disable @typescript-eslint/no-explicit-any */
import * as fc from 'fast-check'
import { BoosterConfig } from '../src/config'
import { expect } from 'fancy-test'
import { MigrationMetadata } from '../src/concepts'

describe('the config type', () => {
  describe('resourceNames', () => {
    it('fails to get if the app name is empty', () => {
      const cfg = new BoosterConfig()
      cfg.appName = ''
      expect(() => cfg.resourceNames).to.throw()
    })

    it('gets the application stack name from the app name', () => {
      fc.assert(
        fc.property(fc.string(1, 10), (appName) => {
          const cfg = new BoosterConfig()
          cfg.appName = appName
          expect(cfg.resourceNames.applicationStack).to.equal(`${appName}-application-stack`)
        })
      )
    })

    it('gets the events stream name from the app name', () => {
      fc.assert(
        fc.property(fc.string(1, 10), (appName) => {
          const cfg = new BoosterConfig()
          cfg.appName = appName
          expect(cfg.resourceNames.eventsStream).to.equal(`${appName}-application-stack-events-stream`)
        })
      )
    })

    it('gets the events store name from the app name', () => {
      fc.assert(
        fc.property(fc.string(1, 10), (appName) => {
          const cfg = new BoosterConfig()
          cfg.appName = appName
          expect(cfg.resourceNames.eventsStore).to.equal(`${appName}-application-stack-events-store`)
        })
      )
    })

    it('gets well-formatted readmodel names, based on the application name', () => {
      fc.assert(
        fc.property(fc.string(1, 10), fc.string(1, 10), (appName, readModelName) => {
          const cfg = new BoosterConfig()
          cfg.appName = appName
          expect(cfg.resourceNames.forReadModel(readModelName)).to.equal(
            `${appName}-application-stack-${readModelName}`
          )
        })
      )
    })
  })

  describe('thereAreRoles', () => {
    it('returns true when there are roles defined', () => {
      const config = new BoosterConfig()
      config.roles['test-role'] = {
        allowSelfSignUp: false,
      }

      expect(config.thereAreRoles).to.be.equal(true)
    })

    it('returns false when there are no roles defined', () => {
      const config = new BoosterConfig()
      expect(config.thereAreRoles).to.be.equal(false)
    })
  })

  describe('currentVersionFor', () => {
    it('returns 1 when the concept does not have any migration defined', () => {
      const config = new BoosterConfig()
      const migrations = new Map()
      migrations.set(2, {} as any)
      config.migrations['concept-with-migrations'] = migrations

      expect(config.currentVersionFor('concept-without-migration')).to.be.equal(1)
    })

    it('returns the version of the latest migration', () => {
      class SchemaTest {}
      class MigrationClassTest {}
      const config = new BoosterConfig()
      const migrations = new Map<number, MigrationMetadata>()
      migrations.set(3, {
        fromSchema: SchemaTest,
        toSchema: SchemaTest,
        methodName: 'method3',
        migrationClass: MigrationClassTest,
        toVersion: 3,
      })
      migrations.set(2, {
        fromSchema: SchemaTest,
        toSchema: SchemaTest,
        methodName: 'method2',
        migrationClass: MigrationClassTest,
        toVersion: 2,
      })
      config.migrations['concept'] = migrations

      expect(config.currentVersionFor('concept')).to.be.equal(3)
    })
  })

  describe('validate', () => {
    it('throws when there are gaps in the migration versions for a concept', () => {
      const config = new BoosterConfig()
      const migrations = new Map()
      migrations.set(3, {} as any)
      migrations.set(2, {} as any)
      migrations.set(5, {} as any)
      config.migrations['concept'] = migrations

      expect(() => config.validate()).to.throw(/Migrations for 'concept' are invalid/)
    })

    it('does not throw when there are no gaps in the migration versions for a concept', () => {
      const config = new BoosterConfig()
      const migrations = new Map()
      migrations.set(4, {} as any)
      migrations.set(2, {} as any)
      migrations.set(3, {} as any)
      config.migrations['concept'] = migrations

      expect(() => config.validate()).to.not.throw()
    })
  })
})
