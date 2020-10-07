/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-unused-vars */
import { expect } from '../expect'
import { EventHandler } from '../../src/decorators'
import { Booster } from '../../src'
import { Event } from '../../src/decorators'
import { UUID, Register, BoosterConfig } from '@boostercloud/framework-types'

describe('the `EventHandler` decorator', () => {
  afterEach(() => {
    Booster.configureCurrentEnv((config: BoosterConfig) => {
      for (const propName in config.eventHandlers) {
        delete config.eventHandlers[propName]
      }
    })
  })

  it('registers the event handler class as an event handler in Booster configuration', () => {
    @Event
    class SomeEvent {
      public entityID(): UUID {
        return '123'
      }
    }

    @EventHandler(SomeEvent)
    class SomeEventHandler {
      public static handle(_event: SomeEvent, _register: Register): Promise<void> {
        return Promise.resolve()
      }
    }

    const booster = Booster as any
    const someEventHandlers = booster.config.eventHandlers['SomeEvent']

    expect(someEventHandlers).to.be.an('Array')
    expect(someEventHandlers).to.contain(SomeEventHandler)
  })
})
