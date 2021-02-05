/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { fake, replace, restore, match, spy } from 'sinon'
import { expect } from './expect'
import { BoosterCommandDispatcher } from '../src/booster-command-dispatcher'
import { BoosterConfig, Logger, Register, UUID } from '@boostercloud/framework-types'
import { RegisterHandler } from '../src/booster-register-handler'

describe('the `BoosterCommandsDispatcher`', () => {
  afterEach(() => {
    restore()
  })

  const logger: Logger = {
    debug() {},
    info() {},
    error() {},
  }

  describe('private methods', () => {
    describe('the `dispatchCommand` method', () => {
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

      it('calls the handler method of a registered command and handles the result', async () => {
        class PostComment {
          public constructor(readonly comment: string) {}
          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          public static async handle(_command: PostComment, _register: Register): Promise<void> {
            throw new Error('Not implemented')
          }
        }

        const config = new BoosterConfig('test') as any
        config.commandHandlers = {
          PostComment: {
            class: PostComment,
            properties: { name: 'comment', type: String },
            authorizedRoles: 'all',
          },
        }

        const fakeHandler = fake()
        replace(PostComment, 'handle', fakeHandler)
        replace(RegisterHandler, 'handle', fake())

        const command = new PostComment('This test is good!')

        await new BoosterCommandDispatcher(config, logger).dispatchCommand({
          requestID: '1234',
          version: 1,
          typeName: 'PostComment',
          value: command,
        })

        expect(fakeHandler).to.have.been.calledOnceWith(command)
        expect(RegisterHandler.handle).to.have.been.calledOnceWith(config, logger, match.instanceOf(Register))
      })

      it('properly handle the registered events', async () => {
        class SomethingHappened {
          public constructor(readonly when: string) {}
          public entityID() {
            return UUID.generate()
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
        class PostComment {
          public constructor(readonly comment: string) {}
          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          public static async handle(command: PostComment, _register: Register): Promise<void> {
            await new Promise((resolve) => setTimeout(resolve, 100))
            asyncOperationFinished = true
          }
        }

        const config = new BoosterConfig('test') as any
        config.commandHandlers = {
          PostComment: {
            class: PostComment,
            properties: { name: 'comment', type: String },
            authorizedRoles: 'all',
          },
        }

        const handlerSpy = spy(PostComment, 'handle')
        replace(RegisterHandler, 'handle', fake())

        const command = new PostComment('This test is good!')

        await new BoosterCommandDispatcher(config, logger).dispatchCommand({
          requestID: '1234',
          version: 1,
          typeName: 'PostComment',
          value: command,
        })

        expect(handlerSpy).to.have.been.calledOnceWith(command)
        expect(asyncOperationFinished).to.be.true
      })

      it('fails if the command "version" is not sent', async () => {
        const command = {
          typeName: 'PostComment',
          value: { comment: 'This comment is pointless' },
        }

        const config = new BoosterConfig('test')

        await expect(
          new BoosterCommandDispatcher(config, logger).dispatchCommand(command as any)
        ).to.eventually.be.rejectedWith('The required command "version" was not present')
      })

      it('fails if the command is not registered', async () => {
        const command = {
          version: 1,
          typeName: 'PostComment',
          value: { comment: 'This comment is pointless' },
        }

        const config = new BoosterConfig('test')

        await expect(
          new BoosterCommandDispatcher(config, logger).dispatchCommand(command as any)
        ).to.eventually.be.rejectedWith('Could not find a proper handler for PostComment')
      })
    })
  })
})
