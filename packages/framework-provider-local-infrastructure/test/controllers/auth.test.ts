/* eslint-disable @typescript-eslint/no-explicit-any */
import { AuthController } from '../../src/controllers/auth'
import { UserRegistry } from '@boostercloud/framework-provider-local'
import { mockRes, mockReq } from 'sinon-express-mock'
import * as faker from 'faker'
import { stub, restore } from 'sinon'
import { expect } from '../expect'
import { UserApp } from '@boostercloud/framework-types'

describe('the auth controller', () => {
  afterEach(() => {
    restore()
  })
  const userApp = ({ boosterPreSignUpChecker: stub().resolves() } as unknown) as UserApp

  describe('/sign-up', () => {
    it('should return status 200 if the request is correct', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          role: '',
        },
        password: faker.internet.password(),
      }
      const req = mockReq({ body: user })
      const res = mockRes()
      await controller.signUp(req, res, stub().resolves())
      expect(res.status).to.be.calledOnceWith(200)
    })

    it('should call userRegistry.signUp if the request is correct', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        userAttributes: {
          role: '',
        },
        password: faker.internet.password(),
      }
      const req = mockReq({ body: user })
      const res = mockRes()
      await controller.signUp(req, res, stub().resolves())
      expect(userRegistry.signUp).to.be.calledOnceWith(user)
    })

    it('should return a status 400 on malformed requests', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const req = mockReq({ body: faker.hacker.phrase() })
      const res = mockRes()
      await controller.signUp(req, res, stub().resolves())
      expect(res.status).to.be.calledOnceWith(400)
    })
  })

  describe('/sign-in', () => {
    it('should return status 200 for well formed requests', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        password: faker.internet.password(),
      }
      const req = mockReq({ body: user })
      const res = mockRes()
      await controller.signIn(req, res, stub().resolves())
      expect(res.status).to.be.calledOnceWith(200)
    })

    it('should call userRegistry.signIn well formed requests', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const user = {
        clientId: faker.random.uuid(),
        username: faker.internet.email(),
        password: faker.internet.password(),
      }
      const req = mockReq({ body: user })
      const res = mockRes()
      await controller.signIn(req, res, stub().resolves())
      expect(userRegistry.signIn).to.be.calledOnceWith(user)
    })

    it('should return status 400 on malformed requests', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const req = mockReq({ body: faker.hacker.phrase() })
      const res = mockRes()
      await controller.signIn(req, res, stub().resolves())
      expect(res.status).to.be.calledOnceWith(400)
    })
  })

  describe('/sign-out', () => {
    it('should return status 200 on well formed requests', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const token = faker.random.uuid()
      const request = {
        body: {
          accessToken: token,
        },
      }
      const req = mockReq(request)
      const res = mockRes()
      await controller.signOut(req, res, stub().resolves())
      expect(res.status).to.be.calledOnceWith(200)
    })

    it('should call userRegistry.signOut on well formed requests', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const token = faker.random.uuid()
      const request = {
        body: {
          accessToken: token,
        },
      }
      const req = mockReq(request)
      const res = mockRes()
      await controller.signOut(req, res, stub().resolves())
      expect(userRegistry.signOut).to.be.calledOnceWith(token)
    })

    it('should return status 400 on malformed requests', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const req = mockReq({ body: faker.hacker.phrase() })
      const res = mockRes()
      await controller.signOut(req, res, stub().resolves())
      expect(res.status).to.be.calledOnceWith(400)
    })
  })

  describe('/confirm', () => {
    it('should return status 200 on well formed requests', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const request = {
        params: {
          email: faker.internet.email(),
        },
      }
      const req = mockReq(request)
      const res = mockRes()
      await controller.confirmUser(req, res, stub().resolves())
      expect(res.status).to.be.calledOnceWith(200)
    })

    it('should call userRegistry.confirmUser on well formed requests', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const email = faker.internet.email()
      const request = {
        params: {
          email,
        },
      }
      const req = mockReq(request)
      const res = mockRes()
      await controller.confirmUser(req, res, stub().resolves())
      expect(userRegistry.confirmUser).to.be.calledOnceWith(email)
    })

    it('should return status 400 on malformed requests', async () => {
      const userRegistry = ({
        signIn: stub().resolves('fake-token'),
        signUp: stub().resolvesArg(0),
        signOut: stub().resolves(),
        confirmUser: stub().resolves(),
      } as any) as UserRegistry
      const controller = new AuthController(3000, userRegistry, userApp)
      const req = mockReq({ body: faker.hacker.phrase() })
      const res = mockRes()
      await controller.confirmUser(req, res, stub().resolves())
      expect(res.status).to.be.calledOnceWith(400)
    })
  })
})
