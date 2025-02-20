import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import ScheduledCommand from '../../../src/commands/new/scheduled-command'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { Config } from '@oclif/core'
import { expect } from '../../expect'
import { template } from '../../../src/services/generator'

describe('new', (): void => {
  describe('ScheduledCommand', () => {
    const scheduledCommandName = 'ExampleScheduledCommand'
    const scheduledCommandRoot = 'src/scheduled-commands/'
    const scheduledCommandPath = `${scheduledCommandRoot}example-scheduled-command.ts`
    const defaultScheduledCommandImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'ScheduledCommand',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'Register',
      },
    ]

    beforeEach(() => {
      stub(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis()
      replace(fs, 'outputFile', fake.resolves({}))
      replace(ProjectChecker, 'checkCurrentDirBoosterVersion', fake.resolves({}))
    })

    afterEach(() => {
      restore()
    })

    it('init calls checkCurrentDirBoosterVersion', async () => {
      const config = await Config.load()
      await new ScheduledCommand([], config).init()
      expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
    })

    describe('Created correctly', () => {
      it('with scheduled command name', async () => {
        const config = await Config.load()
        await new ScheduledCommand([scheduledCommandName], config).run()
        const renderedCommand = Mustache.render(template('scheduled-command'), {
          imports: defaultScheduledCommandImports,
          name: scheduledCommandName,
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(scheduledCommandPath, renderedCommand)
      })
    })

    describe('displays an error', () => {
      it('with empty scheduled command name', async () => {
        replace(console, 'error', fake.resolves({}))
        const config = await Config.load()
        await new ScheduledCommand([], config).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(scheduledCommandRoot)
        expect(console.error).to.have.been.calledWithMatch(/You haven't provided a scheduled command name/)
      })

      it('with two scheduled command names', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new ScheduledCommand([scheduledCommandName, 'AnotherName'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Unexpected argument: AnotherName')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(scheduledCommandPath)
      })
    })
  })
})
