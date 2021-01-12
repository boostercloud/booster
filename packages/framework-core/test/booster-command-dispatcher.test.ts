/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { fake, replace, restore, match, spy } from 'sinon'
import { expect } from './expect'
import { BoosterCommandDispatcher } from '../src/booster-command-dispatcher'
import { BoosterConfig, Logger, Register } from '@boostercloud/framework-types'
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
