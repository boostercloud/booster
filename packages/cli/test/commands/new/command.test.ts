import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import Command from '../../../src/commands/new/command'
import { templates } from '../../../src/templates'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { IConfig } from '@oclif/config'
import { expect } from '../../expect'

describe('new', (): void => {
  describe('Command', () => {
    const command = 'ExampleCommand'
    const commandsRoot = 'src/commands/'
    const commandPath = `${commandsRoot}example-command.ts`
    const defaultCommandImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'Command',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'Register',
      },
    ]
    const uuidCommandImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'Command',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'Register, UUID',
      },
    ]

    const renderCommand = (imports: any[], name: string, fields: any[]): string => {
      return Mustache.render(templates.command, {
        imports: imports,
        name: name,
        fields: fields,
      })
    }

    beforeEach(() => {
      stub(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis()
      replace(fs, 'outputFile', fake.resolves({}))
      replace(ProjectChecker,'checkCurrentDirBoosterVersion', fake.resolves({}))
    })

    afterEach(() => {
      restore()
    })

    describe('Created correctly', () => {
      it('with no fields', async () => {
        await new Command([command], {} as IConfig).run()
        const renderedCommand = renderCommand(defaultCommandImports, command, [])
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
        expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
      })

      it('creates command with a string field', async () => {
        await new Command([command, '--fields', 'title:string'], {} as IConfig).run()
        const renderedCommand = renderCommand(defaultCommandImports, command, [{ name: 'title', type: 'string' }])
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
        expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
      })

      it('creates command with a number field', async () => {
        await new Command([command, '--fields', 'quantity:number'], {} as IConfig).run()
        const renderedCommand = renderCommand(defaultCommandImports, command, [{ name: 'quantity', type: 'number' }])
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
        expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
      })

      it('creates command with UUID field', async () => {
        await new Command([command, '--fields', 'identifier:UUID'], {} as IConfig).run()
        const renderedCommand = renderCommand(uuidCommandImports, command, [{ name: 'identifier', type: 'UUID' }])
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
        expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
      })

      it('creates command with multiple fields', async () => {
        await new Command(
          [command, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'],
          {} as IConfig
        ).run()
        const fields = [
          { name: 'title', type: 'string' },
          { name: 'quantity', type: 'number' },
          { name: 'identifier', type: 'UUID' },
        ]
        const renderedCommand = renderCommand(uuidCommandImports, command, fields)
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
        expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
      })
    })

    describe('displays an error', () => {
      it('with empty command name', async () => {
        replace(console, 'error', fake.resolves({}))
        await new Command([], {} as IConfig).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(commandsRoot)
        expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
        expect(console.error).to.have.been.calledWithMatch(
          /You haven't provided a command name/
        )
      })

      it('with empty fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Command([command, '--fields'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('--fields expects a value')
        expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.not.been.called
      })

      it('with field with no type', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Command([command, '--fields', 'title'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain(
          'Error parsing field title'
        )
        expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
      })

      it('with no field type after :', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Command([command, '--fields', 'title:'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing field title')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(commandPath)
        expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
      })

      it('with repeated fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Command([command, '--fields', 'title:string', 'title:string', 'quantity:number'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing field title')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(commandPath)
        expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
      })
    })
  })
})
