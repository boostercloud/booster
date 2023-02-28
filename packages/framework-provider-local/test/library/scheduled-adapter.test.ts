/* eslint-disable @typescript-eslint/no-explicit-any */
import { SinonStub, stub, replace, restore, fake } from 'sinon'
import { rawScheduledInputToEnvelope, LocalScheduleCommandEnvelope } from '../../src/library/scheduled-adapter'
import { createMockLocalScheduleCommandEnvelope } from '../helpers/scheduled-helper'
import { BoosterConfig, UUID } from '@boostercloud/framework-types'
import { expect } from '../expect'
import { random } from 'faker'

describe('Local provider scheduled-adapter', () => {
  describe('rawScheduledInputToEnvelope', () => {
    let config: BoosterConfig

    let mockScheduledEnvelope: Partial<LocalScheduleCommandEnvelope>
    let mockEmptyScheduledEnvelop: Partial<unknown>
    let mockUuid: string

    let generateStub: SinonStub

    beforeEach(() => {
      config = new BoosterConfig('test')
      config.logger = {
        debug: fake(),
      } as any

      mockUuid = random.uuid()

      mockScheduledEnvelope = createMockLocalScheduleCommandEnvelope()
      mockEmptyScheduledEnvelop = {}

      generateStub = stub().returns(mockUuid)

      replace(UUID, 'generate', generateStub)
    })

    afterEach(() => {
      restore()
    })

    it('should call logger.debug', async () => {
      await rawScheduledInputToEnvelope(config, mockScheduledEnvelope)

      expect(config.logger?.debug).to.have.been.calledOnceWith(
        '[Booster]|rawScheduledInputToEnvelope: ',
        'Received LocalScheduleCommand request: ',
        mockScheduledEnvelope
      )
    })

    it('should thrown an exception for empty typeName', async () => {
      await expect(rawScheduledInputToEnvelope(config, mockEmptyScheduledEnvelop)).to.be.rejectedWith(
        'typeName is not defined or empty, scheduled command envelope should have the structure {typeName: string }, but you gave {}'
      )
    })

    it('should generate expected envelop', async () => {
      const result = await rawScheduledInputToEnvelope(config, mockScheduledEnvelope)
      expect(result).to.be.deep.equal({
        requestID: mockUuid,
        typeName: mockScheduledEnvelope.typeName,
      })
    })
  })
})
