/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig, ProviderLibrary, UserApp, NotAuthorizedError } from '@boostercloud/framework-types'
import { expect } from 'chai'
import * as faker from 'faker'
import { stub } from 'sinon'
import * as chai from 'chai'
import * as chaiAsPromised from 'chai-as-promised'
import * as sinonChai from 'sinon-chai'
import { UserRegistry } from '../../src/services/user-registry'
import * as sinon from 'sinon'
chai.use(chaiAsPromised)
chai.use(sinonChai)

describe('the user registry', () => {
  beforeEach(() => {
    sinon.restore()
  })
  const provider = {} as ProviderLibrary
  const config = new BoosterConfig()
  config.provider = provider

  describe('the signUp method', () => {
    it('should insert users into the registeredUsers database', async () => {
      const userProject = { boosterPreSignUpChecker: stub() as any } as UserApp
      const userRegistry = new UserRegistry(userProject)
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }

      userRegistry.registeredUsers.find = stub().yields(null, [])
      userRegistry.registeredUsers.insert = stub().yields(null, user)

      await userRegistry.signUp(user)
      return expect(userRegistry.registeredUsers.insert).to.have.been.called
    })

    it('should fail if the database `find` fails', async () => {
      const userProject = { boosterPreSignUpChecker: stub() as any } as UserApp
      const userRegistry = new UserRegistry(userProject)
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }

      const error = new Error(faker.random.words())

      userRegistry.registeredUsers.find = stub().yields(null, [])
      userRegistry.registeredUsers.insert = stub().yields(error, user)

      return expect(userRegistry.signUp(user)).to.have.been.rejectedWith(error)
    })

    it('should fail if the database `insert` fails', async () => {
      const userProject = { boosterPreSignUpChecker: stub() as any } as UserApp
      const userRegistry = new UserRegistry(userProject)
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }

      const error = new Error(faker.random.words())

      userRegistry.registeredUsers.find = stub().yields(error, [])
      userRegistry.registeredUsers.insert = stub().yields(null, user)

      return expect(userRegistry.signUp(user)).to.have.been.rejectedWith(error)
    })
  })

  describe('the signIn method', () => {
    it('should check if the user has been registered', async () => {
      const userRegistry = new UserRegistry({} as UserApp)
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }
      userRegistry.registeredUsers.find = stub().yields(null, [{ ...user, confirmed: true }])
      userRegistry.authenticatedUsers.insert = stub().yields(null, { ...user, confirmed: true })
      await userRegistry.signIn(user)
      return expect(userRegistry.registeredUsers.find).to.have.been.called
    })

    it('should insert users into the authenticated users database', async () => {
      const userRegistry = new UserRegistry({} as UserApp)
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }
      userRegistry.registeredUsers.find = stub().yields(null, [{ ...user, confirmed: true }])
      userRegistry.authenticatedUsers.insert = stub().yields(null, { ...user, confirmed: true })
      await userRegistry.signIn(user)
      return expect(userRegistry.authenticatedUsers.insert).to.have.been.called
    })

    it('should fail for users that are not registered', async () => {
      const userRegistry = new UserRegistry({} as UserApp)
      const user = {
        username: faker.internet.email(),
        password: faker.internet.password(),
        roles: [],
      }
      userRegistry.registeredUsers.find = stub().yields(null, [])
      return expect(userRegistry.signIn(user)).to.be.rejectedWith(NotAuthorizedError)
    })

    it('should fail for users that are not confirmed', async () => {
      const userRegistry = new UserRegistry({} as UserApp)
      const user = {
        username: faker.internet.email(),
        password: faker.internet.password(),
        roles: [],
      }
      userRegistry.registeredUsers.find = stub().yields(null, [{ ...user, confirmed: false }])
      return expect(userRegistry.signIn(user)).to.be.rejectedWith(NotAuthorizedError)
    })

    it('should fail if the database `find` fails', async () => {
      const userRegistry = new UserRegistry({} as UserApp)
      const user = {
        username: faker.internet.email(),
        password: faker.internet.password(),
        roles: [],
      }

      const error = new Error(faker.random.words())
      userRegistry.registeredUsers.find = stub().yields(error, null)
      return expect(userRegistry.signIn(user)).to.be.rejectedWith(error)
    })
  })

  describe('the signOut method', () => {
    it('should sign out users', async () => {
      const userRegistry = new UserRegistry({} as UserApp)
      userRegistry.authenticatedUsers.remove = stub().yields(null, null)
      const mockToken = faker.random.uuid()
      await userRegistry.signOut(mockToken)
      return expect(userRegistry.authenticatedUsers.remove).to.have.been.called
    })

    it('should fail if database `remove` fails', async () => {
      const userRegistry = new UserRegistry({} as UserApp)
      const error = new Error(faker.random.words())
      userRegistry.authenticatedUsers.remove = stub().yields(error, null)
      const mockToken = faker.random.uuid()
      return expect(userRegistry.signOut(mockToken)).to.have.be.rejectedWith(error)
    })
  })
})
