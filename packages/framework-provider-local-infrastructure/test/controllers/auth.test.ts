/* eslint-disable @typescript-eslint/no-explicit-any */
import { AuthController } from '../../src/controllers/auth'
import { RuntimeStorage } from '../../src/runtime-storage'
import { BoosterConfig, ProviderLibrary, UserApp, UserEnvelope } from '@boostercloud/framework-types'
import { expect } from 'chai'
import * as faker from 'faker'
import { stub } from 'sinon'

describe('the authorization controller', () => {
  it('should sign up users', async () => {
    const registeredUsers: Record<string, UserEnvelope> = {}
    const storage = {
      registerUser: (user: UserEnvelope) => {
        registeredUsers[user.email] = user
      },
    } as RuntimeStorage
    const provider = {} as ProviderLibrary
    const userProject = { boosterPreSignUpChecker: stub() as any } as UserApp
    const config = new BoosterConfig()
    config.provider = provider
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
    const registeredUsers: Record<string, UserEnvelope> = {}
    const authenticatedUsers: Record<string, UserEnvelope> = {}
    const storage = {
      registerUser: (user: UserEnvelope) => {
        registeredUsers[user.email] = user
      },
      authenticateUser: (token: string, user: UserEnvelope) => {
        authenticatedUsers[token] = user
      },
    } as RuntimeStorage
    const provider = {} as ProviderLibrary
    const userProject = { boosterPreSignUpChecker: stub() as any } as UserApp
    const config = new BoosterConfig()
    config.provider = provider
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
})
