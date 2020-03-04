/* eslint-disable @typescript-eslint/no-explicit-any */
import { AuthController } from '../../src/controllers/auth'
import { UserRegistry } from '../../src/services/user-registry'
import * as chai from 'chai'
import * as sinonChai from 'sinon-chai'
import { mockRes, mockReq } from 'sinon-express-mock'
import * as faker from 'faker'
import { stub } from 'sinon'
import { expect } from 'chai'

chai.use(sinonChai)

describe('the auth controller', () => {
  const userRegistry = ({
    signIn: stub().resolves('fake-token'),
    signUp: stub().resolves(),
    signOut: stub().resolves(),
  } as any) as UserRegistry

  const controller = new AuthController(userRegistry)

  describe('/sign-up', () => {
    it('should accept well formed requests', async () => {
      const userEmail = faker.internet.email()
      const user = {
        email: userEmail,
        roles: [],
      }
      const req = mockReq(user)
      const res = mockRes()
      await controller.signUp(req, res)
      expect(res.status).to.be.calledWith(200)
    })

    it('should fail on malformed requests', async () => {
      const req = mockReq({ blahblah: faker.hacker.phrase() })
      const res = mockRes()
      await controller.signUp(req, res)
      expect(res.status).to.be.calledWith(500)
    })
  })

  describe('the sign-in endpoint', () => {
    it('should accept well formed requests', async () => {
      const userEmail = faker.internet.email()
      const user = {
        email: userEmail,
        roles: [],
      }
      const req = mockReq(user)
      const res = mockRes()
      await controller.signIn(req, res)
      expect(res.status).to.be.calledWith(200)
    })

    it('should fail on malformed requests', async () => {
      const req = mockReq({ blahblah: faker.hacker.phrase() })
      const res = mockRes()
      await controller.signIn(req, res)
      expect(res.status).to.be.calledWith(500)
    })
  })

  describe('the sign-out endpoint', () => {
    it('should accept well formed requests', async () => {
      const token = faker.random.uuid()
      const request = {
        body: {
          accessToken: token,
        },
      }
      const req = mockReq(request)
      const res = mockRes()
      await controller.signOut(req, res)
      expect(res.status).to.be.calledWith(200)
    })

    it('should fail on malformed requests', async () => {
      const req = mockReq({ blahblah: faker.hacker.phrase() })
      const res = mockRes()
      await controller.signOut(req, res)
      expect(res.status).to.be.calledWith(400)
    })
  })
})
