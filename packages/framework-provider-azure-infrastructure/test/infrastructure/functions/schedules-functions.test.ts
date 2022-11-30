import { BoosterConfig, ScheduledCommandInterface, ScheduleInterface } from '@boostercloud/framework-types'
import { expect } from '../../expect'
import { describe } from 'mocha'
import { ScheduledFunctions } from '../../../src/infrastructure/functions/scheduled-functions'
import { ScheduleFunctionDefinition } from '../../../src/infrastructure/types/functionDefinition'
import * as path from 'path'

describe('Creating scheduled-functions', () => {
  describe('without scheduledCommandHandlers', () => {
    const config = buildConfig()

    it('is an undefined object', async () => {
      const definitions = new ScheduledFunctions(config).getFunctionDefinitions()
      expect(definitions).to.be.undefined
    })
  })

  describe('with one scheduledCommandHandlers', () => {
    const config = buildConfig()
    const scheduleCommandInterface = {} as ScheduledCommandInterface
    const scheduleInterface = { day: '1' } as ScheduleInterface

    const scheduleCommandName = 'test'
    config.scheduledCommandHandlers[scheduleCommandName] = {
      class: scheduleCommandInterface,
      scheduledOn: scheduleInterface,
    }

    it('is one definition with the proper fields', () => {
      const definitions = new ScheduledFunctions(config).getFunctionDefinitions() as Array<ScheduleFunctionDefinition>
      expect(definitions).not.to.be.null
      expect(definitions.length).to.be.equal(1)
      expectDefinition(definitions[0], scheduleCommandName, '0 * * 1 * *')
    })
  })

  describe('with two scheduledCommandHandlers', () => {
    const config = buildConfig()
    const scheduleCommandInterface = {} as ScheduledCommandInterface
    const scheduleInterface = { day: '1' } as ScheduleInterface

    const scheduleCommandName1 = 'test-1'
    config.scheduledCommandHandlers[scheduleCommandName1] = {
      class: scheduleCommandInterface,
      scheduledOn: scheduleInterface,
    }

    const scheduleCommandName2 = 'test-2'
    config.scheduledCommandHandlers[scheduleCommandName2] = {
      class: scheduleCommandInterface,
      scheduledOn: scheduleInterface,
    }

    it('is two definitions with the proper fields', () => {
      const definitions = new ScheduledFunctions(config).getFunctionDefinitions() as Array<ScheduleFunctionDefinition>
      expect(definitions).not.to.be.null
      expect(definitions.length).to.be.equal(2)
      expectDefinition(definitions[0], scheduleCommandName1, '0 * * 1 * *')
      expectDefinition(definitions[1], scheduleCommandName2, '0 * * 1 * *')
    })
  })

  describe('with all scheduled fields', () => {
    const config = buildConfig()
    const scheduleCommandInterface = {} as ScheduledCommandInterface
    const scheduleInterface = {
      day: 'day',
      hour: 'hour',
      weekDay: 'weekDay',
      minute: 'minute',
      month: 'month',
      year: 'year',
    } as ScheduleInterface

    const scheduleCommandName = 'test'
    config.scheduledCommandHandlers[scheduleCommandName] = {
      class: scheduleCommandInterface,
      scheduledOn: scheduleInterface,
    }

    it('create the expected nCronTab', () => {
      const definitions = new ScheduledFunctions(config).getFunctionDefinitions() as Array<ScheduleFunctionDefinition>
      expectDefinition(definitions[0], scheduleCommandName, '0 minute hour day month weekDay')
    })
  })

  describe('without scheduled fields', () => {
    const config = buildConfig()
    const scheduleCommandInterface = {} as ScheduledCommandInterface
    const scheduleInterface = {} as ScheduleInterface

    const scheduleCommandName = 'test'
    config.scheduledCommandHandlers[scheduleCommandName] = {
      class: scheduleCommandInterface,
      scheduledOn: scheduleInterface,
    }

    it('skip the function', () => {
      const definitions = new ScheduledFunctions(config).getFunctionDefinitions() as Array<ScheduleFunctionDefinition>
      expect(definitions.length).to.be.equal(0)
    })
  })

  function expectDefinition(
    definition: ScheduleFunctionDefinition,
    scheduleCommandName: string,
    nCronTabExpression = '0 * * * * *'
  ): void {
    expect(definition.name).to.be.equal(`scheduleFunction-${scheduleCommandName}`)
    expect(definition.config.bindings.length).to.be.equal(1)
    expect(definition.config.bindings[0].type).to.be.equal('timerTrigger')
    expect(definition.config.bindings[0].name).to.be.equal(scheduleCommandName)
    expect(definition.config.bindings[0].direction).to.be.equal('in')
    expect(definition.config.bindings[0].schedule).to.be.equal(nCronTabExpression)
    expect(definition.config.scriptFile).to.be.equal(path.join('../dist/index.js'))
    expect(definition.config.entryPoint).to.be.equal('boosterTriggerScheduledCommand')
  }

  function buildConfig(): BoosterConfig {
    return new BoosterConfig('test')
  }
})
