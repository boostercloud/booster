import { AuthController } from '../../src/controllers/auth'
import { RuntimeStorage } from '../../src/runtime-storage'
import { BoosterConfig, ProviderLibrary } from '@boostercloud/framework-types'
import { expect } from 'chai'
import * as faker from 'faker'
import path = require('path')

describe('the authorization controller', () => {
  it('should sign up users', async () => {
    const storage = new RuntimeStorage()
    const provider = {} as ProviderLibrary
    const userProject = require(path.normalize(path.join(process.cwd(), '../framework-example/src/index')))
    const config = new BoosterConfig()
    config.provider = provider
    const controller = new AuthController(storage, config, userProject)
    const userEmail = faker.internet.email()
    const user = {
      email: userEmail,
      roles: [],
    }
    await controller.signUp(user)
    return expect(storage.registeredUsers[userEmail]).to.eq(user)
  })
})
