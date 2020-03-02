/* eslint-disable @typescript-eslint/no-explicit-any */
import { AuthController } from '../../src/controllers/auth'
import { RuntimeStorage } from '../../src/runtime-storage'
import {
  BoosterConfig,
  ProviderLibrary,
  UserApp,
  UserEnvelope,
  NotAuthorizedError,
} from '@boostercloud/framework-types'
import { expect } from 'chai'
import * as faker from 'faker'
import { stub } from 'sinon'
import * as chai from 'chai'
import * as chaiAsPromised from 'chai-as-promised'
chai.use(chaiAsPromised)

describe('the authorization controller', () => {
  let registeredUsers: Record<string, UserEnvelope> = {}
  let authenticatedUsers: Record<string, UserEnvelope> = {}
  const storage = {
    registerUser: (user: UserEnvelope) => {
      registeredUsers[user.email] = user
    },
    authenticateUser: (token: string, user: UserEnvelope) => {
      authenticatedUsers[token] = user
    },
    getRegisteredUsersByEmail: (email: string) => {
      const registered = registeredUsers[email]
      return Promise.resolve(registered ? [registered] : [])
    },
  } as RuntimeStorage
  const provider = {} as ProviderLibrary
  const userProject = { boosterPreSignUpChecker: stub() as any } as UserApp
  const config = new BoosterConfig()
  config.provider = provider

  beforeEach(() => {
    registeredUsers = {}
    authenticatedUsers = {}
  })
  it('should sign up users', async () => {
    const controller = new AuthController(storage, config, userProject)
    const userEmail = faker.internet.email()
    const user = {
      email: userEmail,
      roles: [],
    }
    await controller.signUp(user)
    return expect(registeredUsers[userEmail]).to.eq(user)
  })

  it('should sign in users', async () => {
    const controller = new AuthController(storage, config, userProject)
    const userEmail = faker.internet.email()
    const user = {
      email: userEmail,
      roles: [],
    }
    await storage.registerUser(user)
    await controller.signIn(user)
    return expect(Object.values(authenticatedUsers)).to.contain(user)
  })

  it('should not sign in users that are not registered', async () => {
    const controller = new AuthController(storage, config, userProject)
    const userEmail = faker.internet.email()
    const user = {
      email: userEmail,
      roles: [],
    }
    return expect(controller.signIn(user)).to.be.rejectedWith(NotAuthorizedError)
  })
})
