/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Register } from '@boostercloud/framework-types'
import { Command } from '../../src/decorators'
import { Booster } from '../../src'

describe('the `Command` decorator', () => {
  afterEach(() => {
    Booster.configure('test', (config) => {
      config.appName = ''
      for (const propName in config.commandHandlers) {
        delete config.commandHandlers[propName]
      }
    })
  })

  it('adds the command class as a command handler for some command in the Booster configuration', () => {
    // Register command
    @Command({ authorize: 'all' })
    class PostComment {
      public constructor(readonly comment: string) {}
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      public static async handle(_command: PostComment, _register: Register): Promise<void> {
        throw new Error('Not implemented')
      }
    }

    Booster.configureCurrentEnv((config) => {
      expect(config.commandHandlers[PostComment.constructor.name]).to.contain(PostComment)
    })
  })
})
