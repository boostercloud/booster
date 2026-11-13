import { BoosterConfig, ScheduledCommandMetadata } from '@boostercloud/framework-types'
import * as scheduler from 'node-schedule'
import { restore, stub, fake, SinonStub } from 'sinon'
import { prepareScheduler } from '../src/scheduler'
import { expect } from './expect'

describe('prepareScheduler', () => {
  let scheduleJobStub: SinonStub
  let config: BoosterConfig

  beforeEach(() => {
    scheduleJobStub = stub(scheduler, 'scheduleJob')
    config = new BoosterConfig('test')
    config.scheduledCommandHandlers['CheckCart'] = {
      class: class CheckCart {},
      scheduledOn: { minute: '0', hour: '*/2' },
    } as unknown as ScheduledCommandMetadata
  })

  afterEach(() => {
    restore()
  })

  function triggerScheduledJob(): void {
    const jobCallback = scheduleJobStub.firstCall.args[2] as () => void
    jobCallback()
  }

  it('schedules each scheduled command with its cron expression', () => {
    prepareScheduler(config, { boosterTriggerScheduledCommands: fake.resolves(undefined) })()

    expect(scheduleJobStub).to.have.been.calledOnceWith('CheckCart', '0 */2 * * *')
  })

  it('triggers the command using `boosterTriggerScheduledCommands`', () => {
    const boosterTriggerScheduledCommands = fake.resolves(undefined)
    prepareScheduler(config, { boosterTriggerScheduledCommands })()

    triggerScheduledJob()

    expect(boosterTriggerScheduledCommands).to.have.been.calledOnceWith({ typeName: 'CheckCart' })
  })

  it('falls back to the deprecated `boosterTriggerScheduledCommand` export', () => {
    const boosterTriggerScheduledCommand = fake.resolves(undefined)
    prepareScheduler(config, { boosterTriggerScheduledCommand })()

    triggerScheduledJob()

    expect(boosterTriggerScheduledCommand).to.have.been.calledOnceWith({ typeName: 'CheckCart' })
  })

  it('prefers `boosterTriggerScheduledCommands` when both exports are present', () => {
    const boosterTriggerScheduledCommands = fake.resolves(undefined)
    const boosterTriggerScheduledCommand = fake.resolves(undefined)
    prepareScheduler(config, { boosterTriggerScheduledCommands, boosterTriggerScheduledCommand })()

    triggerScheduledJob()

    expect(boosterTriggerScheduledCommands).to.have.been.calledOnce
    expect(boosterTriggerScheduledCommand).not.to.have.been.called
  })

  it('does not schedule anything until the returned function is called', () => {
    prepareScheduler(config, { boosterTriggerScheduledCommands: fake.resolves(undefined) })

    expect(scheduleJobStub).not.to.have.been.called
  })

  it('logs the error when the scheduled command fails', async () => {
    const error = new Error('boom')
    const consoleErrorStub = stub(console, 'error')
    prepareScheduler(config, { boosterTriggerScheduledCommands: fake.rejects(error) })()

    triggerScheduledJob()
    await new Promise((resolve) => setImmediate(resolve))

    expect(consoleErrorStub).to.have.been.calledOnceWith('Scheduled command CheckCart failed:', error)
  })

  it('throws a descriptive error when the app exports no trigger', () => {
    expect(() => prepareScheduler(config, {})).to.throw(/boosterTriggerScheduledCommands/)
    expect(scheduleJobStub).not.to.have.been.called
  })

  it('does not require the trigger when there are no scheduled commands', () => {
    delete config.scheduledCommandHandlers['CheckCart']

    const startScheduler = prepareScheduler(config, {})
    startScheduler()

    expect(scheduleJobStub).not.to.have.been.called
  })
})
