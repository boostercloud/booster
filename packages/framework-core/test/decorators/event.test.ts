/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Event } from '../../src/decorators'
import {UUID} from '@boostercloud/framework-types'
import {Booster} from '../../src'

describe('the `Event` decorator', () => {
  it('does nothing, but can be used', () => {
      @Event
      class AnEvent {
          public constructor(readonly foo: string) {}
          public entityID(): UUID {
              return '123'
          }
      }
    // We add the 'foo' field and use it here so the TS compiler doesn't complain
    // about it being unused.
      expect(Booster.config.events['AnEvent']).to.deep.equal({
          class: AnEvent,
      })
  })
})
