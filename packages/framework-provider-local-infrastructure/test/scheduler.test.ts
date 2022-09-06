import { BoosterConfig } from '@boostercloud/framework-types'
import { describe } from 'mocha'
import { fake, replace, restore } from 'sinon'
import { configureScheduler } from '../src/scheduler'
import { expect } from '../test/expect'
import * as scheduler from 'node-schedule'
import * as fc from 'fast-check'

describe('scheduler', () => {
  let mockConfig: any

  const userProject = {
    boosterTriggerScheduledCommand: () => {},
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
              weekDay: '*'
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
          },
        },
      }

      configureScheduler(mockConfig as unknown as BoosterConfig, userProject)

      expect(scheduler.scheduleJob).to.have.been.calledWithMatch('cmd1', '5 4 * * *')
      expect(scheduler.scheduleJob).to.have.been.calledWithMatch('cmd2', '* 4 3 * *')
      expect(scheduler.scheduleJob).to.have.been.calledWithMatch('cmdWithEmptyScheduledOn', {})

      // expect(scheduler.scheduleJob).to.not.have.been.calledWithMatch('cmdWithEmptyScheduledOn')
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


    it('should accept all kinds of cron expressions', () => {
      const minuteRegex = /(\*|(?:\*|(?:[0-9]|(?:[1-5][0-9])))\/(?:[0-9]|(?:[1-5][0-9]))|(?:[0-9]|(?:[1-5][0-9]))(?:(?:\-[0-9]|\-(?:[1-5][0-9]))?|(?:\,(?:[0-9]|(?:[1-5][0-9])))*))/
      const hourRegex = /(\*|(?:[0-9]|1[0-9]|2[0-3])(?:(?:\-(?:[0-9]|1[0-9]|2[0-3]))?|(?:\,(?:[0-9]|1[0-9]|2[0-3]))*))/
      const dayRegex = /(\*|(?:[1-9]|(?:[12][0-9])|3[01])(?:(?:\-(?:[1-9]|(?:[12][0-9])|3[01]))?|(?:\,(?:[1-9]|(?:[12][0-9])|3[01]))*))/
      const monthRegex = /(\*|(?:[1-9]|1[012]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:(?:\-(?:[1-9]|1[012]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?|(?:\,(?:[1-9]|1[012]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))*))/
      const weekdayRegex = /(\*|(?:[0-6]|SUN|MON|TUE|WED|THU|FRI|SAT)(?:(?:\-(?:[0-6]|SUN|MON|TUE|WED|THU|FRI|SAT))?|(?:\,(?:[0-6]|SUN|MON|TUE|WED|THU|FRI|SAT))*))/

      const scheduledOnArb = fc.record(
        {
          minute: fc.string({maxLength: 9}).filter((randomString) => minuteRegex.test(randomString)),
          hour: fc.string({maxLength: 9}).filter((randomString) => hourRegex.test(randomString)),
          day: fc.string({maxLength: 9}).filter((randomString) => dayRegex.test(randomString)),
          month: fc.string({maxLength: 9}).filter((randomString) => monthRegex.test(randomString)),
          weekDay: fc.string({maxLength: 9}).filter((randomString) => weekdayRegex.test(randomString))
        }
      )

      fc.assert(
        fc.property(
          scheduledOnArb, (scheduledOn) => {
            mockConfig = {
              scheduledCommandHandlers: {
                cmd: {
                  class: fake(),
                  scheduledOn: {
                    minute: scheduledOn.minute,
                    hour: scheduledOn.hour,
                    day: scheduledOn.day,
                    month: scheduledOn.month,
                    weekDay: scheduledOn.weekDay
                  },
                }
              },
            }
            configureScheduler(mockConfig as unknown as BoosterConfig, userProject)
            expect(scheduler.scheduleJob).to.have.been.calledWithMatch(
              'cmd',
              `${scheduledOn.minute} ${scheduledOn.hour} ${scheduledOn.day} ${scheduledOn.month} ${scheduledOn.weekDay}`
            )
          }
        )
      )
    })
  })
})
