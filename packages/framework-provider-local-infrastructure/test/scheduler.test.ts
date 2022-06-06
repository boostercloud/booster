import { BoosterConfig } from '@boostercloud/framework-types'
import { restore, SinonStub, SinonStubbedInstance, createStubInstance, replace, stub, useFakeTimers } from 'sinon'
import { configureScheduler, createCronExpression, buildScheduledCommandInfo } from '../src/scheduler'
import { expect } from './expect'
import { describe } from 'mocha'
import { random } from 'faker'

declare class UserProject {
  constructor()
  boosterTriggerScheduledCommand: (rawRequest: unknown) => void
}
describe('Local Scheduler', () => {
  let mockUserProjectStub: SinonStubbedInstance<UserProject>
  let mockScheduledCommand: any
  let mockConfig: BoosterConfig
  let mockScheduledCommandName: string
  let queryStub: SinonStub
  let clock: any

  beforeEach(() => {
    class CheckCart {
      public static async handle(): Promise<void> {
        console.log('handle')
      }
    }
    class UserProject {
      boosterTriggerScheduledCommand(rawRequest: unknown): void {
        console.log('rawRequest: ', rawRequest)
      }
    }

    clock = useFakeTimers()

    mockScheduledCommandName = random.word()
    queryStub = stub()

    mockUserProjectStub = createStubInstance(UserProject)
    replace(mockUserProjectStub, 'boosterTriggerScheduledCommand', queryStub as any)

    mockConfig = buildConfig()
    mockConfig.scheduledCommandHandlers[mockScheduledCommandName] = {
      class: CheckCart,
      scheduledOn: {},
    }

    mockScheduledCommand = buildScheduledCommandInfo(mockConfig, mockScheduledCommandName)
  })

  afterEach(() => {
    restore()
  })

  describe('buildScheduledCommandInfo', () => {
    it('should return expected scheduled command info', async () => {
      const result = await buildScheduledCommandInfo(mockConfig, mockScheduledCommandName)
      expect(result).to.be.deep.equal({
        name: mockScheduledCommandName,
        metadata: mockConfig.scheduledCommandHandlers[mockScheduledCommandName],
      })
    })
  })

  describe('createCronExpression', () => {
    it('should return expected cron expression', async () => {
      const result = await createCronExpression(mockScheduledCommand.metadata)
      expect(result).to.be.equal('* * * * * *')
    })
  })

  describe('configureScheduler', () => {
    it('should call scedule job', async () => {
      const results = configureScheduler(mockConfig, mockUserProjectStub)
      clock.tick(3250)
      setTimeout(() => {
        results.forEach((result) => result.cancel())
      }, 3250)
      expect(queryStub).to.have.callCount(3)
    })
  })

  function buildConfig(): BoosterConfig {
    return new BoosterConfig('test')
  }
})
