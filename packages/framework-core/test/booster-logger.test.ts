/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */

import { expect } from 'chai'
import * as chai from 'chai'
import { fake, replace, restore } from 'sinon'
import { buildLogger } from '../src/booster-logger'
import { Level } from '@boostercloud/framework-types'

chai.use(require('sinon-chai'))

describe('the `buildLogger method`', () => {
  afterEach(() => {
    restore()
  })

  it('"debug", "info" and "error" log when in `debug` level ', () => {
    const fakeConsoleLog = fake()
    const fakeConsoleError = fake()
    replace(console, 'log', fakeConsoleLog)
    replace(console, 'error', fakeConsoleError)

    const logger = buildLogger(Level.debug)
    logger.debug('a')
    logger.info('b')
    logger.error('c')

    expect(fakeConsoleLog).to.have.been.calledTwice
    expect(fakeConsoleError).to.have.been.calledOnce
  })

  it('only "info" and "error" log when in `info` level ', () => {
    const fakeConsoleLog = fake()
    const fakeConsoleError = fake()
    replace(console, 'log', fakeConsoleLog)
    replace(console, 'error', fakeConsoleError)

    const logger = buildLogger(Level.info)
    logger.debug('a')
    logger.info('b')
    logger.error('c')

    expect(fakeConsoleLog).to.have.been.calledOnce
    expect(fakeConsoleError).to.have.been.calledOnce
  })

  it('only "error" logs when in `error` level ', () => {
    const fakeConsoleLog = fake()
    const fakeConsoleError = fake()
    replace(console, 'log', fakeConsoleLog)
    replace(console, 'error', fakeConsoleError)

    const logger = buildLogger(Level.error)
    logger.debug('a')
    logger.info('b')
    logger.error('c')

    expect(fakeConsoleLog).not.to.have.been.called
    expect(fakeConsoleError).to.have.been.calledOnce
  })
})
