/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Event, Entity, Reduces, Role } from '../../src/decorators/'
import { Booster } from '../../src'
import { UUID } from '@boostercloud/framework-types'

describe('the `Entity` decorator', () => {
  afterEach(() => {
    Booster.configure('test', (config) => {
      config.appName = ''
      for (const propName in config.reducers) {
        delete config.reducers[propName]
      }
      for (const propName in config.entities) {
        delete config.entities[propName]
      }
      for (const propName in config.roles) {
        delete config.roles[propName]
      }
    })
  })

  it('adds the entity class as an entity that reduces some events', () => {
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

    expect(Booster.config.entities['Comment']).to.deep.equal({
      class: Comment,
      authorizeReadEvents: [],
    })
    expect(Booster.config.reducers['CommentPosted']).to.deep.include({
      class: Comment,
      methodName: 'react',
    })
  })
  it('', () => {
    @Event
    class PersonCreated {
      public constructor(
        readonly personId: UUID,
        readonly firstName: string,
        readonly lastName: string,
        readonly age: number
      ) {}
      public fullName(): string {
        return this.firstName + ' ' + this.lastName
      }
      public entityID(): UUID {
        return this.personId
      }
    }
    @Entity
    class Person {
      public constructor(readonly id: UUID, readonly fullName: string, readonly age: number) {}
      @Reduces(PersonCreated)
      public static reducePersonCreated(event: PersonCreated, currentEntity: Person): Person {
        const fullName = event.fullName()
        return new Person(event.personId, fullName, event.age)
      }
    }
    let exceptionThrown = false
    try {
      new PersonCreated(UUID.generate(), 'Pink', 'Floyd', 70)
    } catch (e) {
      exceptionThrown = true
    }
    expect(exceptionThrown).to.be.equal(false)
    expect(Booster.config.entities['Person']).to.deep.equal({
      class: Person,
      authorizeReadEvents: [],
    })
  })
  it('adds the entity class as an entity with the right read events permissions', () => {
    @Entity({
      authorizeReadEvents: 'all',
    })
    class Comment {
      public constructor(readonly id: UUID, readonly content: string) {}
    }

    @Role({
      auth: {},
    })
    class Manager {}

    @Entity({
      authorizeReadEvents: [Manager],
    })
    class User {
      public constructor(readonly id: UUID, readonly content: string) {}
    }

    expect(Booster.config.entities['Comment']).to.deep.equal({
      class: Comment,
      authorizeReadEvents: 'all',
    })
    expect(Booster.config.entities['User']).to.deep.equal({
      class: User,
      authorizeReadEvents: [Manager],
    })
  })
})
