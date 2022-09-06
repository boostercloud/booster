import { BoosterConfig } from '@boostercloud/framework-types'
import { describe } from 'mocha'
import { fake, replace, restore } from 'sinon'
import { configureScheduler } from '../src/scheduler'
import { expect } from '../test/expect'
import * as scheduler from 'node-schedule'

describe('scheduler', () => {
  let mockConfig: any

  const userProject = {
    boosterTriggerScheduledCommand: () => {}
  }

  describe('configureScheduler', () => {
    beforeEach(() => {
      replace(scheduler, 'scheduleJob', fake())
    })

    afterEach(() => {
      restore()
    })

    it('should only schedule commands where `scheduledOn` exists and is not undefined or null or empty', () => {
      mockConfig = {
        scheduledCommandHandlers: {
          cmd1: {
            class: fake(),
            scheduledOn: {
              minute: '5',
              hour: '4',
              day: '*',
              month: '*',
              weekDay: '*',
              year: '*',
            },
          },
          cmd2: {
            class: fake(),
            scheduledOn: {
              minute: '*',
              hour: '4',
              day: '3',
              month: '*',
              weekDay: '*',
              year: '*',
            },
          },
          cmdWithoutScheduledOn: {
            class: fake(),
          },
          cmdWithEmptyScheduledOn: {
            class: fake(),
            scheduledOn: {},
          },
          cmdWithUndefinedScheduledOn: {
            class: fake(),
            scheduledOn: undefined,
          },
          cmdWithNullScheduledOn: {
            class: fake(),
            scheduledOn: null,
          }
        },
      }

      configureScheduler(mockConfig as unknown as BoosterConfig, userProject)

      expect(scheduler.scheduleJob).to.have.been.calledWithMatch('cmd1', '5 4 * * *')
      expect(scheduler.scheduleJob).to.have.been.calledWithMatch('cmd2', '* 4 3 * *')
      
      expect(scheduler.scheduleJob).to.not.have.been.calledWithMatch('cmdWithEmptyScheduledOn')
      expect(scheduler.scheduleJob).to.not.have.been.calledWithMatch('cmdWithoutScheduledOn')
      expect(scheduler.scheduleJob).to.not.have.been.calledWithMatch('cmdWithUndefinedScheduledOn')
      expect(scheduler.scheduleJob).to.not.have.been.calledWithMatch('cmdWithNullScheduledOn')
    })

    it('should not schedule anything when there are no commands', () => {
      mockConfig = {
        scheduledCommandHandlers: {},
      }

      configureScheduler(mockConfig as unknown as BoosterConfig, userProject)

      expect(scheduler.scheduleJob).to.not.have.been.called
    })
  })
})
