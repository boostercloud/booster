/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */

import { expect } from './expect'
import { fake, replace, restore } from 'sinon'
import { buildLogger } from '../src/booster-logger'
import { Level } from '@boostercloud/framework-types'

describe('the `buildLogger method`', () => {
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

    const logger = buildLogger(Level.debug)
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

    const logger = buildLogger(Level.info)
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

    const logger = buildLogger(Level.error)
    logger.debug('a')
    logger.info('b')
    logger.error('c')

    expect(fakeConsoleDebug).to.not.have.been.called
    expect(fakeConsoleInfo).to.not.have.been.calledOnce
    expect(fakeConsoleError).to.have.been.calledOnce
  })
})
