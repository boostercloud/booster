/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Booster } from '../../src'
import { EventInterface, UUID } from '@boostercloud/framework-types'
import { UnknownReducer } from '../../src/decorators/unknown-reducer'

describe('the `UnknownReducer` decorator', () => {
  afterEach(() => {
    Booster.configure('test', (config) => {
      config.appName = ''
      for (const propName in config.reducers) {
        delete config.reducers[propName]
      }
      for (const propName in config.entities) {
        delete config.entities[propName]
      }
      for (const propName in config.roles) {
        delete config.roles[propName]
      }
      delete config.unknownReducerHandler
    })
  })

  it('setup the unknownReducerHandler to the method of the annotated class', () => {
    class ReducerUnknownEvents {
      public constructor(readonly id: UUID, readonly content: string) {}

      @UnknownReducer()
      public static unknownReducer(event: EventInterface):  void {
        throw new Error('Not implemented')
      }
    }

    expect(Booster.config.unknownReducerHandler).to.deep.include({
      class: ReducerUnknownEvents,
      methodName: 'unknownReducer',
    })
  })

})
