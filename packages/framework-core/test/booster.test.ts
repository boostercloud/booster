/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from './expect'
import { Booster, boosterEventDispatcher, boosterServeGraphQL } from '../src/booster'
import { replace, fake, restore, match, replaceGetter } from 'sinon'
import { Importer } from '../src/importer'
import * as EntitySnapshotFetcher from '../src/entity-snapshot-fetcher'
import { UUID } from '@boostercloud/framework-types'

describe('the `Booster` class', () => {
  afterEach(() => {
    restore()
    Booster.configure('test', (config) => {
      config.appName = ''
      for (const propName in config.commandHandlers) {
        delete config.commandHandlers[propName]
      }
    })
  })

  describe('the `configure` method', () => {
    it('can be used to configure the app, using the `configure` method', () => {
      const booster = Booster as any

      Booster.configure('test', (config) => {
        config.appName = 'test-app-name'
      })

      Booster.configure('another-environment', (config) => {
        config.appName = 'this-shouldnt-be-set'
      })

      expect(booster.configuredEnvironments).to.have.lengthOf(2)
      expect(booster.configuredEnvironments).to.include.keys(['test', 'another-environment'])
      expect(booster.config.appName).to.equal('test-app-name')
    })
  })

  describe('the `start` method', () => {
    it('imports all the user files', () => {
      const fakeImporter = fake()
      replace(Importer, 'importUserProjectFiles', fakeImporter)
      Booster.start('path/to/code')
      expect(fakeImporter).to.have.been.calledOnce
    })
  })

  describe('the public static `fetchEntitySnapshot` method', () => {
    it('calls the entitySnapshotFetcher passing the config, the logger and the `entityName` and `entityID` parameters', async () => {
      replace(EntitySnapshotFetcher, 'fetchEntitySnapshot', fake())
      const booster = Booster as any

      class SomeEntity {
        public constructor(readonly id: UUID) {}
      }

      await Booster.fetchEntitySnapshot(SomeEntity, '42')

      expect(EntitySnapshotFetcher.fetchEntitySnapshot).to.have.been.calledOnceWith(
        booster.config,
        booster.logger,
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
      Booster.configureCurrentEnv((config) => {
        replaceGetter(config, 'provider', () => {
          return {
            readModels: {
              search: searcherFunctionFake,
            },
          } as any
        })
      })
      await Booster.readModel(TestReadModel).search()

      expect(searcherFunctionFake).to.have.been.calledOnceWithExactly(
        match.any,
        match.any,
        TestReadModel.name,
        match.any
      )
    })
  })
})

describe('the public static function `boosterEventDispatcher`', () => {
  it('calls `Booster.dispatchEvent` passing the rawEvent', async () => {
    replace(Booster, 'dispatchEvent', fake())
    const message = { body: 'Test body' }

    await boosterEventDispatcher(message)

    expect(Booster.dispatchEvent).to.have.been.calledOnceWith(message)
  })
})

describe('the public static function `boosterServeGraphQL`', () => {
  it('calls `Booster.serveGraphQL` passing the rawMessage', async () => {
    replace(Booster, 'serveGraphQL', fake())
    const message = { body: 'Test body' }

    await boosterServeGraphQL(message)

    expect(Booster.serveGraphQL).to.have.been.calledOnceWith(message)
  })
})
