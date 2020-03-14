/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig, ProviderLibrary, UserApp, NotAuthorizedError } from '@boostercloud/framework-types'
import { expect } from 'chai'
import * as faker from 'faker'
import { stub } from 'sinon'
import * as chai from 'chai'
import * as chaiAsPromised from 'chai-as-promised'
import * as sinonChai from 'sinon-chai'
import { UserRegistry } from '../../src/services/user-registry'
chai.use(chaiAsPromised)
chai.use(sinonChai)

describe('the authorization controller', () => {
  const provider = {} as ProviderLibrary
  const userProject = { boosterPreSignUpChecker: stub() as any } as UserApp
  const config = new BoosterConfig()
  config.provider = provider
  const makeRegistry = (): any => {
    const userRegistry = new UserRegistry(userProject) as any
    userRegistry.registeredUsers = {
      insert: stub(),
      remove: stub(),
    }
    userRegistry.authenticatedUsers = {
      insert: stub(),
      remove: stub(),
    }
    // Stubbing `getRegisteredUsersByEmail` instead of `find` because the
    // latter is promisified and doesn't play well with sinon
    userRegistry.getRegisteredUsersByEmail = stub().returns([{ confirmed: true }])
    userRegistry.passwordsMatch = stub().returns(true)
    return userRegistry
  }

  describe('the signUp method', () => {
    it('should insert users into the registeredUsers database', async () => {
      const userRegistry = makeRegistry()
      userRegistry.getRegisteredUsersByEmail = stub().returns([])
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }
      await userRegistry.signUp(user)
      return expect(userRegistry.registeredUsers.insert).to.have.been.calledWith({ ...user, confirmed: false })
    })
  })

  describe('the signIn method', () => {
    it('should check if the user has been registered', async () => {
      const userRegistry = makeRegistry() as any
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }
      userRegistry.getRegisteredUsersByEmail = stub().returns([{ ...user, confirmed: true }])
      await userRegistry.signIn(user)
      return expect(userRegistry.getRegisteredUsersByEmail).to.have.been.calledWith(user.username)
    })

    it('should insert users into the authenticated users database', async () => {
      const userRegistry = makeRegistry()
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }
      userRegistry.getRegisteredUsersByEmail = stub().returns([{ ...user, confirmed: true }])
      const token = await userRegistry.signIn(user)
      return expect(userRegistry.authenticatedUsers.insert).to.have.been.calledWith({ username: user.username, token })
    })

    it('should fail for users that are not registered', async () => {
      const userRegistry = makeRegistry() as any
      userRegistry.getRegisteredUsersByEmail = stub().returns([])
      const userEmail = faker.internet.email()
      const user = {
        email: userEmail,
        roles: [],
      }
      return expect(userRegistry.signIn(user)).to.be.rejectedWith(NotAuthorizedError)
    })

    it('should fail for users that are not confirmed', async () => {
      const userRegistry = makeRegistry() as any
      const userEmail = faker.internet.email()
      const user = {
        email: userEmail,
        roles: [],
      }
      userRegistry.getRegisteredUsersByEmail = stub().returns([{ ...user, confirmed: false }])
      return expect(userRegistry.signIn(user)).to.be.rejectedWith(NotAuthorizedError)
    })
  })

  describe('the signOut method', () => {
    it('should sign out users', async () => {
      const userRegistry = makeRegistry()
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }
      userRegistry.getRegisteredUsersByEmail = stub().returns([{ ...user, confirmed: true }])
      const token = await userRegistry.signIn(user)
      await userRegistry.signOut(token)
      return expect(userRegistry.authenticatedUsers.remove).to.have.been.calledWith({ token })
    })
  })
})
