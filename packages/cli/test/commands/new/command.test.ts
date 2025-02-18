import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import Command from '../../../src/commands/new/command'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { Config } from '@oclif/core'
import { expect } from '../../expect'
import { template } from '../../../src/services/generator'

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
      return Mustache.render(template('command'), {
        imports: imports,
        name: name,
        fields: fields,
      })
    }

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
      await new Command([], config).init()
      expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
    })

    describe('Created correctly', () => {
      it('with no fields', async () => {
        const config = await Config.load()
        await new Command([command], config).run()
        const renderedCommand = renderCommand(defaultCommandImports, command, [])
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })

      it('creates command with a string field', async () => {
        const config = await Config.load()
        await new Command([command, '--fields', 'title:string'], config).run()
        const renderedCommand = renderCommand(defaultCommandImports, command, [{ name: 'title', type: 'string' }])
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })

      it('creates command with a number field', async () => {
        const config = await Config.load()
        await new Command([command, '--fields', 'quantity:number'], config).run()
        const renderedCommand = renderCommand(defaultCommandImports, command, [{ name: 'quantity', type: 'number' }])
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })

      it('creates command with UUID field', async () => {
        const config = await Config.load()
        await new Command([command, '--fields', 'identifier:UUID'], config).run()
        const renderedCommand = renderCommand(uuidCommandImports, command, [{ name: 'identifier', type: 'UUID' }])
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })

      it('creates command with multiple fields', async () => {
        const config = await Config.load()
        await new Command(
          [command, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'],
          config
        ).run()
        const fields = [
          { name: 'title', type: 'string' },
          { name: 'quantity', type: 'number' },
          { name: 'identifier', type: 'UUID' },
        ]
        const renderedCommand = renderCommand(uuidCommandImports, command, fields)
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })
    })

    describe('displays an error', () => {
      it('with empty command name', async () => {
        replace(console, 'error', fake.resolves({}))
        const config = await Config.load()
        await new Command([], config).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(commandsRoot)
        expect(console.error).to.have.been.calledWithMatch(/You haven't provided a command name/)
      })

      it('with empty fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Command([command, '--fields'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('--fields expects a value')
      })

      it('with field with no type', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Command([command, '--fields', 'title'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing field title')
      })

      it('with no field type after :', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Command([command, '--fields', 'title:'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing field title')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(commandPath)
      })

      it('with repeated fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Command(
            [command, '--fields', 'title:string', 'title:string', 'quantity:number'],
            config
          ).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing field title')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(commandPath)
      })
    })
  })
})
