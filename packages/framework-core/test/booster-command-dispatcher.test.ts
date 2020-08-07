/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { Booster } from '../src/booster'
import { fake, replace, restore, match } from 'sinon'
import { expect } from './expect'
import { BoosterCommandDispatcher } from '../src/booster-command-dispatcher'
import { Logger, Register } from '@boostercloud/framework-types'
import { Command } from '../src/decorators'
import { RegisterHandler } from '../src/booster-register-handler'

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

  describe('private methods', () => {
    describe('the `dispatchCommand` method', () => {
      it('calls the handler method of a registered command', () => {
        @Command({ authorize: 'all' })
        class PostComment {
          public constructor(readonly comment: string) {}
          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          public static async handle(_command: PostComment, _register: Register): Promise<void> {
            throw new Error('Not implemented')
          }
        }

        const fakeHandler = fake()
        const command = new PostComment('This test is good!')
        replace(RegisterHandler, 'handle', fake())

        Booster.configure(
          'test',
          async (config): Promise<void> => {
            await new BoosterCommandDispatcher(config, logger).dispatchCommand({
              requestID: '1234',
              version: 1,
              typeName: 'PostComment',
              value: command,
            })
            expect(fakeHandler).to.have.been.calledOnce
            expect(RegisterHandler.handle).to.have.been.calledOnceWith(config, logger, match.instanceOf(Register))
          }
        )
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

      it('fails if the command "version" is not sent', () => {
        const command = {
          typeName: 'PostComment',
          value: { comment: 'This comment is pointless' },
        }
        Booster.configure('test', async (config) => {
          // We use `bind` to generate a thunk that chai will then call, checking that it throws
          await expect(
            new BoosterCommandDispatcher(config, logger).dispatchCommand(command as any)
          ).to.eventually.throw('The required command "version" was not present')
        })
      })

      it('fails if the command is not registered', () => {
        const command = {
          version: 1,
          typeName: 'PostComment',
          value: { comment: 'This comment is pointless' },
        }
        Booster.configure('test', async (config) => {
          // We use `bind` to generate a thunk that chai will then call, checking that it throws
          await expect(
            new BoosterCommandDispatcher(config, logger).dispatchCommand(command as any)
          ).to.eventually.throw('Could not find a proper handler for PostComment')
        })
      })
    })
  })
})
