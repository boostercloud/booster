import { initializeEnvironment, currentEnvironment } from '../../src/services/environment'
import { logger } from '../../src/services/logger'
import { restore, replace, fake } from 'sinon'
import { expect } from '../expect'

describe('environment service', (): void => {
  let formerValue: string | undefined

  beforeEach(() => {
    if (process.env.BOOSTER_ENV !== undefined) {
      formerValue = process.env.BOOSTER_ENV
    }
    delete process.env.BOOSTER_ENV
  })

  afterEach(() => {
    process.env.BOOSTER_ENV = formerValue
    restore()
  })

  describe('currentEnvironment', (): void => {
    it('get current environment: testing', async () => {
      process.env.BOOSTER_ENV = 'testing'
      expect(currentEnvironment()).to.be.equal('testing')
    })

    it('get current environment: undefined', async () => {
      expect(currentEnvironment()).to.be.undefined
    })
  })

  describe('initializeEnvironment', (): void => {
    beforeEach(() => {
      replace(logger, 'error', fake.resolves({}))
    })
    const logMessage = /No environment set/

    describe('process.env.BOOSTER_ENV set', (): void => {
      beforeEach(() => {
        process.env.BOOSTER_ENV = 'testing'
      })

      it('set environment in param: no log message', async () => {
        initializeEnvironment(logger, 'production')
        expect(logger.error).to.not.have.been.calledWithMatch(logMessage)
        expect(process.env.BOOSTER_ENV).to.be.equal('production')
      })

      it('do not pass environment as param: no log message', async () => {
        initializeEnvironment(logger)
        expect(logger.error).to.not.have.been.calledWithMatch(logMessage)
        expect(process.env.BOOSTER_ENV).to.be.equal('testing')
      })
    })

    describe('process.env.BOOSTER_ENV not set', (): void => {
      it('set environment only in param: no log message', async () => {
        initializeEnvironment(logger, 'production')
        expect(logger.error).to.not.have.been.calledWithMatch(logMessage)
        expect(process.env.BOOSTER_ENV).to.be.equal('production')
      })

      it('environment not set as param: log message', async () => {
        initializeEnvironment(logger)
        expect(logger.error).to.have.been.calledWithMatch(logMessage)
        expect(process.env.BOOSTER_ENV).to.be.undefined
      })
    })
  })
})
