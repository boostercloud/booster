/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { Booster } from '../src/booster'
import { fake, replace, restore, spy } from 'sinon'
import { expect } from './expect'
import { BoosterCommandDispatcher } from '../src/booster-command-dispatcher'
import { CommandBeforeFunction, Register, NotAuthorizedError } from '@boostercloud/framework-types'
import { Command, RegisterHandler } from '../src'
import { random } from 'faker'
import { BoosterAuthorizer } from '../src/booster-authorizer'

describe('the `BoosterCommandsDispatcher`', () => {
  afterEach(() => {
    restore()
    Booster.configure('test', (config) => {
      config.appName = ''
      for (const propName in config.commandHandlers) {
        delete config.commandHandlers[propName]
      }
    })
  })

  describe('the `dispatchCommand` method', () => {
    it('fails if the command "version" is not sent', () => {
      const command = {
        typeName: 'PostComment',
        value: { comment: 'This comment is pointless' },
      }
      Booster.configure('test', async (config) => {
        await expect(
          new BoosterCommandDispatcher(config).dispatchCommand(command as any, {} as any)
        ).to.be.eventually.rejectedWith('The required command "version" was not present')
      })
    })

    it('fails if the command is not registered', () => {
      const command = {
        version: 1,
        typeName: 'PostComment',
        value: { comment: 'This comment is pointless' },
      }
      Booster.configure('test', async (config) => {
        await expect(
          new BoosterCommandDispatcher(config).dispatchCommand(command as any, {} as any)
        ).to.be.eventually.rejectedWith('Could not find a proper handler for PostComment')
      })
    })

    it('fails if the current user is not authorized', async () => {
      class Thor {}

      const config = {
        commandHandlers: {
          UnauthorizedCommand: {
            authorizer: BoosterAuthorizer.authorizeRoles.bind(null, [Thor]),
          },
        },
      }

      const commandEnvelope = {
        typeName: 'UnauthorizedCommand',
        version: 'π', // JS doesn't care, and π is a number after all xD...
        currentUser: {
          roles: ['Loki'],
        },
      }

      await expect(
        new BoosterCommandDispatcher(config as any).dispatchCommand(commandEnvelope as any, {} as any)
      ).to.be.eventually.rejectedWith(NotAuthorizedError)
    })

    it('calls the handler method of a registered command', async () => {
      const fakeHandler = fake()
      class ProperlyHandledCommand {
        public static handle() {}
      }

      replace(ProperlyHandledCommand, 'handle', fakeHandler)
      replace(RegisterHandler, 'handle', fake())

      const config = {
        commandHandlers: {
          ProperlyHandledCommand: {
            authorizer: BoosterAuthorizer.allowAccess,
            before: [],
            class: ProperlyHandledCommand,
          },
        },
        currentVersionFor: fake.returns(1),
      }
      const commandValue = {
        something: 'to handle',
      }

      const commandEnvelope = {
        typeName: 'ProperlyHandledCommand',
        version: 'π', // JS doesn't care, and π is a number after all xD...
        currentUser: {
          roles: ['Loki'],
        },
        value: commandValue,
        requestID: '42',
      }

      await new BoosterCommandDispatcher(config as any).dispatchCommand(commandEnvelope as any, {} as any)

      expect(fakeHandler).to.have.been.calledWithMatch(commandValue)
    })

    it('allows the handler set the responseHeaders', async () => {
      class ProperlyHandledCommand {
        public static handle(command: ProperlyHandledCommand, register: Register) {
          register.responseHeaders['Test-Header'] = 'test'
        }
      }

      spy(ProperlyHandledCommand, 'handle')
      replace(RegisterHandler, 'handle', fake())

      const config = {
        commandHandlers: {
          ProperlyHandledCommand: {
            authorizer: BoosterAuthorizer.allowAccess,
            before: [],
            class: ProperlyHandledCommand,
          },
        },
        currentVersionFor: fake.returns(1),
      }
      const commandValue = {
        something: 'to handle',
      }

      const commandEnvelope = {
        typeName: 'ProperlyHandledCommand',
        version: 'π', // JS doesn't care, and π is a number after all xD...
        currentUser: {
          roles: ['Loki'],
        },
        value: commandValue,
        requestID: '42',
      }

      const context = {
        responseHeaders: {},
      }

      await new BoosterCommandDispatcher(config as any).dispatchCommand(commandEnvelope as any, context as any)

      expect(ProperlyHandledCommand.handle).to.have.been.calledWithMatch(commandValue, { responseHeaders: {} })
      expect(context.responseHeaders).to.deep.equal({ 'Test-Header': 'test' })
    })

    it('properly handles the registered events', async () => {
      class SomethingHappened {
        public constructor(readonly when: string) {}
        public entityID() {
          return random.uuid()
        }
      }

      const event = new SomethingHappened('right now!')

      const fakeHandler = fake((_command: any, register: Register) => {
        register.events(event)
      })

      class ProperlyHandledCommand {
        public static handle() {}
      }

      replace(ProperlyHandledCommand, 'handle', fakeHandler as any)
      replace(RegisterHandler, 'handle', fake())

      const config = {
        commandHandlers: {
          ProperlyHandledCommand: {
            authorizer: BoosterAuthorizer.allowAccess,
            before: [],
            class: ProperlyHandledCommand,
          },
        },
        currentVersionFor: fake.returns(1),
      }
      const commandValue = {
        something: 'to handle',
      }

      const commandEnvelope = {
        typeName: 'ProperlyHandledCommand',
        version: 'π', // JS doesn't care, and π is a number after all xD...
        currentUser: {
          roles: ['Loki'],
        },
        value: commandValue,
        requestID: '42',
      }

      await new BoosterCommandDispatcher(config as any).dispatchCommand(commandEnvelope as any, {} as any)

      expect(fakeHandler).to.have.been.calledWithMatch(commandValue)
      expect(RegisterHandler.handle).to.have.been.calledWithMatch(config, {
        requestID: '42',
        currentUser: commandEnvelope.currentUser,
        eventList: [event],
      })
    })

    it('waits for the handler method of a registered command to finish any async operation', async () => {
      let asyncOperationFinished = false
      @Command({ authorize: 'all' })
      class PostComment {
        public constructor(readonly comment: string) {}
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        public static async handle(command: PostComment, _register: Register): Promise<void> {
          await new Promise((resolve) => setTimeout(resolve, 100))
          asyncOperationFinished = true
        }
      }

      const command = new PostComment('This test is good!')
      replace(RegisterHandler, 'handle', fake())

      let boosterConfig: any
      Booster.configure('test', (config) => {
        boosterConfig = config
      })

      await new BoosterCommandDispatcher(boosterConfig).dispatchCommand(
        {
          requestID: '1234',
          version: 1,
          typeName: 'PostComment',
          value: command,
        },
        {} as any
      )
      expect(asyncOperationFinished).to.be.true
    })

    context('when before hook functions are passed', () => {
      const newComment = 'Look, I changed the message'
      const newCommentV2 = 'Yes, I changed it for a second time'
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const beforeFn: CommandBeforeFunction = async (input, _currentUser) => {
        input.comment = newComment
        const result = await Promise.resolve()
        console.log(result)
        return input
      }
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const beforeFnV2: CommandBeforeFunction = async (input, _currentUser) => {
        // To double-check it's really chained
        if (input.comment === newComment) input.comment = newCommentV2
        const result = await Promise.resolve()
        console.log(result)
        return input
      }

      it('transforms the input if a before hook function is passed', async () => {
        let transformedInput = {}
        @Command({ authorize: 'all', before: [beforeFn] })
        class PostComment {
          public constructor(readonly comment: string) {}
          public static async handle(command: PostComment): Promise<void> {
            transformedInput = command
          }
        }

        const command = new PostComment('This test is good!')
        replace(RegisterHandler, 'handle', fake())

        let boosterConfig: any
        Booster.configure('test', (config) => {
          boosterConfig = config
        })

        await new BoosterCommandDispatcher(boosterConfig).dispatchCommand(
          {
            requestID: '1234',
            version: 1,
            typeName: 'PostComment',
            value: command,
          },
          {} as any
        )

        expect(transformedInput).to.deep.equal(new PostComment(newComment))
      })

      it('transforms the input when more than one before hook function is passed', async () => {
        let transformedInput = {}
        @Command({ authorize: 'all', before: [beforeFn, beforeFnV2] })
        class PostComment {
          public constructor(readonly comment: string) {}
          public static async handle(command: PostComment): Promise<void> {
            transformedInput = command
          }
        }

        const command = new PostComment('This test is good!')
        replace(RegisterHandler, 'handle', fake())

        let boosterConfig: any
        Booster.configure('test', (config) => {
          boosterConfig = config
        })

        await new BoosterCommandDispatcher(boosterConfig).dispatchCommand(
          {
            requestID: '1234',
            version: 1,
            typeName: 'PostComment',
            value: command,
          },
          {} as any
        )

        expect(transformedInput).to.deep.equal(new PostComment(newCommentV2))
      })
    })
  })
})
