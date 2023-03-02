/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */

import { expect } from './helpers/expect'
import { fake, replace, restore } from 'sinon'
import { BoosterConfig, Level } from '@boostercloud/framework-types'
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
    context('when no prefix is configured', () => {
      it('uses the configured logger instance with the default prefix', () => {
        const fakeConsoleDebug = fake()
        const fakeConsoleInfo = fake()
        const fakeConsoleWarn = fake()
        const fakeConsoleError = fake()

        const config = new BoosterConfig('test')
        config.logger = {
          debug: fakeConsoleDebug,
          info: fakeConsoleInfo,
          warn: fakeConsoleWarn,
          error: fakeConsoleError,
        }
        const logger = getLogger(config)
        logger.debug('a')
        logger.info('b')
        logger.warn('c')
        logger.error('d')

        expect(fakeConsoleDebug).to.have.been.calledOnceWith('[Booster]: ', 'a')
        expect(fakeConsoleInfo).to.have.been.calledOnceWith('[Booster]: ', 'b')
        expect(fakeConsoleWarn).to.have.been.calledOnceWith('[Booster]: ', 'c')
        expect(fakeConsoleError).to.have.been.calledOnceWith('[Booster]: ', 'd')
      })

      context('and a location is set', () => {
        it('uses the configured logger instance with the default prefix and renders the location', () => {
          const fakeConsoleDebug = fake()
          const fakeConsoleInfo = fake()
          const fakeConsoleWarn = fake()
          const fakeConsoleError = fake()

          const config = new BoosterConfig('test')
          config.logger = {
            debug: fakeConsoleDebug,
            info: fakeConsoleInfo,
            warn: fakeConsoleWarn,
            error: fakeConsoleError,
          }
          const logger = getLogger(config, 'test-location')
          logger.debug('a')
          logger.info('b')
          logger.warn('c')
          logger.error('d')

          expect(fakeConsoleDebug).to.have.been.calledOnceWith('[Booster]|test-location: ', 'a')
          expect(fakeConsoleInfo).to.have.been.calledOnceWith('[Booster]|test-location: ', 'b')
          expect(fakeConsoleWarn).to.have.been.calledOnceWith('[Booster]|test-location: ', 'c')
          expect(fakeConsoleError).to.have.been.calledOnceWith('[Booster]|test-location: ', 'd')
        })
      })
    })

    context('when a prefix is configured', () => {
      it('uses the configured logger instance with the configured prefix', () => {
        const fakeConsoleDebug = fake()
        const fakeConsoleInfo = fake()
        const fakeConsoleWarn = fake()
        const fakeConsoleError = fake()

        const config = new BoosterConfig('test')
        config.logger = {
          debug: fakeConsoleDebug,
          info: fakeConsoleInfo,
          warn: fakeConsoleWarn,
          error: fakeConsoleError,
        }
        config.logPrefix = 'MyLogger'
        const logger = getLogger(config)
        logger.debug('a')
        logger.info('b')
        logger.warn('c')
        logger.error('d')

        expect(fakeConsoleDebug).to.have.been.calledOnceWith('[MyLogger]: ', 'a')
        expect(fakeConsoleInfo).to.have.been.calledOnceWith('[MyLogger]: ', 'b')
        expect(fakeConsoleWarn).to.have.been.calledOnceWith('[MyLogger]: ', 'c')
        expect(fakeConsoleError).to.have.been.calledOnceWith('[MyLogger]: ', 'd')
      })
    })

    context('when a `location` is provided via parameter', () => {
      it('uses the configured logger instance and sends the provided prefix', () => {
        const fakeConsoleDebug = fake()
        const fakeConsoleInfo = fake()
        const fakeConsoleWarn = fake()
        const fakeConsoleError = fake()

        const config = new BoosterConfig('test')
        config.logger = {
          debug: fakeConsoleDebug,
          info: fakeConsoleInfo,
          warn: fakeConsoleWarn,
          error: fakeConsoleError,
        }
        const logger = getLogger(config, 'ParameterLogger')
        logger.debug('a')
        logger.info('b')
        logger.warn('c')
        logger.error('d')

        expect(fakeConsoleDebug).to.have.been.calledOnceWith('[Booster]|ParameterLogger: ', 'a')
        expect(fakeConsoleInfo).to.have.been.calledOnceWith('[Booster]|ParameterLogger: ', 'b')
        expect(fakeConsoleWarn).to.have.been.calledOnceWith('[Booster]|ParameterLogger: ', 'c')
        expect(fakeConsoleError).to.have.been.calledOnceWith('[Booster]|ParameterLogger: ', 'd')
      })
    })

    context('when the `logPrefix` is overriden via parameter', () => {
      it('uses the configured logger instance and uses the `logPrefix` provided via parameter', () => {
        const fakeConsoleDebug = fake()
        const fakeConsoleInfo = fake()
        const fakeConsoleWarn = fake()
        const fakeConsoleError = fake()

        const config = new BoosterConfig('test')
        config.logger = {
          debug: fakeConsoleDebug,
          info: fakeConsoleInfo,
          warn: fakeConsoleWarn,
          error: fakeConsoleError,
        }
        config.logPrefix = 'ConfigLogger'
        const logger = getLogger(config, undefined, 'ParameterLogger')
        logger.debug('a')
        logger.info('b')
        logger.warn('c')
        logger.error('d')

        expect(fakeConsoleDebug).to.have.been.calledOnceWith('[ParameterLogger]: ', 'a')
        expect(fakeConsoleInfo).to.have.been.calledOnceWith('[ParameterLogger]: ', 'b')
        expect(fakeConsoleWarn).to.have.been.calledOnceWith('[ParameterLogger]: ', 'c')
        expect(fakeConsoleError).to.have.been.calledOnceWith('[ParameterLogger]: ', 'd')
      })
    })

    context('when the `logPrefix` is overriden via parameter and the `location` is provided via parameter', () => {
      it('uses the configured logger instance, uses the `logPrefix` provided via parameter and renders the location', () => {
        const fakeConsoleDebug = fake()
        const fakeConsoleInfo = fake()
        const fakeConsoleWarn = fake()
        const fakeConsoleError = fake()

        const config = new BoosterConfig('test')
        config.logger = {
          debug: fakeConsoleDebug,
          info: fakeConsoleInfo,
          warn: fakeConsoleWarn,
          error: fakeConsoleError,
        }
        config.logPrefix = 'ConfigLogger'
        const logger = getLogger(config, 'ParameterLocation', 'ParameterLogger')
        logger.debug('a')
        logger.info('b')
        logger.warn('c')
        logger.error('d')

        expect(fakeConsoleDebug).to.have.been.calledOnceWith('[ParameterLogger]|ParameterLocation: ', 'a')
        expect(fakeConsoleInfo).to.have.been.calledOnceWith('[ParameterLogger]|ParameterLocation: ', 'b')
        expect(fakeConsoleWarn).to.have.been.calledOnceWith('[ParameterLogger]|ParameterLocation: ', 'c')
        expect(fakeConsoleError).to.have.been.calledOnceWith('[ParameterLogger]|ParameterLocation: ', 'd')
      })
    })
  })
})
