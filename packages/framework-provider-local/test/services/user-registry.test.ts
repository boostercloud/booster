/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig, ProviderLibrary, NotAuthorizedError } from '@boostercloud/framework-types'
import { expect } from 'chai'
import * as faker from 'faker'
import { stub, restore } from 'sinon'
import * as chai from 'chai'
import * as chaiAsPromised from 'chai-as-promised'
import * as sinonChai from 'sinon-chai'
import { UserRegistry } from '../../src/services/user-registry'
chai.use(chaiAsPromised)
chai.use(sinonChai)

describe('the user registry', () => {
  beforeEach(() => {
    restore()
  })
  const provider = {} as ProviderLibrary
  const config = new BoosterConfig()
  config.provider = provider

  describe('the signUp method', () => {
    it('should insert users into the registeredUsers database', async () => {
      const userRegistry = new UserRegistry()
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
      const userRegistry = new UserRegistry()
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
      const userRegistry = new UserRegistry()
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
      const userRegistry = new UserRegistry()
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }
      userRegistry.registeredUsers.findOne = stub().yields(null, { ...user, confirmed: true })
      userRegistry.authenticatedUsers.insert = stub().yields(null, { ...user, confirmed: true })
      await userRegistry.signIn(user)
      return expect(userRegistry.registeredUsers.findOne).to.have.been.called
    })

    it('should insert users into the authenticated users database', async () => {
      const userRegistry = new UserRegistry()
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }
      userRegistry.registeredUsers.findOne = stub().yields(null, { ...user, confirmed: true })
      userRegistry.authenticatedUsers.insert = stub().yields(null, { ...user, confirmed: true })
      await userRegistry.signIn(user)
      return expect(userRegistry.authenticatedUsers.insert).to.have.been.called
    })

    it('should fail for users that are not registered', async () => {
      const userRegistry = new UserRegistry()
      const user = {
        username: faker.internet.email(),
        password: faker.internet.password(),
        roles: [],
      }
      userRegistry.registeredUsers.findOne = stub().yields(null, undefined)
      return expect(userRegistry.signIn(user)).to.be.rejectedWith(NotAuthorizedError)
    })

    it('should fail for users that are not confirmed', async () => {
      const userRegistry = new UserRegistry()
      const user = {
        username: faker.internet.email(),
        password: faker.internet.password(),
        roles: [],
      }
      userRegistry.registeredUsers.findOne = stub().yields(null, { ...user, confirmed: false })
      return expect(userRegistry.signIn(user)).to.be.rejectedWith(NotAuthorizedError)
    })

    it('should fail if the database `find` fails', async () => {
      const userRegistry = new UserRegistry()
      const user = {
        username: faker.internet.email(),
        password: faker.internet.password(),
        roles: [],
      }

      const error = new Error(faker.random.words())
      userRegistry.registeredUsers.findOne = stub().yields(error, null)
      return expect(userRegistry.signIn(user)).to.be.rejectedWith(error)
    })
  })

  describe('the signOut method', () => {
    it('should sign out users', async () => {
      const userRegistry = new UserRegistry()
      userRegistry.authenticatedUsers.remove = stub().yields(null, null)
      userRegistry.authenticatedUsers.remove = stub().yields(null, null)
      const mockToken = faker.random.uuid()
      await userRegistry.signOut(mockToken)
      return expect(userRegistry.authenticatedUsers.remove).to.have.been.called
    })

    it('should fail if database `remove` fails', async () => {
      const userRegistry = new UserRegistry()
      const error = new Error(faker.random.words())
      userRegistry.authenticatedUsers.remove = stub().yields(error, null)
      const mockToken = faker.random.uuid()
      return expect(userRegistry.signOut(mockToken)).to.have.be.rejectedWith(error)
    })
  })

  describe('the getAuthenticatedUser method', () => {
    it('should fail if the authenticatedUsers database fails', () => {
      const userRegistry = new UserRegistry()
      const error = new Error(faker.random.words())
      userRegistry.authenticatedUsers.findOne = stub().yields(error, null)
      const mockToken = faker.random.uuid()
      return expect(userRegistry.getAuthenticatedUser(mockToken)).to.have.be.rejectedWith(error)
    })

    it('should fail if the registeredUsers database fails', () => {
      const userRegistry = new UserRegistry()
      const error = new Error(faker.random.words())
      userRegistry.authenticatedUsers.findOne = stub().yields(error, null)
      const mockToken = faker.random.uuid()
      return expect(userRegistry.getAuthenticatedUser(mockToken)).to.have.be.rejectedWith(error)
    })

    it('should retrieve the user data properly', async () => {
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }
      const userRegistry = new UserRegistry()
      userRegistry.authenticatedUsers.findOne = stub().yields(null, user)
      userRegistry.registeredUsers.findOne = stub().yields(null, user)
      const mockToken = faker.random.uuid()
      const retrievedUser = await userRegistry.getAuthenticatedUser(mockToken)
      expect(userRegistry.authenticatedUsers.findOne).to.have.been.called
      expect(userRegistry.registeredUsers.findOne).to.have.been.called
      expect(retrievedUser).to.deep.equal({ email: user.username, roles: user.userAttributes.roles })
    })
  })

  describe('the confirmUser method', () => {
    it('should fail if the database fails', () => {
      const userRegistry = new UserRegistry()
      const error = new Error(faker.random.words())
      userRegistry.registeredUsers.update = stub().yields(error, null)
      const username = faker.random.word()
      return expect(userRegistry.confirmUser(username)).to.have.be.rejectedWith(error)
    })
    it('should call the update method of the database', async () => {
      const userRegistry = new UserRegistry()
      userRegistry.registeredUsers.update = stub().yields(null, [])
      await userRegistry.confirmUser(faker.random.word())
      return expect(userRegistry.registeredUsers.update).to.have.be.called
    })
  })
})
