/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { Booster } from '../src/booster'
import { fake, replace, restore } from 'sinon'
import { expect } from './expect'
import { BoosterCommandDispatcher } from '../src/booster-command-dispatcher'
import {CommandBeforeFunction, Logger, Register} from '@boostercloud/framework-types'
import { Command } from '../src/decorators'
import { RegisterHandler } from '../src/booster-register-handler'
import { random } from 'faker'

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

  const logger: Logger = {
    debug() {},
    info() {},
    error() {},
  }

  describe('the `dispatchCommand` method', () => {
    it('fails if the command "version" is not sent', () => {
      const command = {
        typeName: 'PostComment',
        value: { comment: 'This comment is pointless' },
      }
      Booster.configure('test', async (config) => {
        await expect(
          new BoosterCommandDispatcher(config, logger).dispatchCommand(command as any)
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
          new BoosterCommandDispatcher(config, logger).dispatchCommand(command as any)
        ).to.be.eventually.rejectedWith('Could not find a proper handler for PostComment')
      })
    })

    it('fails if the current user is not authorized', async () => {
      class Thor {}

      const config = {
        commandHandlers: {
          UnauthorizedCommand: {
            authorizedRoles: [Thor],
          },
        },
      }

      const commandEnvelope = {
        typeName: 'UnauthorizedCommand',
        version: 'π', // JS doesn't care, and π is a number after all xD...
        currentUser: {
          role: 'Loki',
        },
      }

      await expect(
        new BoosterCommandDispatcher(config as any, logger).dispatchCommand(commandEnvelope as any)
      ).to.be.eventually.rejectedWith("Access denied for command 'UnauthorizedCommand'")
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
            authorizedRoles: 'all',
            before: [],
            class: ProperlyHandledCommand,
          },
        },
      }
      const commandValue = {
        something: 'to handle',
      }

      const commandEnvelope = {
        typeName: 'ProperlyHandledCommand',
        version: 'π', // JS doesn't care, and π is a number after all xD...
        currentUser: {
          role: 'Loki',
        },
        value: commandValue,
        requestID: '42',
      }

      await new BoosterCommandDispatcher(config as any, logger).dispatchCommand(commandEnvelope as any)

      expect(fakeHandler).to.have.been.calledWithMatch(commandValue)
    })

    it('properly handle the registered events', async () => {
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

      replace(ProperlyHandledCommand, 'handle', fakeHandler)
      replace(RegisterHandler, 'handle', fake())

      const config = {
        commandHandlers: {
          ProperlyHandledCommand: {
            authorizedRoles: 'all',
            before: [],
            class: ProperlyHandledCommand,
          },
        },
      }
      const commandValue = {
        something: 'to handle',
      }

      const commandEnvelope = {
        typeName: 'ProperlyHandledCommand',
        version: 'π', // JS doesn't care, and π is a number after all xD...
        currentUser: {
          role: 'Loki',
        },
        value: commandValue,
        requestID: '42',
      }

      await new BoosterCommandDispatcher(config as any, logger).dispatchCommand(commandEnvelope as any)

      expect(fakeHandler).to.have.been.calledWithMatch(commandValue)
      expect(RegisterHandler.handle).to.have.been.calledWithMatch(config, logger, {
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

      await new BoosterCommandDispatcher(boosterConfig, logger).dispatchCommand({
        requestID: '1234',
        version: 1,
        typeName: 'PostComment',
        value: command,
      })
      expect(asyncOperationFinished).to.be.true
    })

    context('when before hook functions are passed', () => {
      const newComment = 'Look, I changed the message'
      const newCommentV2 = 'Yes, I changed it for a second time'
      const beforeFn: CommandBeforeFunction = (input, currentUser) => {
        input.comment = newComment
        return input
      }
      const beforeFnV2: CommandBeforeFunction = (input, currentUser) => {
        // To double-check it's really chained
        if (input.comment === newComment) input.comment = newCommentV2
        return input
      }

      it('transforms the input if a before hook function is passed', async () => {
        let transformedInput = {}
        @Command({ authorize: 'all', beforeCommand: [beforeFn] })
        class PostComment {
          public constructor(readonly comment: string) {}
          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          public static async handle(command: PostComment, _register: Register): Promise<void> {
            transformedInput = command
          }
        }

        const command = new PostComment('This test is good!')
        replace(RegisterHandler, 'handle', fake())

        let boosterConfig: any
        Booster.configure('test', (config) => {
          boosterConfig = config
        })

        await new BoosterCommandDispatcher(boosterConfig, logger).dispatchCommand({
          requestID: '1234',
          version: 1,
          typeName: 'PostComment',
          value: command,
        })

        expect(transformedInput).to.deep.equal({ comment: newComment } as PostComment)
      })

      it('transforms the input when more than one before hook function is passed', async () => {
        let transformedInput = {}
        @Command({ authorize: 'all', beforeCommand: [beforeFn, beforeFnV2] })
        class PostComment {
          public constructor(readonly comment: string) {}
          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          public static async handle(command: PostComment, _register: Register): Promise<void> {
            transformedInput = command
          }
        }

        const command = new PostComment('This test is good!')
        replace(RegisterHandler, 'handle', fake())

        let boosterConfig: any
        Booster.configure('test', (config) => {
          boosterConfig = config
        })

        await new BoosterCommandDispatcher(boosterConfig, logger).dispatchCommand({
          requestID: '1234',
          version: 1,
          typeName: 'PostComment',
          value: command,
        })

        expect(transformedInput).to.deep.equal({ comment: newCommentV2 } as PostComment)
      })
    })
  })
})
