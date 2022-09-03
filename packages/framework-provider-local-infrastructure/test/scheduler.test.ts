import { BoosterConfig } from '@boostercloud/framework-types'
import { describe } from 'mocha'
import { fake, replace } from 'sinon'
import { configureScheduler } from '../src/scheduler'
import { expect } from '../test/expect'
import * as scheduler from 'node-schedule'

describe('scheduler', () => {
  let mockConfig: any

  const userProject = {
    boosterTriggerScheduledCommand: () => {
      console.log('triggering scheduled command...')
    },
  }

  beforeEach(() => {
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
      },
    }

    replace(scheduler, 'scheduleJob', fake())
  })

  describe('configureScheduler', () => {
    it('should schedule all commands with the corresponding crons', async () => {
      configureScheduler(mockConfig as unknown as BoosterConfig, userProject)

      expect(scheduler.scheduleJob).to.have.been.calledWithMatch('cmd1', '5 4 * * *')
      expect(scheduler.scheduleJob).to.have.been.calledWithMatch('cmd2', '* 4 3 * *')
    })
  })
})
