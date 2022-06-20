/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Register } from '@boostercloud/framework-types'
import { Command } from '../../src/decorators'
import { Booster } from '../../src'
import { fake } from 'sinon'
import { BoosterAuthorizer } from '../../src/booster-authorizer'

describe('the `Command` decorator', () => {
  afterEach(() => {
    const booster = Booster as any
    delete booster.config.commandHandlers['PostComment']
  })

  context('when an authorizer function is provided', () => {
    const fakeCommandAuthorizer = fake.resolves(undefined)
    @Command({ authorize: fakeCommandAuthorizer })
    class PostComment {
      public constructor(readonly comment: string) {}
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      public static async handle(_command: PostComment, _register: Register): Promise<void> {
        throw new Error('Not implemented')
      }
    }

    it('injects the command handler metadata in the Booster configuration with the provided authorizer function', () => {
      // Make Booster be of any type to access private members
      const booster = Booster as any
      const commandMetadata = booster.config.commandHandlers[PostComment.name]

      expect(commandMetadata).to.be.an('object')
      expect(commandMetadata.class).to.equal(PostComment)
      expect(commandMetadata.properties).to.include({ name: 'comment', typeInfo: 'string' })
      expect(commandMetadata.methods).to.include({ name: 'handle', typeInfo: 'Promise<void>' })
      expect(commandMetadata.authorizer).to.equal(fakeCommandAuthorizer)
      expect(commandMetadata.before).to.be.an('Array')
      expect(commandMetadata.before).to.be.empty
    })
  })

  context('when an authorizer function is not provided', () => {
    @Command({})
    class PostComment {
      public constructor(readonly comment: string) {}
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      public static async handle(_command: PostComment, _register: Register): Promise<void> {
        throw new Error('Not implemented')
      }
    }

    it('injects the command handler metadata in the Booster configuration and denies access', () => {
      // Make Booster be of any type to access private members
      const booster = Booster as any
      const commandMetadata = booster.config.commandHandlers[PostComment.name]

      expect(commandMetadata).to.be.an('object')
      expect(commandMetadata.class).to.equal(PostComment)
      expect(commandMetadata.properties).to.include({ name: 'comment', typeInfo: 'string' })
      expect(commandMetadata.methods).to.include({ name: 'handle', typeInfo: 'Promise<void>' })
      expect(commandMetadata.authorizer).to.equal(BoosterAuthorizer.denyAccess)
      expect(commandMetadata.before).to.be.an('Array')
      expect(commandMetadata.before).to.be.empty
    })
  })

  context('when a `before` hook is provided', () => {
    const fakeBeforeHook = fake.resolves(undefined)
    @Command({ before: [fakeBeforeHook] })
    class PostComment {
      public constructor(readonly comment: string) {}
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      public static async handle(_command: PostComment, _register: Register): Promise<void> {
        throw new Error('Not implemented')
      }
    }

    it('injects the command handler metadata in the Booster configuration with the provided before hook', () => {
      // Make Booster be of any type to access private members
      const booster = Booster as any
      const commandMetadata = booster.config.commandHandlers[PostComment.name]

      expect(commandMetadata).to.be.an('object')
      expect(commandMetadata.class).to.equal(PostComment)
      expect(commandMetadata.properties).to.include({ name: 'comment', typeInfo: 'string' })
      expect(commandMetadata.methods).to.include({ name: 'handle', typeInfo: 'Promise<void>' })
      expect(commandMetadata.authorizer).to.equal(BoosterAuthorizer.denyAccess)
      expect(commandMetadata.before).to.be.an('Array')
      expect(commandMetadata.before).to.include(fakeBeforeHook)
    })
  })
})
