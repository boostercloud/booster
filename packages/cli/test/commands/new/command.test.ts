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

    beforeEach(() => {
      stub(ProjectChecker, 'checkItIsABoosterProject').returnsThis()
      replace(fs, 'outputFile', fake.resolves({}))
    })

    afterEach(() => {
      restore()
    })

    describe('Created correctly', () => {
      it('with no fields', async () => {
        await new Command([command], {} as IConfig).run()
        const renderedCommand = Mustache.render(templates.command, {
          imports: defaultCommandImports,
          name: command,
          fields: [],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })

      it('creates command with a string field', async () => {
        await new Command([command, '--fields', 'title:string'], {} as IConfig).run()
        const renderedCommand = Mustache.render(templates.command, {
          imports: defaultCommandImports,
          name: command,
          fields: [{ name: 'title', type: 'string' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })

      it('creates command with a number field', async () => {
        await new Command([command, '--fields', 'quantity:number'], {} as IConfig).run()
        const renderedCommand = Mustache.render(templates.command, {
          imports: defaultCommandImports,
          name: command,
          fields: [{ name: 'quantity', type: 'number' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })

      it('creates command with UUID field', async () => {
        await new Command([command, '--fields', 'identifier:UUID'], {} as IConfig).run()
        const renderedCommand = Mustache.render(templates.command, {
          imports: uuidCommandImports,
          name: command,
          fields: [{ name: 'identifier', type: 'UUID' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })

      it('creates command with multiple fields', async () => {
        await new Command(
          [command, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'],
          {} as IConfig
        ).run()
        const renderedCommand = Mustache.render(templates.command, {
          imports: uuidCommandImports,
          name: command,
          fields: [
            { name: 'title', type: 'string' },
            { name: 'quantity', type: 'number' },
            { name: 'identifier', type: 'UUID' },
          ],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })
    })

    describe('displays an error', () => {
      it('with empty command name', async () => {
        replace(console, 'error', fake.resolves({}))
        await new Command([], {} as IConfig).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(commandsRoot)
        expect(console.error).to.have.been.calledWith(
          "You haven't provided a command name, but it is required, run with --help for usage"
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
        expect(exceptionMessage).to.be.equal('Flag --fields expects a value')
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
          'Error: Error parsing field title. Fields must be in the form of <field name>:<field type>'
        )
      })
    })

    xdescribe('should display an error but is not currently being validated', () => {
      it('with repeated fields', async () => {
        await new Command([command, '--fields', 'title:string', 'title:string', 'quantity:number'], {} as IConfig).run()
        const renderedCommand = Mustache.render(templates.command, {
          imports: defaultCommandImports,
          name: command,
          fields: [
            { name: 'title', type: 'string' },
            { name: 'title', type: 'string' },
            { name: 'quantity', type: 'number' },
          ],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })

      it('with invalid field type', async () => {
        await new Command([command, '--fields', 'title:unimplemented_type'], {} as IConfig).run()
        const renderedCommand = Mustache.render(templates.command, {
          imports: defaultCommandImports,
          name: command,
          fields: [{ name: 'title', type: 'unimplemented_type' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })

      it('with no field type after :', async () => {
        await new Command([command, '--fields', 'title:'], {} as IConfig).run()
        const renderedCommand = Mustache.render(templates.command, {
          imports: defaultCommandImports,
          name: command,
          fields: [{ name: 'title', type: '' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand)
      })
    })
  })
})
