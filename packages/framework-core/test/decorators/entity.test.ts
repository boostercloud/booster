/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { Event, Entity, Reduces, Role } from '../../src/decorators/'
import { Booster } from '../../src'
import { UserEnvelope, UUID } from '@boostercloud/framework-types'
import { BoosterAuthorizer } from '../../src/booster-authorizer'
import { fake, replace } from 'sinon'

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

    expect(Booster.config.entities['Comment'].class).to.be.equal(Comment)
    expect(Booster.config.entities['Comment'].eventStreamAuthorizer).to.be.equal(BoosterAuthorizer.denyAccess)

    expect(Booster.config.reducers['CommentPosted']).to.deep.include({
      class: Comment,
      methodName: 'react',
    })
  })

  it('adds the entity class as an entity with the right read events permissions', async () => {
    const fakeAuthorizeRoles = fake()
    replace(BoosterAuthorizer, 'authorizeRoles', fakeAuthorizeRoles)

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
      eventStreamAuthorizer: BoosterAuthorizer.allowAccess,
    })
    expect(Booster.config.entities['User'].class).to.be.equal(User)
    const fakeUserEnvelope = {
      username: 'asdf',
    } as UserEnvelope
    await Booster.config.entities['User'].eventStreamAuthorizer(fakeUserEnvelope)
    expect(fakeAuthorizeRoles).to.have.been.calledWithMatch([Manager], fakeUserEnvelope)
  })
})
