/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { Booster } from '../src/booster'
import { fake, replace, restore, match } from 'sinon'
import * as chai from 'chai'
import { expect } from 'chai'
import { BoosterCommandDispatcher } from '../src/booster-command-dispatcher'
import { BoosterConfig, Logger, Register } from '@boostercloud/framework-types'
import { Command } from '../src/decorators'
import { ProviderLibrary } from '@boostercloud/framework-types'
import { RegisterHandler } from '../src/booster-register-handler'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

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

  describe('the `dispatch` method', () => {
    it('dispatches the command when there are no errors', async () => {
      const config = new BoosterConfig('test')
      config.provider = ({
        rawCommandToEnvelope: fake.resolves({}),
        requestSucceeded: fake(),
      } as unknown) as ProviderLibrary

      const boosterCommandDispatcher = new BoosterCommandDispatcher(config, logger)
      replace(boosterCommandDispatcher, 'dispatchCommand', fake())

      await boosterCommandDispatcher.dispatch({ body: 'Test body' })

      expect(config.provider.rawCommandToEnvelope).to.have.been.calledOnce
      expect(boosterCommandDispatcher.dispatchCommand).to.have.been.calledOnce
      expect(config.provider.requestSucceeded).to.have.been.calledOnce
    })

    it('builds and returns a failure response when there were errors', async () => {
      const omgError = new Error('OMG!!!')
      const config = new BoosterConfig('test')
      config.provider = {
        rawCommandToEnvelope: fake.resolves({}),
        requestFailed: fake(),
      } as any
      const boosterCommandDispatcher = new BoosterCommandDispatcher(config, logger)
      replace(boosterCommandDispatcher, 'dispatchCommand', fake.throws(omgError))
      replace(RegisterHandler, 'handle', fake())

      await boosterCommandDispatcher.dispatch({ body: 'Test body' })

      expect(config.provider.rawCommandToEnvelope).to.have.been.calledOnce
      expect(boosterCommandDispatcher.dispatchCommand).to.have.been.calledOnce
      expect(RegisterHandler.handle).not.to.have.been.called
      expect(config.provider.requestFailed).to.have.been.calledOnceWithExactly(omgError)
    })
  })

  describe('private methods', () => {
    describe('the `dispatchCommand` method', () => {
      it('calls the handler method of a registered command', () => {
        @Command({ authorize: 'all' })
        class PostComment {
          public constructor(readonly comment: string) {}
          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          public handle(_register: Register): void {
            throw new Error('Not implemented')
          }
        }

        const fakeHandler = fake()
        const command = new PostComment('This test is good!')
        replace(command, 'handle', fakeHandler)
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
