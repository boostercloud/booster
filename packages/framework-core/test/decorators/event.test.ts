/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Event } from '../../src/decorators'

describe('the `Event` decorator', () => {
  it('does nothing, but can be used', () => {
    @Event
    class CommentPosted {
      public static foo = 'foo'
    }

    // We add the 'foo' field and use it here so the TS compiler doesn't complain
    // about it being unused.
    expect(CommentPosted.foo).to.equal('foo')
  })
})
