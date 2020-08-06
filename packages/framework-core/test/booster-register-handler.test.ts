/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from './expect'
import { Register, BoosterConfig, Level, UserEnvelope } from '@boostercloud/framework-types'
import { replace, fake, restore } from 'sinon'
import { RegisterHandler } from '../src/booster-register-handler'
import { buildLogger } from '../src/booster-logger'
import { spy } from 'sinon'

class SomeEntity {}

class SomeEvent {
  public constructor(readonly someField: string) {}
  entityID() {
    return '42'
  }
}

describe('the `RegisterHandler` class', () => {
  const logger = buildLogger(Level.debug)

  afterEach(() => {
    restore()
  })

  it('handles a register', async () => {
    const config = new BoosterConfig('test')
    config.provider = {
      events: {
        store: fake(),
      },
    } as any
    config.reducers['SomeEvent'] = { class: SomeEntity, methodName: 'whatever' }

    const register = new Register('1234')
    const event1 = new SomeEvent('a')
    const event2 = new SomeEvent('b')
    register.events(event1, event2)

    const registerHandler = RegisterHandler as any
    spy(registerHandler, 'wrapEvent')

    await RegisterHandler.handle(config, logger, register)

    expect(registerHandler.wrapEvent).to.have.been.calledTwice
    expect(registerHandler.wrapEvent).to.have.been.calledWith(register, config, event1)
    expect(registerHandler.wrapEvent).to.have.been.calledWith(register, config, event2)
    expect(config.provider.events.store).to.have.been.calledOnce
  })

  it('does nothing when there are no events', async () => {
    const config = new BoosterConfig('test')
    config.provider = {
      events: {
        store: fake(),
      },
    } as any
    config.reducers['SomeEvent'] = { class: SomeEntity, methodName: 'whatever' }

    const register = new Register('1234')
    await RegisterHandler.handle(config, logger, register)

    expect(config.provider.events.store).to.not.have.been.called
  })

  it('stores wrapped events', async () => {
    const config = new BoosterConfig('test')
    config.provider = {
      events: {
        store: fake(),
      },
    } as any
    config.reducers['SomeEvent'] = {
      class: SomeEntity,
      methodName: 'aReducer',
    }

    replace(Date.prototype, 'toISOString', fake.returns('just the right time'))

    const register = new Register('1234')
    const event1 = new SomeEvent('a')
    const event2 = new SomeEvent('b')
    register.events(event1, event2)

    await RegisterHandler.handle(config, logger, register)

    expect(config.provider.events.store).to.have.been.calledOnce
    expect(config.provider.events.store).to.have.been.calledWithMatch(
      [
        {
          createdAt: 'just the right time',
          currentUser: undefined,
          entityID: '42',
          entityTypeName: 'SomeEntity',
          kind: 'event',
          requestID: '1234',
          typeName: 'SomeEvent',
          value: event1,
          version: 1,
        },
        {
          createdAt: 'just the right time',
          currentUser: undefined,
          entityID: '42',
          entityTypeName: 'SomeEntity',
          kind: 'event',
          requestID: '1234',
          typeName: 'SomeEvent',
          value: event2,
          version: 1,
        },
      ],
      config,
      logger
    )
  })

  it('can wrap events to produce eventEnvelopes', () => {
    const config = new BoosterConfig('test')
    config.reducers['SomeEvent'] = {
      class: SomeEntity,
      methodName: 'someReducer',
    }
    const user: UserEnvelope = {
      username: 'paco@example.com',
      role: 'Paco',
    }
    const register = new Register('1234', user)
    const event = new SomeEvent('a')
    replace(Date.prototype, 'toISOString', fake.returns('right here, right now!'))

    const registerHandler = RegisterHandler as any
    expect(registerHandler.wrapEvent(register, config, event)).to.deep.equal({
      version: 1,
      kind: 'event',
      entityID: '42',
      requestID: '1234',
      entityTypeName: 'SomeEntity',
      value: event,
      createdAt: 'right here, right now!',
      currentUser: user,
      typeName: 'SomeEvent',
    })
  })
})
