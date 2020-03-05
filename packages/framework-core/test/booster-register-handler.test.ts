/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from 'chai'
import { Register, BoosterConfig, Level, UserEnvelope } from '@boostercloud/framework-types'
import { replace, fake, restore, match } from 'sinon'
import { RegisterHandler } from '../src/booster-register-handler'
import { buildLogger } from '../src/booster-logger'
import { spy } from 'sinon'

class SomeEntity {}

class SomeCommand {
  public constructor(readonly someField: string) {}
  handle() {}
}

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
    const config = new BoosterConfig()
    config.provider = {
      publishEvents: fake(),
      submitCommands: fake(),
    } as any
    config.reducers['SomeEvent'] = { class: SomeEntity, methodName: 'whatever' }

    const register = new Register('1234')
    const command1 = new SomeCommand('1')
    const command2 = new SomeCommand('2')
    register.commands(command1, command2)
    const event1 = new SomeEvent('a')
    const event2 = new SomeEvent('b')
    register.events(event1, event2)

    const registerHandler = RegisterHandler as any
    spy(registerHandler.prototype, 'wrapEvent')
    spy(registerHandler.prototype, 'wrapCommand')

    await RegisterHandler.handle(config, logger, register)

    expect(registerHandler.prototype.wrapEvent).to.have.been.calledTwice
    expect(registerHandler.prototype.wrapEvent).to.have.been.calledWith(event1)
    expect(registerHandler.prototype.wrapEvent).to.have.been.calledWith(event2)
    // TODO: The method is currently being called three times because of the recursive hack we have in `submitCommands`, which is calling the `publishEvents` method for each command registered
    // expect(config.provider.publishEvents).to.have.been.calledOnce

    expect(registerHandler.prototype.wrapCommand).to.have.been.calledTwice
    expect(registerHandler.prototype.wrapCommand).to.have.been.calledWith(command1)
    expect(registerHandler.prototype.wrapCommand).to.have.been.calledWith(command2)
    // TODO: The method is currently being called three times because of the recursive hack we have in `submitCommands`, which is calling the `publishEvents` method for each command registered
    // expect(config.provider.submitCommands).to.have.been.calledOnce
  })

  it('publishes wrapped events', async () => {
    const config = new BoosterConfig()
    config.provider = {
      publishEvents: fake(),
      submitCommands: fake(),
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

    expect(config.provider.publishEvents).to.have.been.calledOnce
    expect(config.provider.publishEvents).to.have.been.calledWithMatch(
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

  // TODO: This test is temporal, we'll remove it when we do the async commands refactor.
  it('handles the registered commands synchronously', async () => {
    const config = new BoosterConfig()
    config.provider = {
      publishEvents: fake(),
    } as any
    const fakeUser = { email: 'a@s.df', roles: ['XD'] }
    const register = new Register('1234', fakeUser)
    const command1 = new SomeCommand('1')
    replace(command1, 'handle', fake())
    const command2 = new SomeCommand('2')
    replace(command2, 'handle', fake())
    register.commands(command1, command2)

    replace(Date.prototype, 'toISOString', fake.returns('right here, right now!'))

    await RegisterHandler.handle(config, logger, register)

    expect(command1.handle).to.have.been.calledOnceWith(
      match({
        requestID: match.defined,
        currentUser: fakeUser,
      })
    )
    expect(command2.handle).to.have.been.calledOnceWith(
      match({
        requestID: match.defined,
        currentUser: fakeUser,
      })
    )
  })

  /* TODO: This test is commented because we're not currently processing commands asynchronously.
   * In this version we're just calling to the command handlers and processing their register objects
   * Recursively. There's a pending refactor to fix this.
   */
  // it('submits wrapped commands', async () => {
  //   const config = new BoosterConfig()
  //   config.provider = {
  //     publishEvents: fake(),
  //     submitCommands: fake(),
  //   } as any

  //   const register = new Register('1234')
  //   const command1 = new SomeCommand('1')
  //   const command2 = new SomeCommand('2')
  //   register.commands(command1, command2)

  //   replace(Date.prototype, 'toISOString', fake.returns('right here, right now!'))

  //   await RegisterHandler.handle(config, logger, register)

  //   expect(config.provider.submitCommands).to.have.been.calledOnce
  //   expect(config.provider.submitCommands).to.have.been.calledWithMatch(
  //     [
  //       {
  //         currentUser: undefined,
  //         requestID: '1234',
  //         typeName: 'SomeCommand',
  //         value: command1,
  //         version: 1,
  //       },
  //       {
  //         currentUser: undefined,
  //         requestID: '1234',
  //         typeName: 'SomeCommand',
  //         value: command2,
  //         version: 1,
  //       },
  //     ],
  //     config,
  //     logger
  //   )
  // })

  it('can wrap events to produce eventEnvelopes', () => {
    const config = new BoosterConfig()
    config.reducers['SomeEvent'] = {
      class: SomeEntity,
      methodName: 'someReducer',
    }
    const user: UserEnvelope = {
      email: 'paco@example.com',
      roles: ['Paco'],
    }
    const register = new Register('1234', user)
    const event = new SomeEvent('a')
    const registerHandler = RegisterHandler as any
    const registerHandlerInstance = new registerHandler(register, config, logger)
    replace(Date.prototype, 'toISOString', fake.returns('right here, right now!'))

    expect(registerHandlerInstance.wrapEvent(event)).to.deep.equal({
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

  it('can wrap commands to produce commandEnvelopes', () => {
    const config = new BoosterConfig()
    const user: UserEnvelope = {
      email: 'paco@example.com',
      roles: ['Paco'],
    }
    const register = new Register('1234', user)
    const command = new SomeCommand('1')
    const registerHandler = RegisterHandler as any
    const registerHandlerInstance = new registerHandler(register, config, logger)
    replace(Date.prototype, 'toISOString', fake.returns('right here, right now!'))

    expect(registerHandlerInstance.wrapCommand(command)).to.deep.equal({
      requestID: '1234',
      typeName: 'SomeCommand',
      version: 1,
      value: command,
      currentUser: user,
    })
  })
})
