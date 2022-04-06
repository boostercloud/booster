/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { Booster } from '../src/booster'
import { fake, replace, restore } from 'sinon'
import { expect } from './expect'
import { BoosterCommandDispatcher } from '../src/booster-command-dispatcher'
import { CommandAfterFunction, CommandBeforeFunction, Logger, Register } from '@boostercloud/framework-types'
import { Command, RegisterHandler } from '../src'
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
    warn() {},
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
          roles: ['Loki'],
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
            after: [],
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
          roles: ['Loki'],
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
            after: [],
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
          roles: ['Loki'],
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
      const beforeFn: CommandBeforeFunction = async (input, register) => {
        input.comment = newComment
        const result = await Promise.resolve()
        console.log(result)
        return input
      }
      const beforeFnV2: CommandBeforeFunction = async (input, register) => {
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

        await new BoosterCommandDispatcher(boosterConfig, logger).dispatchCommand({
          requestID: '1234',
          version: 1,
          typeName: 'PostComment',
          value: command,
        })

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

        await new BoosterCommandDispatcher(boosterConfig, logger).dispatchCommand({
          requestID: '1234',
          version: 1,
          typeName: 'PostComment',
          value: command,
        })

        expect(transformedInput).to.deep.equal(new PostComment(newCommentV2))
      })
    })

    context('when after hook functions are passed', () => {
      it('call after if an after hook function is passed', async () => {
        let expectedResult = 0

        const afterFn: CommandAfterFunction = async (previousResult, input, register) => {
          expectedResult = 1
          await Promise.resolve()
        }

        @Command({ authorize: 'all', after: [afterFn] })
        class PostComment {
          public constructor(readonly comment: string) {}
          public static async handle(command: PostComment): Promise<void> {}
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

        expect(expectedResult).to.be.eq(1)
      })

      it('call register more than one when more than one after hook function is passed', async () => {
        let expectedResult = 0

        const afterFn: CommandAfterFunction = async (previousResult, input, register) => {
          expectedResult = 2
          await Promise.resolve()
        }

        const afterFn2: CommandAfterFunction = async (previousResult, input, register) => {
          expectedResult = expectedResult * 3
          await Promise.resolve()
        }

        @Command({ authorize: 'all', after: [afterFn, afterFn2] })
        class PostComment {
          public constructor(readonly comment: string) {}
          public static async handle(command: PostComment): Promise<void> {}
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

        expect(expectedResult).to.be.eq(6)
      })
    })

    context('when there is an error the onError is processed', () => {
      it('call the onError method when there is an error on handle method', async () => {
        const onErrorFn = fake.rejects('Error on errorFn')

        @Command({ authorize: 'all', onError: onErrorFn })
        class PostComment {
          public constructor(readonly comment: string) {}
          public static async handle(command: PostComment): Promise<void> {
            await Promise.reject('Error on command')
          }
        }

        const command = new PostComment('This test is good!')

        let boosterConfig: any
        Booster.configure('test', (config) => {
          boosterConfig = config
        })

        await expect(
          new BoosterCommandDispatcher(boosterConfig, logger).dispatchCommand({
            requestID: '1234',
            version: 1,
            typeName: 'PostComment',
            value: command,
          })
        ).to.be.eventually.rejectedWith('Error on errorFn')
        expect(onErrorFn).calledOnceWith('Error on command')
      })
    })
  })
})
