/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Booster, UnknownEvent } from '../../src'
import { EventInterface, UUID } from '@boostercloud/framework-types'

describe('the `UnknownEvent` decorator', () => {
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

  it('setup the unknownEvent to the method of the annotated class', () => {
    class EventUnknownEvents {
      public constructor(readonly id: UUID, readonly content: string) {}

      @UnknownEvent()
      public static unknownEvent(event: EventInterface): void {
        throw new Error('Not implemented')
      }
    }

    expect(Booster.config.unknownEvent).to.deep.include({
      class: EventUnknownEvents,
      methodName: 'unknownEvent',
    })
  })
})
