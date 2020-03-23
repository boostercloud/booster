/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from 'chai'
import { Event, Entity, Reduces } from '../../src/decorators/'
import { Booster } from '../../src'
import { UUID } from '@boostercloud/framework-types'

describe('the `Entity` decorator', () => {
  process.env.BOOSTER_ENV = 'test'
  afterEach(() => {
    Booster.configure('test', (config) => {
      config.appName = ''
      for (const propName in config.reducers) {
        delete config.reducers[propName]
      }
      for (const propName in config.entities) {
        delete config.entities[propName]
      }
    })
  })

  it('adds the entity class as an entity that reduces some comments', () => {
    @Event
    class CommentPosted {
      public constructor(readonly foo: string) {}
      public entityID(): UUID {
        return '123'
      }
    }

    @Entity
    class Comment {
      public constructor(readonly id: UUID, readonly content: string) {}

      @Reduces(CommentPosted)
      public static react(_event: CommentPosted): Comment {
        throw new Error('Not implemented')
      }
    }

    // Make Booster be of any type to access private members
    const booster = Booster as any

    expect(booster.config.entities['Comment']).to.deep.equal({
      class: Comment,
    })
    expect(booster.config.reducers['CommentPosted']).to.deep.include({
      class: Comment,
      methodName: 'react',
    })
  })
})
