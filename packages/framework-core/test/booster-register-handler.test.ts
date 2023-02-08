/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from './expect'
import { Register, BoosterConfig, Level, UserEnvelope, UUID } from '@boostercloud/framework-types'
import { replace, fake, restore, spy } from 'sinon'
import { RegisterHandler } from '../src'
import { BoosterEntityMigrated } from '../src/core-concepts/data-migration/events/booster-entity-migrated'

class SomeEntity {
  public constructor(readonly id: UUID) {}
}

class SomeEvent {
  public constructor(readonly someField: string) {}
  entityID() {
    return '42'
  }
}

class SomeNotification {
  public constructor() {}
}

describe('the `RegisterHandler` class', () => {
  const testConfig = new BoosterConfig('Test')
  testConfig.logLevel = Level.debug

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

    const register = new Register('1234', {} as any, RegisterHandler.flush)
    const event1 = new SomeEvent('a')
    const event2 = new SomeEvent('b')
    register.events(event1, event2)

    const registerHandler = RegisterHandler as any
    spy(registerHandler, 'wrapEvent')

    await RegisterHandler.handle(config, register)

    expect(registerHandler.wrapEvent).to.have.been.calledTwice
    expect(registerHandler.wrapEvent).to.have.been.calledWith(config, event1, register)
    expect(registerHandler.wrapEvent).to.have.been.calledWith(config, event2, register)
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

    const register = new Register('1234', {} as any, RegisterHandler.flush)
    await RegisterHandler.handle(config, register)

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

    const register = new Register('1234', {} as any, RegisterHandler.flush)
    const event1 = new SomeEvent('a')
    const event2 = new SomeEvent('b')
    register.events(event1, event2)

    await RegisterHandler.handle(config, register)

    expect(config.provider.events.store).to.have.been.calledOnce
    expect(config.provider.events.store).to.have.been.calledWithMatch(
      [
        {
          createdAt: 'just the right time',
          currentUser: undefined,
          entityID: '42',
          entityTypeName: 'SomeEntity',
          kind: 'event',
          superKind: 'domain',
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
          superKind: 'domain',
          requestID: '1234',
          typeName: 'SomeEvent',
          value: event2,
          version: 1,
        },
      ],
      config
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
      roles: ['Paco'],
      claims: {},
    }
    const register = new Register('1234', {} as any, RegisterHandler.flush, user)
    const event = new SomeEvent('a')
    replace(Date.prototype, 'toISOString', fake.returns('right here, right now!'))

    const registerHandler = RegisterHandler as any
    expect(registerHandler.wrapEvent(config, event, register)).to.deep.equal({
      version: 1,
      kind: 'event',
      superKind: 'domain',
      entityID: '42',
      requestID: '1234',
      entityTypeName: 'SomeEntity',
      value: event,
      createdAt: 'right here, right now!',
      currentUser: user,
      typeName: 'SomeEvent',
    })
  })

  it('can wrap notifications to produce eventEnvelopes', () => {
    const config = new BoosterConfig('test')
    config.notifications[SomeNotification.name] = {
      class: SomeNotification,
    }

    const user: UserEnvelope = {
      username: 'paco@example.com',
      roles: ['Paco'],
      claims: {},
    }

    const register = new Register('1234', {} as any, RegisterHandler.flush, user)
    const notification = new SomeNotification()
    replace(Date.prototype, 'toISOString', fake.returns('right here, right now!'))

    const registerHandler = RegisterHandler as any
    expect(registerHandler.wrapEvent(config, notification, register)).to.deep.equal({
      version: 1,
      kind: 'event',
      superKind: 'domain',
      entityID: 'default',
      requestID: '1234',
      entityTypeName: 'defaultTopic',
      value: notification,
      createdAt: 'right here, right now!',
      currentUser: user,
      typeName: SomeNotification.name,
    })
  })

  it('can wrap internal events to produce eventEnvelopes', () => {
    const config = new BoosterConfig('test')
    config.reducers['BoosterEntityMigrated'] = {
      class: SomeEntity,
      methodName: 'someReducer',
    }
    const user: UserEnvelope = {
      username: 'paco@example.com',
      roles: ['Paco'],
      claims: {},
    }
    const register = new Register('1234', {}, RegisterHandler.flush, user)
    const someEntity = new SomeEntity('42')
    const event = new BoosterEntityMigrated('oldEntity', 'oldEntityId', 'newEntityName', someEntity)
    replace(Date.prototype, 'toISOString', fake.returns('right here, right now!'))

    const registerHandler = RegisterHandler as any
    expect(registerHandler.wrapEvent(config, event, register)).to.deep.equal({
      version: 1,
      kind: 'event',
      superKind: 'booster',
      entityTypeName: 'oldEntity',
      entityID: 'oldEntityId',
      requestID: '1234',
      value: event,
      createdAt: 'right here, right now!',
      currentUser: user,
      typeName: 'BoosterEntityMigrated',
    })
  })
})
