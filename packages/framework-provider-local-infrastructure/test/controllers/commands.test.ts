/* eslint-disable @typescript-eslint/no-explicit-any */
import { CommandsController } from '../../src/controllers/commands'
import { CommandResult } from '@boostercloud/framework-provider-local'
import * as chai from 'chai'
import * as sinonChai from 'sinon-chai'
import { mockRes, mockReq } from 'sinon-express-mock'
import * as faker from 'faker'
import { stub } from 'sinon'
import { expect } from 'chai'
import { UserApp } from '@boostercloud/framework-types'

chai.use(sinonChai)

describe('the commands controller', () => {
  describe('/', () => {
    it('should return status 200 if the command dispatcher succeeds', async () => {
      const commandResult = faker.random.words()
      const userApp = ({
        boosterCommandDispatcher: stub().resolves({
          status: 'success',
          result: commandResult,
        }),
      } as unknown) as UserApp
      const controller = new CommandsController(userApp)
      const req = mockReq()
      const res = mockRes()
      await controller.handle(req, res, stub())
      expect(res.status).to.be.calledWith(200)
    })

    it('should return the error status if the command dispatcher fails', async () => {
      const title = faker.random.word()
      const reason = faker.random.words()
      const code = faker.random.number()
      const userApp = ({
        boosterCommandDispatcher: stub().resolves({
          status: 'failure',
          title,
          reason,
          code,
        } as CommandResult),
      } as unknown) as UserApp
      const controller = new CommandsController(userApp)
      const req = mockReq()
      const res = mockRes()
      await controller.handle(req, res, stub())
      expect(res.status).to.be.calledWith(code)
      expect(res.json).to.be.calledWith({
        title,
        reason,
      })
    })
  })
})
