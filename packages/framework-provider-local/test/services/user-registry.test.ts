/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig, ProviderLibrary, NotAuthorizedError } from '@boostercloud/framework-types'
import { expect } from '../expect'
import * as faker from 'faker'
import { stub, restore } from 'sinon'
import { UserRegistry } from '../../src/services'

describe('the user registry', () => {
  afterEach(() => {
    restore()
  })
  const provider = {} as ProviderLibrary
  const config = new BoosterConfig('test')
  config.provider = provider

  describe('the signUp method', () => {
    it('should insert users into the registeredUsers database', async () => {
      const userRegistry = new UserRegistry()
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          role: '',
        },
        password: faker.internet.password(),
      }

      userRegistry.registeredUsers.find = stub().returns({ exec: stub().yields(null, []) })
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
          role: '',
        },
        password: faker.internet.password(),
      }

      const error = new Error(faker.lorem.words())

      userRegistry.registeredUsers.find = stub().returns({ exec: stub().yields(error, []) })
      userRegistry.registeredUsers.insert = stub().yields(null, user)

      return expect(userRegistry.signUp(user)).to.have.been.rejectedWith(error)
    })

    it('should fail if the database `insert` fails', async () => {
      const userRegistry = new UserRegistry()
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          role: '',
        },
        password: faker.internet.password(),
      }

      const error = new Error(faker.lorem.words())

      userRegistry.registeredUsers.find = stub().returns({ exec: stub().yields(null, []) })
      userRegistry.registeredUsers.insert = stub().yields(error, null)

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
          role: '',
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
          role: '',
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
        role: '',
      }
      userRegistry.registeredUsers.findOne = stub().yields(null, undefined)
      return expect(userRegistry.signIn(user)).to.be.rejectedWith(NotAuthorizedError)
    })

    it('should fail for users that are not confirmed', async () => {
      const userRegistry = new UserRegistry()
      const user = {
        username: faker.internet.email(),
        password: faker.internet.password(),
        role: '',
      }
      userRegistry.registeredUsers.findOne = stub().yields(null, { ...user, confirmed: false })
      return expect(userRegistry.signIn(user)).to.be.rejectedWith(NotAuthorizedError)
    })

    it('should fail if the database `find` fails', async () => {
      const userRegistry = new UserRegistry()
      const user = {
        username: faker.internet.email(),
        password: faker.internet.password(),
        role: '',
      }

      const error = new Error(faker.lorem.words())
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
      const error = new Error(faker.lorem.words())
      userRegistry.authenticatedUsers.remove = stub().yields(error, null)
      const mockToken = faker.random.uuid()
      return expect(userRegistry.signOut(mockToken)).to.have.be.rejectedWith(error)
    })
  })

  describe('the getAuthenticatedUser method', () => {
    it('should fail if the authenticatedUsers database fails', () => {
      const userRegistry = new UserRegistry()
      const error = new Error(faker.lorem.words())
      userRegistry.authenticatedUsers.findOne = stub().yields(error, null)
      const mockToken = faker.random.uuid()
      return expect(userRegistry.getAuthenticatedUser(mockToken)).to.have.be.rejectedWith(error)
    })

    it('should fail if the registeredUsers database fails', () => {
      const userRegistry = new UserRegistry()
      const error = new Error(faker.lorem.words())
      userRegistry.authenticatedUsers.findOne = stub().yields(error, null)
      const mockToken = faker.random.uuid()
      return expect(userRegistry.getAuthenticatedUser(mockToken)).to.have.be.rejectedWith(error)
    })

    it('should retrieve the user data properly', async () => {
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          role: '',
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
      expect(retrievedUser).to.deep.equal({
        username: user.username,
        role: user.userAttributes.role,
      })
    })
  })

  describe('the confirmUser method', () => {
    it('should fail if the database fails', () => {
      const userRegistry = new UserRegistry()
      const username = faker.internet.email()
      const error = new Error(faker.lorem.words())
      userRegistry.registeredUsers.find = stub().returns({ exec: stub().yields(null, [username]) })
      userRegistry.registeredUsers.update = stub().yields(error, null)
      return expect(userRegistry.confirmUser(username)).to.have.be.rejectedWith(error)
    })

    it('should fail if user does not exist', () => {
      const userRegistry = new UserRegistry()
      const username = faker.internet.email()
      userRegistry.registeredUsers.find = stub().returns({ exec: stub().yields(null, []) })
      return expect(userRegistry.confirmUser(username)).to.have.been.rejectedWith(`Incorrect username ${username}`)
    })

    it('should call the update method of the database', async () => {
      const userRegistry = new UserRegistry()
      const username = faker.internet.email()
      userRegistry.registeredUsers.find = stub().returns({ exec: stub().yields(null, [username]) })
      userRegistry.registeredUsers.update = stub().yields(null, [])
      await userRegistry.confirmUser(username)
      return expect(userRegistry.registeredUsers.update).to.have.been.called
    })
  })
})
