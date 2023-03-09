/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */

import { expect } from './helpers/expect'
import { fake, replace, restore } from 'sinon'
import { BoosterConfig, Level, Logger } from '@boostercloud/framework-types'
import { getLogger } from '../src/logger'

describe('the `getLogger method`', () => {
  afterEach(() => {
    restore()
  })

  context('in `debug` level', () => {
    it('provides `debug`, `info`, `warn` and `error` functions', () => {
      const fakeConsoleDebug = fake()
      const fakeConsoleInfo = fake()
      const fakeConsoleWarn = fake()
      const fakeConsoleError = fake()
      replace(console, 'debug', fakeConsoleDebug)
      replace(console, 'info', fakeConsoleInfo)
      replace(console, 'warn', fakeConsoleWarn)
      replace(console, 'error', fakeConsoleError)

      const config = new BoosterConfig('test')
      config.logLevel = Level.debug
      const logger = getLogger(config)
      logger.debug('a')
      logger.info('b')
      logger.warn('c')
      logger.error('d')

      expect(fakeConsoleDebug).to.have.been.calledOnce
      expect(fakeConsoleInfo).to.have.been.calledOnce
      expect(fakeConsoleWarn).to.have.been.calledOnce
      expect(fakeConsoleError).to.have.been.calledOnce
    })
  })

  context('in `info` level', () => {
    it('provides only `info`, `warn` and `error` functions', () => {
      const fakeConsoleDebug = fake()
      const fakeConsoleInfo = fake()
      const fakeConsoleWarn = fake()
      const fakeConsoleError = fake()
      replace(console, 'debug', fakeConsoleDebug)
      replace(console, 'info', fakeConsoleInfo)
      replace(console, 'warn', fakeConsoleWarn)
      replace(console, 'error', fakeConsoleError)

      const config = new BoosterConfig('test')
      config.logLevel = Level.info
      const logger = getLogger(config)
      logger.debug('a')
      logger.info('b')
      logger.warn('c')
      logger.error('d')

      expect(fakeConsoleDebug).not.to.have.been.called
      expect(fakeConsoleInfo).to.have.been.calledOnce
      expect(fakeConsoleWarn).to.have.been.calledOnce
      expect(fakeConsoleError).to.have.been.calledOnce
    })
  })

  context('in `warn` level', () => {
    it('provides only `warn` and `error` functions', () => {
      const fakeConsoleDebug = fake()
      const fakeConsoleInfo = fake()
      const fakeConsoleWarn = fake()
      const fakeConsoleError = fake()
      replace(console, 'debug', fakeConsoleDebug)
      replace(console, 'info', fakeConsoleInfo)
      replace(console, 'warn', fakeConsoleWarn)
      replace(console, 'error', fakeConsoleError)

      const config = new BoosterConfig('test')
      config.logLevel = Level.warn
      const logger = getLogger(config)
      logger.debug('a')
      logger.info('b')
      logger.warn('c')
      logger.error('d')

      expect(fakeConsoleDebug).not.to.have.been.called
      expect(fakeConsoleInfo).not.to.have.been.called
      expect(fakeConsoleWarn).to.have.been.calledOnce
      expect(fakeConsoleError).to.have.been.calledOnce
    })
  })

  context('in `error` level', () => {
    it('provides only `error` functions', () => {
      const fakeConsoleDebug = fake()
      const fakeConsoleInfo = fake()
      const fakeConsoleWarn = fake()
      const fakeConsoleError = fake()
      replace(console, 'debug', fakeConsoleDebug)
      replace(console, 'info', fakeConsoleInfo)
      replace(console, 'warn', fakeConsoleWarn)
      replace(console, 'error', fakeConsoleError)

      const config = new BoosterConfig('test')
      config.logLevel = Level.error
      const logger = getLogger(config)
      logger.debug('a')
      logger.info('b')
      logger.warn('c')
      logger.error('d')

      expect(fakeConsoleDebug).not.to.have.been.called
      expect(fakeConsoleInfo).not.to.have.been.called
      expect(fakeConsoleWarn).not.to.have.been.called
      expect(fakeConsoleError).to.have.been.calledOnce
    })
  })

  context('when a logger instance is defined at config level', () => {
    let config: BoosterConfig
    const fakeLogger = {
      debug: fake(),
      info: fake(),
      warn: fake(),
      error: fake(),
      logProcess: fake(),
    }

    const runLoggerOperations = (logger: Logger): void => {
      logger.debug('a')
      logger.info('b')
      logger.warn('c')
      logger.error('d')
      logger.logProcess('e', () => 'f')
    }

    beforeEach(() => {
      Object.values(fakeLogger).forEach((f) => f.resetHistory())
      config = new BoosterConfig('test')
      config.logger = fakeLogger
    })

    context('when no prefix is configured', () => {
      it('uses the configured logger instance with the default prefix', () => {
        const logger = getLogger(config)
        runLoggerOperations(logger)

        expect(fakeLogger.debug).to.have.been.calledOnceWith('[Booster]: ', 'a')
        expect(fakeLogger.info).to.have.been.calledWith('[Booster]: ', 'b')
        expect(fakeLogger.warn).to.have.been.calledOnceWith('[Booster]: ', 'c')
        expect(fakeLogger.error).to.have.been.calledOnceWith('[Booster]: ', 'd')
        expect(fakeLogger.logProcess).to.have.been.calledWith('e')
      })

      context('and a location is set', () => {
        it('uses the configured logger instance with the default prefix and renders the location', () => {
          const logger = getLogger(config, 'test-location')
          runLoggerOperations(logger)

          expect(fakeLogger.debug).to.have.been.calledOnceWith('[Booster]|test-location: ', 'a')
          expect(fakeLogger.info).to.have.been.calledWith('[Booster]|test-location: ', 'b')
          expect(fakeLogger.warn).to.have.been.calledOnceWith('[Booster]|test-location: ', 'c')
          expect(fakeLogger.error).to.have.been.calledOnceWith('[Booster]|test-location: ', 'd')
          expect(fakeLogger.logProcess).to.have.been.calledWithMatch('e')
        })
      })
    })

    context('when a prefix is configured', () => {
      it('uses the configured logger instance with the configured prefix', () => {
        config.logPrefix = 'MyLogger'
        const logger = getLogger(config)
        runLoggerOperations(logger)

        expect(fakeLogger.debug).to.have.been.calledOnceWith('[MyLogger]: ', 'a')
        expect(fakeLogger.info).to.have.been.calledWith('[MyLogger]: ', 'b')
        expect(fakeLogger.warn).to.have.been.calledOnceWith('[MyLogger]: ', 'c')
        expect(fakeLogger.error).to.have.been.calledOnceWith('[MyLogger]: ', 'd')
        expect(fakeLogger.logProcess).to.have.been.calledWithMatch('e')
      })
    })

    context('when a `location` is provided via parameter', () => {
      it('uses the configured logger instance and sends the provided prefix', () => {
        const logger = getLogger(config, 'ParameterLogger')
        runLoggerOperations(logger)

        expect(fakeLogger.debug).to.have.been.calledOnceWith('[Booster]|ParameterLogger: ', 'a')
        expect(fakeLogger.info).to.have.been.calledWith('[Booster]|ParameterLogger: ', 'b')
        expect(fakeLogger.warn).to.have.been.calledOnceWith('[Booster]|ParameterLogger: ', 'c')
        expect(fakeLogger.error).to.have.been.calledOnceWith('[Booster]|ParameterLogger: ', 'd')
        expect(fakeLogger.logProcess).to.have.been.calledWithMatch('e')
      })
    })

    context('when the `logPrefix` is overridden via parameter', () => {
      it('uses the configured logger instance and uses the `logPrefix` provided via parameter', () => {
        config.logPrefix = 'ConfigLogger'
        const logger = getLogger(config, undefined, 'ParameterLogger')
        runLoggerOperations(logger)

        expect(fakeLogger.debug).to.have.been.calledOnceWith('[ParameterLogger]: ', 'a')
        expect(fakeLogger.info).to.have.been.calledWith('[ParameterLogger]: ', 'b')
        expect(fakeLogger.warn).to.have.been.calledOnceWith('[ParameterLogger]: ', 'c')
        expect(fakeLogger.error).to.have.been.calledOnceWith('[ParameterLogger]: ', 'd')
        expect(fakeLogger.logProcess).to.have.been.calledWithMatch('e')
      })
    })

    context('when the `logPrefix` is overridden via parameter and the `location` is provided via parameter', () => {
      it('uses the configured logger instance, uses the `logPrefix` provided via parameter and renders the location', () => {
        config.logPrefix = 'ConfigLogger'
        const logger = getLogger(config, 'ParameterLocation', 'ParameterLogger')
        runLoggerOperations(logger)

        expect(fakeLogger.debug).to.have.been.calledOnceWith('[ParameterLogger]|ParameterLocation: ', 'a')
        expect(fakeLogger.info).to.have.been.calledWith('[ParameterLogger]|ParameterLocation: ', 'b')
        expect(fakeLogger.warn).to.have.been.calledOnceWith('[ParameterLogger]|ParameterLocation: ', 'c')
        expect(fakeLogger.error).to.have.been.calledOnceWith('[ParameterLogger]|ParameterLocation: ', 'd')
        expect(fakeLogger.logProcess).to.have.been.calledWithMatch('e')
      })
    })
  })
})
