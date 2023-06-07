/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/no-unused-vars */
import { BoosterConfig, TraceActionTypes, TraceInfo } from '@boostercloud/framework-types'
import { Booster, Trace } from '../../../src'
import { expect } from '../../expect'
import { stub } from 'sinon'

describe('the `Trace` decorator', async () => {
  afterEach(() => {
    const booster = Booster as any
    delete booster.config.traceConfiguration
  })

  context('When a method is called', async () => {
    it('Injects the correct `this` to the traced method', async () => {
      Booster.config.traceConfiguration = {
        enableTraceNotification: true,
        includeInternal: false,
        onStart: CustomTracer.onStart,
        onEnd: CustomTracer.onEnd,
      }

      const testClass = new TestClass()
      await testClass.myCustomMethod('test')
      expect(testClass.innerField).to.be.eq('test')
    })

    it('onStart and onEnd methods are called in the expected order', async () => {
      const executedMethods: Array<string> = []
      stub(CustomTracer, 'onStart').callsFake(
        async (_config: BoosterConfig, _actionType: string, _traceInfo: TraceInfo): Promise<void> => {
          executedMethods.push('onStart')
        }
      )
      stub(CustomTracer, 'onEnd').callsFake(
        async (_config: BoosterConfig, _actionType: string, _traceInfo: TraceInfo): Promise<void> => {
          executedMethods.push('onEnd')
        }
      )
      Booster.config.traceConfiguration = {
        enableTraceNotification: true,
        includeInternal: false,
        onStart: CustomTracer.onStart,
        onEnd: CustomTracer.onEnd,
      }

      const testClass = new TestClass()
      await testClass.myCustomMethod('test')
      expect(executedMethods).to.have.same.members(['onStart', 'onEnd'])
    })
  })
})

class TestClass {
  public innerField = ''

  @Trace(TraceActionTypes.CUSTOM)
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore
  public async myCustomMethod(param: string): Promise<void> {
    this.innerField = param
  }
}

class CustomTracer {
  static async onStart(_config: BoosterConfig, _actionType: string, _traceInfo: TraceInfo): Promise<void> {}

  static async onEnd(_config: BoosterConfig, _actionType: string, _traceInfo: TraceInfo): Promise<void> {}
}
