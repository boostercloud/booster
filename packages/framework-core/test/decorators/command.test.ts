/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Register } from '@boostercloud/framework-types'
import { Command, Booster } from '../../src'

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

    // Make Booster be of any type to access private members
    const booster = Booster as any

    expect(booster.config.commandHandlers[PostComment.constructor.name]).to.contain(PostComment)
    expect(booster.config.commandHandlers['PostComment']).to.be.deep.equal({
      class: PostComment,
      authorizedRoles: 'all',
      before: [],
      after: [],
      onError: undefined,
      returnClass: Boolean,
      properties: [
        {
          name: 'comment',
          typeInfo: {
            name: 'String',
            parameters: [],
            type: String,
          },
        },
      ],
    })
  })
})
