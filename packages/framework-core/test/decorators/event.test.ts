/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Event } from '../../src/decorators'
import { UUID } from '@boostercloud/framework-types'
import { Booster } from '../../src'

describe('the `Event` decorator', () => {
  it('add the event class as an event', () => {
    @Event
    class AnEvent {
      public constructor(readonly foo: string) {}
      public entityID(): UUID {
        return '123'
      }
    }
    expect(Booster.config.events['AnEvent']).to.deep.equal({
      class: AnEvent,
    })
  })
})
