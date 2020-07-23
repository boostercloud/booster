/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { describe, it, afterEach } from 'mocha'
import { stub, replace, restore, fake } from 'sinon'
import { Script } from '../../src/common/script'
import { expect } from '../expect'
import { logger } from '../../src/services/logger'

interface TestContext {
  ctxParam: 'value'
}

const testContext: TestContext = {
  ctxParam: 'value',
}

function replaceLogger(scriptClass: any) {
  const fakeLogger = logger
  const loggerInfo = fake()
  const loggerFail = fake()
  const loggerStart = fake()
  const loggerSucceed = fake()

  replace(fakeLogger, 'info', loggerInfo)
  replace(fakeLogger, 'fail', loggerFail)
  replace(fakeLogger, 'start', loggerStart)
  replace(fakeLogger, 'succeed', loggerSucceed)
  replace(scriptClass, 'logger', fakeLogger)
  return { loggerInfo, loggerFail, loggerStart, loggerSucceed }
}

describe('The Script class', () => {
  afterEach(() => {
    restore()
  })

  describe('init', () => {
    it('runs a sequence of successful actions chaining context and return values', async () => {
      const fakeAction1 = stub().resolves()
      const fakeAction2 = stub().resolves()
      const fakeAction3 = stub().resolves()

      await Script.init('initializing test', Promise.resolve(testContext))
        .step('step 1', fakeAction1)
        .step('step 2', fakeAction2)
        .step('step 3', fakeAction3)
        .done()

      expect(fakeAction1).to.have.been.calledOnce
      // @ts-ignore
      expect(fakeAction1).to.have.been.calledBefore(fakeAction2)
      expect(fakeAction1).to.have.been.calledOnceWith({ ctxParam: 'value' })

      // @ts-ignore
      expect(fakeAction2).to.have.been.calledBefore(fakeAction3)
      expect(fakeAction2).to.have.been.calledOnceWith({ ctxParam: 'value' })
      // @ts-ignore
      expect(fakeAction2).to.have.been.calledBefore(fakeAction3)
    })

    it('runs a sequence of successful actions and stops on failure', async () => {
      const fakeAction1 = stub().resolves()
      const fakeAction2 = stub().rejects('some error')
      const fakeAction3 = stub().resolves()

      await expect(Script.init('initializing test', Promise.resolve(testContext))
        .step('step', fakeAction1)
        .step('step', fakeAction2)
        .step('step', fakeAction3)
        .done()).to.eventually.be.rejectedWith()

      expect(fakeAction1).to.have.been.calledOnce
      // @ts-ignore
      expect(fakeAction1).to.have.been.calledBefore(fakeAction2)
      expect(fakeAction1).to.have.been.calledOnceWith({ ctxParam: 'value' })
      // @ts-ignore
      expect(fakeAction1).to.have.been.calledBefore(fakeAction2)

      expect(fakeAction2).to.have.been.calledOnceWith({ ctxParam: 'value' })

      expect(fakeAction3).not.to.have.been.called
      expect(fakeAction2).to.have.been.calledOnceWith({ ctxParam: 'value' })
    })

    it('prints the provided message', async () => {
      const msg = 'initializing'
      const { loggerInfo } = replaceLogger(Script)

      await Script.init(msg, Promise.resolve(testContext)).done()

      expect(loggerInfo).to.have.been.calledOnceWith(msg)
    })

    it('fails gracefully in case of initializer failure', async () => {
      const msg = 'initializing'
      const errorMessage = 'some error'
      const err = new Error(errorMessage)
      const initializer = stub().rejects(err)

      await expect(Script.init(msg, initializer()).done()).to.eventually.be.rejectedWith(errorMessage)
    })
  })

  describe('info', () => {
    it('prints the provided message', async () => {
      const initMsg = 'initializing test'
      const msg = 'yo!'
      const { loggerInfo } = replaceLogger(Script)

      await Script.init(initMsg, Promise.resolve(testContext))
        .info(msg)
        .done()

      expect(loggerInfo).to.have.been.calledWith(msg)
    })
  })

  describe('step', () => {
    it('prints the passed message, initializes and runs the function', async () => {
      const msg = 'That is one small step for a man'
      const val = 'one giant leap for mankind'
      const stepFn = stub().resolves(val)
      const { loggerStart, loggerSucceed } = replaceLogger(Script)

      await Script.init('initializing', Promise.resolve(testContext))
        .step(msg, stepFn)
        .done()

      expect(loggerStart).to.have.been.calledOnceWith(msg)
      expect(loggerSucceed).to.have.been.called
      expect(stepFn).to.have.been.calledOnceWith(testContext)
    })

    it('fails gracefully on step function failure', async () => {
      const err = new Error('some error')
      const msg = 'That is no step for anyone'
      const stepFn = stub().rejects(err)
      const { loggerStart, loggerSucceed} = replaceLogger(Script)

      await expect(Script.init('initializing', Promise.resolve(testContext))
        .step(msg, stepFn)
        .done()).to.eventually.be.rejectedWith()
      expect(loggerStart).to.have.been.calledOnceWith(msg)
      expect(loggerSucceed).not.to.have.been.called
      expect(stepFn).to.have.been.calledOnceWith(testContext)
    })
  })

  describe('catch', () => {
    it('prints a custom error message for specified error types', async () => {
      const err = new SyntaxError('other message')
      const msg = 'much nicer message'

      await expect(Script.init('initializing', Promise.reject(err))
        .catch('SyntaxError', () => msg)
        .done()).to.eventually.be.rejectedWith(msg)
    })

    it('prints the error message for non specified error types', async () => {
      const msg = 'some message'
      const err = new Error(msg)

      await expect(Script.init('initializing', Promise.reject(err))
        .catch('SyntaxError', () => msg)
        .done()).to.eventually.be.rejectedWith(msg)
    })
  })
})
