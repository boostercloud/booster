/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from 'chai'
import { Register } from '@boostercloud/framework-types'
import { Command } from '../../src/decorators/command'
import { Booster } from '../../src/index'

describe('the `Command` decorator', () => {
  afterEach(() => {
    Booster.configure((config) => {
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
      public handle(_register: Register): void {
        throw new Error('Not implemented')
      }
    }

    // Make Booster be of any type to access private members
    const booster = Booster as any

    expect(booster.config.commandHandlers[PostComment.constructor.name]).to.contain(PostComment)
  })
})
