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

  context('when no parameters are provided', () => {
    it('injects the entity metadata and sets up the reducers in the booster config denying event reads', () => {
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
  })

  context("when `authorizeRoleAccess` is set to 'all'", () => {
    it('injects the entity metadata and sets up the reducers in the booster config allowing event reads', () => {
      @Entity({
        authorizeReadEvents: 'all',
      })
      class Comment {
        public constructor(readonly id: UUID, readonly content: string) {}
      }

      expect(Booster.config.entities['Comment']).to.deep.equal({
        class: Comment,
        eventStreamAuthorizer: BoosterAuthorizer.allowAccess,
      })
    })
  })

  context('when `authorizeRoleAccess` is set to an array of roles', () => {
    it('injects the entity metadata and sets up the reducers in the booster config allowing event reads to the specified roles', async () => {
      const fakeAuthorizeRoles = fake()
      replace(BoosterAuthorizer, 'authorizeRoles', fakeAuthorizeRoles)

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

      expect(Booster.config.entities['User'].class).to.be.equal(User)
      const fakeUserEnvelope = {
        username: 'asdf',
      } as UserEnvelope
      await Booster.config.entities['User'].eventStreamAuthorizer(fakeUserEnvelope)
      expect(fakeAuthorizeRoles).to.have.been.calledWithMatch([Manager], fakeUserEnvelope)
    })
  })

  context('when `authorizeRoleAccess` is set to a function', () => {
    it('injects the entity metadata and sets up the reducers in the booster config allowing event reads to tokens that fulfill the authorizer function', async () => {
      @Entity({
        authorizeReadEvents: (currentUser?: UserEnvelope): Promise<void> => {
          if (currentUser?.username !== 'asdf') return Promise.reject('Unauthorized')
          return Promise.resolve()
        },
      })
      class User {
        public constructor(readonly id: UUID, readonly content: string) {}
      }

      expect(Booster.config.entities['User'].class).to.be.equal(User)
      const fakeUserEnvelope = {
        username: 'asdf',
      } as UserEnvelope
      await expect(Booster.config.entities['User'].eventStreamAuthorizer(fakeUserEnvelope)).to.be.fulfilled

      const fakeUserEnvelope2 = {
        username: 'qwer',
      } as UserEnvelope
      await expect(Booster.config.entities['User'].eventStreamAuthorizer(fakeUserEnvelope2)).to.be.rejected
    })
  })
})
