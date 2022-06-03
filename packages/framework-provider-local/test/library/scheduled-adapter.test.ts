/* eslint-disable @typescript-eslint/no-explicit-any */
import { SinonStub, stub, replace, restore } from 'sinon'
import { rawScheduledInputToEnvelope, LocalScheduleCommandEnvelope } from '../../src/library/scheduled-adapter'
import { createMockLocalScheduleCommandEnvelope } from '../helpers/scheduled-helper'
import { UUID } from '@boostercloud/framework-types'
import { expect } from '../expect'
import { random } from 'faker'

describe('Local provider scheduled-adapter', () => {
  describe('rawScheduledInputToEnvelope', () => {
    let mockScheduledEnvelop: Partial<LocalScheduleCommandEnvelope>
    let mockEmptyScheduledEnvelop: Partial<unknown>
    let mockUuid: string

    let debugStub: SinonStub
    let generateStub: SinonStub

    let logger: any

    beforeEach(() => {
      mockUuid = random.uuid()

      mockScheduledEnvelop = createMockLocalScheduleCommandEnvelope()
      mockEmptyScheduledEnvelop = {}

      debugStub = stub()
      generateStub = stub().returns(mockUuid)

      logger = {
        debug: debugStub,
      }
      replace(UUID, 'generate', generateStub)
    })

    afterEach(() => {
      restore()
    })

    it('should call logger.debug', async () => {
      await rawScheduledInputToEnvelope(mockScheduledEnvelop, logger)

      expect(debugStub).to.have.been.calledOnceWith(
        'Received LocalScheduleCommand request: ',
        mockScheduledEnvelop
      )
    })

    it('should thrown an exception for empty typeName', async () => {
      const error =  new Error(
        `typeName is not defined or empty, scheduled command envelope should have the structure {typeName: string }, but you gave ${JSON.stringify(
            mockEmptyScheduledEnvelop
        )}`
      )
      expect(rawScheduledInputToEnvelope(mockEmptyScheduledEnvelop, logger)).to.be.rejectedWith(error)
    })

    it('should generate expected envelop', async () => {
      const result = await rawScheduledInputToEnvelope(mockScheduledEnvelop, logger)
      expect(result).to.be.deep.equal({
        requestID: mockUuid,
        typeName: mockScheduledEnvelop.typeName
      })
    })
  })
})
