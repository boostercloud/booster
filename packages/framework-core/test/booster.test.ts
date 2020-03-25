/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from 'chai'
import * as chai from 'chai'
import {
  Booster,
  boosterCommandDispatcher,
  boosterEventDispatcher,
  boosterReadModelMapper,
  boosterPreSignUpChecker,
  boosterServeGraphQL,
  boosterRequestAuthorizer,
} from '../src/booster'
import { replace, fake, restore } from 'sinon'
import { Importer } from '../src/importer'
import { BoosterReadModelFetcher } from '../src/booster-read-model-fetcher'
import * as EntitySnapshotFetcher from '../src/entity-snapshot-fetcher'

chai.use(require('sinon-chai'))

describe('the `Booster` class', () => {
  process.env.BOOSTER_ENV = 'test'
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

      expect(booster.config.appName).to.equal('test-app-name')
    })
  })

  describe('the `start` method', () => {
    it('imports all the user files', () => {
      const fakeImporter = fake()
      replace(Importer, 'importUserProjectFiles', fakeImporter)
      Booster.start()
      expect(fakeImporter).to.have.been.calledOnce
    })
  })

  describe('the public static `fetchReadModels` method', () => {
    it('calls `BoosterReadModelFetcher.fetch` passing the request and initializing it with Booster config', async () => {
      replace(BoosterReadModelFetcher, 'fetch', fake())
      const request = { some: 'request' }
      const booster = Booster as any

      await Booster.fetchReadModels(request)

      expect(BoosterReadModelFetcher.fetch).to.have.been.calledOnceWith(request, booster.config)
    })
  })

  describe('the public static `fetchEntitySnapshot` method', () => {
    it('calls the entitySnapshotFetcher passing the config, the logger and the `entityName` and `entityID` parameters', async () => {
      replace(EntitySnapshotFetcher, 'fetchEntitySnapshot', fake())
      const booster = Booster as any

      await Booster.fetchEntitySnapshot('SomeEntity', '42')

      expect(EntitySnapshotFetcher.fetchEntitySnapshot).to.have.been.calledOnceWith(
        booster.config,
        booster.logger,
        'SomeEntity',
        '42'
      )
    })
  })
})

describe('the public static function `boosterCommandDispatcher`', () => {
  it('calls `Booster.dispatchCommand` passing the rawCommand', async () => {
    replace(Booster, 'dispatchCommand', fake())
    const message = { body: 'Test body' }

    await boosterCommandDispatcher(message)

    expect(Booster.dispatchCommand).to.have.been.calledOnceWith(message)
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

describe('the public static function `boosterReadModelMapper`', () => {
  it('calls `Booster.fetchReadModels` passing the rawMessage', async () => {
    replace(Booster, 'fetchReadModels', fake())
    const message = { body: 'Test body' }

    await boosterReadModelMapper(message)

    expect(Booster.fetchReadModels).to.have.been.calledOnceWith(message)
  })
})

describe('the public static function `boosterPreSignUpChecker`', () => {
  it('calls `Booster.checkSignUp` passing the rawMessage', async () => {
    replace(Booster, 'checkSignUp', fake())
    const message = { body: 'Test body' }

    await boosterPreSignUpChecker(message)

    expect(Booster.checkSignUp).to.have.been.calledOnceWith(message)
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

describe('the public static function `boosterRequestAuthorizer`', () => {
  it('calls `Booster.authorizeRequest` passing the rawMessage', async () => {
    replace(Booster, 'authorizeRequest', fake())
    const message = { body: 'Test body' }

    await boosterRequestAuthorizer(message)

    expect(Booster.authorizeRequest).to.have.been.calledOnceWith(message)
  })
})
