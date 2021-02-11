/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from './expect'
import { BoosterApp } from '../src/booster-app'
import { replace, fake, restore, match, replaceGetter } from 'sinon'
import { Importer } from '../src/importer'
import * as EntitySnapshotFetcher from '../src/entity-snapshot-fetcher'
import { UUID } from '@boostercloud/framework-types'

describe('the `BoosterApp` class', () => {
  afterEach(() => {
    restore()
    BoosterApp.configure('test', (config) => {
      config.appName = ''
      for (const propName in config.commandHandlers) {
        delete config.commandHandlers[propName]
      }
    })
  })

  describe('the `configure` method', () => {
    it('can be used to configure the app, using the `configure` method', () => {
      BoosterApp.configure('test', (config) => {
        config.appName = 'test-app-name'
      })

      BoosterApp.configure('another-environment', (config) => {
        config.appName = 'this-shouldnt-be-set'
      })

      expect(BoosterApp.configuredEnvironments).to.have.lengthOf(2)
      expect(BoosterApp.configuredEnvironments).to.include.keys(['test', 'another-environment'])
    })
  })

  describe('the `configureCurrentEnv` method', () => {
    it('yields the configured environment corresponding to the environment name in the BOOSTER_ENV environment variable', () => {
      BoosterApp.configure('test', (config) => {
        config.appName = 'test-app-name'
      })

      BoosterApp.configure('another-environment', (config) => {
        config.appName = 'this-shouldnt-be-set'
      })

      expect(BoosterApp.config.environmentName).to.equal('test')

      BoosterApp.configureCurrentEnv((config) => {
        expect(config.appName).to.equal('test-app-name')
        expect(config.appName).to.equal(BoosterApp.config.appName) // Also matches to the static config
      })
    })
  })

  describe('the `start` method', () => {
    it('imports all the user files', () => {
      const fakeImporter = fake()
      replace(Importer, 'importUserProjectFiles', fakeImporter)
      BoosterApp.start('path/to/code')
      expect(fakeImporter).to.have.been.calledOnce
    })
  })

  describe('the public static `fetchEntitySnapshot` method', () => {
    it('calls the entitySnapshotFetcher passing the config, the logger and the `entityName` and `entityID` parameters', async () => {
      replace(EntitySnapshotFetcher, 'fetchEntitySnapshot', fake())

      class SomeEntity {
        public constructor(readonly id: UUID) {}
      }

      await BoosterApp.fetchEntitySnapshot(SomeEntity, '42')

      expect(EntitySnapshotFetcher.fetchEntitySnapshot).to.have.been.calledOnceWith(
        BoosterApp.config,
        BoosterApp.logger,
        SomeEntity,
        '42'
      )
    })
  })

  describe('the `readModel` method', () => {
    class TestReadModel {
      constructor(public id: string) {}
    }
    it('returns a properly configured Searcher', async () => {
      const searcherFunctionFake = fake()
      BoosterApp.configureCurrentEnv((config) => {
        replaceGetter(config, 'provider', () => {
          return {
            readModels: {
              search: searcherFunctionFake,
            },
          } as any
        })
      })

      await BoosterApp.readModel(TestReadModel).search()

      expect(searcherFunctionFake).to.have.been.calledOnceWithExactly(
        match.any,
        match.any,
        TestReadModel.name,
        match.any
      )
    })
  })
})
