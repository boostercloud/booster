import { BoosterConfig } from '@boostercloud/framework-types'
import { restore, SinonStubbedInstance, createStubInstance, replace, fake } from 'sinon'
import { configureScheduler } from '../src/scheduler'
import { expect } from './expect'
import { describe } from 'mocha'
import { random } from 'faker'
import * as scheduler from 'node-schedule'

const rewire = require('rewire')
const schedule = rewire('../src/scheduler')
const createCronExpression = schedule.__get__('createCronExpression')
const buildScheduledCommandInfo = schedule.__get__('buildScheduledCommandInfo')
interface ScheduledCommandInfo {
  typeName: string
}
declare class UserProject {
  constructor()
  boosterTriggerScheduledCommand: (rawRequest: unknown) => void
}

describe('Local Scheduler', () => {
  let mockUserProjectStub: SinonStubbedInstance<UserProject>
  let mockConfig: BoosterConfig
  let mockScheduledCommandName: string

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

    mockScheduledCommandName = random.word()

    mockUserProjectStub = createStubInstance(UserProject)

    mockConfig = buildConfig()
    mockConfig.scheduledCommandHandlers[mockScheduledCommandName] = {
      class: CheckCart,
      scheduledOn: {},
    }
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
    it('should return default cron expression', async () => {
      const result = await createCronExpression({ scheduledOn: {} })
      expect(result).to.be.equal('* * * * * *')
    })

    it('should return expected cron expression', async () => {
      const result = await createCronExpression({
        scheduledOn: {
          minute: '30',
          hour: '14',
          weekDay: '0',
        },
      })
      expect(result).to.be.equal('* 30 14 * * 0')
    })
  })

  describe('configureScheduler', () => {
    it('should call scedule job', async () => {
      const fakeScheduleJob = fake((name: string, cronExpression: string, scheduledFunction: () => void) => {
        scheduledFunction()
      })
      const fakeTriggerScheduleCommand = fake((command: ScheduledCommandInfo) => {})
      replace(scheduler, 'scheduleJob', fakeScheduleJob)
      replace(mockUserProjectStub, 'boosterTriggerScheduledCommand', fakeTriggerScheduleCommand as any)

      configureScheduler(mockConfig, mockUserProjectStub)

      expect(scheduler.scheduleJob).to.have.been.calledWith(mockScheduledCommandName, '* * * * * *')
      expect(mockUserProjectStub.boosterTriggerScheduledCommand).to.have.been.calledWith({
        typeName: mockScheduledCommandName,
      })
    })
  })

  function buildConfig(): BoosterConfig {
    return new BoosterConfig('test')
  }
})
