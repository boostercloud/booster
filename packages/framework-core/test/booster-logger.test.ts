/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */

import { expect } from './expect'
import { fake, replace, restore } from 'sinon'
import { buildLogger } from '../src/booster-logger'
import { BoosterConfig, Level, Logger } from '@boostercloud/framework-types'

describe('the `buildLogger method`', () => {
  const config = new BoosterConfig('Test Logger')

  afterEach(() => {
    restore()
  })

  it('"debug", "info" and "error" log when in `debug` level ', () => {
    const fakeConsoleDebug = fake()
    const fakeConsoleInfo = fake()
    const fakeConsoleError = fake()
    replace(console, 'debug', fakeConsoleDebug)
    replace(console, 'info', fakeConsoleInfo)
    replace(console, 'error', fakeConsoleError)

    const logger = buildLogger(Level.debug, config)
    logger.debug('a')
    logger.info('b')
    logger.error('c')

    expect(fakeConsoleDebug).to.have.been.calledOnce
    expect(fakeConsoleInfo).to.have.been.calledOnce
    expect(fakeConsoleError).to.have.been.calledOnce
  })

  it('only "info" and "error" log when in `info` level ', () => {
    const fakeConsoleDebug = fake()
    const fakeConsoleInfo = fake()
    const fakeConsoleError = fake()
    replace(console, 'debug', fakeConsoleDebug)
    replace(console, 'info', fakeConsoleInfo)
    replace(console, 'error', fakeConsoleError)

    const logger = buildLogger(Level.info, config)
    logger.debug('a')
    logger.info('b')
    logger.error('c')

    expect(fakeConsoleDebug).to.not.have.been.called
    expect(fakeConsoleInfo).to.have.been.calledOnce
    expect(fakeConsoleError).to.have.been.calledOnce
  })

  it('only "error" logs when in `error` level ', () => {
    const fakeConsoleDebug = fake()
    const fakeConsoleInfo = fake()
    const fakeConsoleError = fake()
    replace(console, 'debug', fakeConsoleDebug)
    replace(console, 'info', fakeConsoleInfo)
    replace(console, 'error', fakeConsoleError)

    const logger = buildLogger(Level.error, config)
    logger.debug('a')
    logger.info('b')
    logger.error('c')

    expect(fakeConsoleDebug).to.not.have.been.called
    expect(fakeConsoleInfo).to.not.have.been.calledOnce
    expect(fakeConsoleError).to.have.been.calledOnce
  })

  it('use custom logger when is defined at config level', () => {
    const fakeConsoleDebug = fake()
    const fakeCustomDebug = fake()
    replace(console, 'debug', fakeConsoleDebug)
    const configWithLogger = new BoosterConfig('TestLogger')

    const customLogger: Logger = {
      debug: (message?: any, ...optionalParams: any[]) => {},
      info: (message?: any, ...optionalParams: any[]) => {},
      error: (message?: any, ...optionalParams: any[]) => {},
    }

    replace(customLogger, 'debug', fakeCustomDebug)
    configWithLogger.logger = customLogger

    const logger = buildLogger(Level.debug, configWithLogger)
    logger.debug('a')

    expect(fakeConsoleDebug).to.not.have.been.called
    expect(fakeCustomDebug).to.have.been.calledOnce
  })
})
