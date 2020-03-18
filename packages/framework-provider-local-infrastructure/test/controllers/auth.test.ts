/* eslint-disable @typescript-eslint/no-explicit-any */
import { AuthController } from '../../src/controllers/auth'
import { UserRegistry } from '@boostercloud/framework-provider-local'
import * as chai from 'chai'
import * as sinonChai from 'sinon-chai'
import { mockRes, mockReq } from 'sinon-express-mock'
import * as faker from 'faker'
import { stub } from 'sinon'
import { expect } from 'chai'
import { UserApp } from '@boostercloud/framework-types'

chai.use(sinonChai)

describe('the auth controller', () => {
  const userRegistry = ({
    signIn: stub().resolves('fake-token'),
    signUp: stub().resolvesArg(0),
    signOut: stub().resolves(),
  } as any) as UserRegistry

  const userApp = ({ boosterPreSignUpChecker: stub().resolves() } as unknown) as UserApp
  const controller = new AuthController(3000, userRegistry, userApp)

  describe('/sign-up', () => {
    it('should return status 200 if the request is correct', async () => {
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }
      const req = mockReq({ body: user })
      const res = mockRes()
      await controller.signUp(req, res, stub())
      expect(res.status).to.be.calledWith(200)
    })

    it('should call userRegistry.signUp if the request is correct', async () => {
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          roles: [],
        },
        password: faker.internet.password(),
      }
      const req = mockReq({ body: user })
      const res = mockRes()
      await controller.signUp(req, res, stub())
      expect(userRegistry.signUp).to.be.calledWith(user)
    })

    it('should return a status 400 on malformed requests', async () => {
      const req = mockReq({ body: faker.hacker.phrase() })
      const res = mockRes()
      await controller.signUp(req, res, stub())
      expect(res.status).to.be.calledWith(400)
    })
  })

  describe('/sign-in', () => {
    it('should return status 200 for well formed requests', async () => {
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        password: faker.internet.password(),
      }
      const req = mockReq({ body: user })
      const res = mockRes()
      await controller.signIn(req, res, stub())
      expect(res.status).to.be.calledWith(200)
    })

    it('should call userRegistry.signIn well formed requests', async () => {
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        password: faker.internet.password(),
      }
      const req = mockReq({ body: user })
      const res = mockRes()
      await controller.signIn(req, res, stub())
      expect(userRegistry.signIn).to.be.calledWith(user)
    })

    it('should return status 400 on malformed requests', async () => {
      const req = mockReq({ body: faker.hacker.phrase() })
      const res = mockRes()
      await controller.signIn(req, res, stub())
      expect(res.status).to.be.calledWith(400)
    })
  })

  describe('/sign-out', () => {
    it('should return status 200 on well formed requests', async () => {
      const token = faker.random.uuid()
      const request = {
        body: {
          accessToken: token,
        },
      }
      const req = mockReq(request)
      const res = mockRes()
      await controller.signOut(req, res, stub())
      expect(res.status).to.be.calledWith(200)
    })

    it('should call userRegistry.signOut on well formed requests', async () => {
      const token = faker.random.uuid()
      const request = {
        body: {
          accessToken: token,
        },
      }
      const req = mockReq(request)
      const res = mockRes()
      await controller.signOut(req, res, stub())
      expect(userRegistry.signOut).to.be.calledWith(token)
    })

    it('should return status 400 on malformed requests', async () => {
      const req = mockReq({ body: faker.hacker.phrase() })
      const res = mockRes()
      await controller.signOut(req, res, stub())
      expect(res.status).to.be.calledWith(400)
    })
  })
})
