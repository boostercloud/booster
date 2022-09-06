import { BoosterConfig } from '@boostercloud/framework-types'
import { expect } from '../expect'
import { fake, SinonStub, stub } from 'sinon'
import { rawScheduledInputToEnvelope } from '../../src/library/scheduled-adapter'
import { describe } from 'mocha'

describe('scheduled-adapter', () => {
  let mockConfig: BoosterConfig
  let loggerDebugStub: SinonStub

  beforeEach(() => {
    mockConfig = new BoosterConfig('test')
    mockConfig.appName = 'nuke-button'

    loggerDebugStub = stub()

    mockConfig.logger = {
      info: fake(),
      warn: fake(),
      error: fake(),
      debug: loggerDebugStub,
    }
  })

  describe('rawScheduledInputToEnvelope', () => {
    it('should throw an error when typeName is empty', async () => {
      const emptyScheduledCommandEnvelope = { typeName: '' }
      await expect(rawScheduledInputToEnvelope(mockConfig, emptyScheduledCommandEnvelope)).to.eventually.be.rejectedWith(
        new Error(
          `typeName is not defined or empty, scheduled command envelope should have the structure {typeName: string }, but you gave ${JSON.stringify(
            emptyScheduledCommandEnvelope
          )}`
        ).message
      )
    })

    it('should throw an error when typeName is undefined', async () => {
      const undefinedScheduledCommandEnvelope = { typeName: undefined }
      await expect(rawScheduledInputToEnvelope(mockConfig, undefinedScheduledCommandEnvelope)).to.eventually.be.rejectedWith(
        new Error(
          `typeName is not defined or empty, scheduled command envelope should have the structure {typeName: string }, but you gave ${JSON.stringify(
            undefinedScheduledCommandEnvelope
          )}`
        ).message
      )
    })

    it('should return a ScheduledCommandEnvelope with the same typeName', async () => {
      const input = { typeName: 'dummy' }
      const results = await rawScheduledInputToEnvelope(mockConfig, input)
      expect(results.typeName).to.deep.equal(input.typeName)
    })
  })
})
