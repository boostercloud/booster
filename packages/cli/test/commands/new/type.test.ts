import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import Type from '../../../src/commands/new/type'
import { templates } from '../../../src/templates'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { IConfig } from '@oclif/config'
import { expect } from '../../expect'

describe('new', (): void => {
  describe('Type', () => {
    const typeName = 'ExampleType'
    const typesRoot = 'src/common/'
    const typePath = `${typesRoot}example-type.ts`
    const defaultTypeImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'Type',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'Register',
      },
    ]
    const uuidTypeImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'Type',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'Register, UUID',
      },
    ]

    beforeEach(() => {
      stub(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis()
      replace(fs, 'outputFile', fake.resolves({}))
    })

    afterEach(() => {
      restore()
    })

    describe('Created correctly', () => {
      it('with no fields', async () => {
        await new Type([typeName], {} as IConfig).run()
        const renderedType = Mustache.render(templates.type, {
          imports: defaultTypeImports,
          name: typeName,
          fields: [],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(typePath, renderedType)
      })

      it('creates Type with a string field', async () => {
        await new Type([typeName, '--fields', 'title:string'], {} as IConfig).run()
        const renderedType = Mustache.render(templates.type, {
          imports: defaultTypeImports,
          name: typeName,
          fields: [{ name: 'title', type: 'string' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(typePath, renderedType)
      })

      it('creates Type with a number field', async () => {
        await new Type([typeName, '--fields', 'quantity:number'], {} as IConfig).run()
        const renderedType = Mustache.render(templates.type, {
          imports: defaultTypeImports,
          name: typeName,
          fields: [{ name: 'quantity', type: 'number' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(typePath, renderedType)
      })

      it('creates Type with UUID field', async () => {
        await new Type([typeName, '--fields', 'identifier:UUID'], {} as IConfig).run()
        const renderedType = Mustache.render(templates.type, {
          imports: uuidTypeImports,
          name: typeName,
          fields: [{ name: 'identifier', type: 'UUID' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(typePath, renderedType)
      })

      it('creates Type with multiple fields', async () => {
        await new Type(
          [typeName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'],
          {} as IConfig
        ).run()
        const renderedType = Mustache.render(templates.type, {
          imports: uuidTypeImports,
          name: typeName,
          fields: [
            { name: 'title', type: 'string' },
            { name: 'quantity', type: 'number' },
            { name: 'identifier', type: 'UUID' },
          ],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(typePath, renderedType)
      })
    })

    describe('displays an error', () => {
      it('with empty Type name', async () => {
        replace(console, 'error', fake.resolves({}))
        await new Type([], {} as IConfig).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(typesRoot)
        expect(console.error).to.have.been.calledWith(
          "You haven't provided a type name, but it is required, run with --help for usage"
        )
      })

      it('with empty fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Type([typeName, '--fields'], {} as IConfig).run()
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
          await new Type([typeName, '--fields', 'title'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain(
          'Error: Error parsing field title. Fields must be in the form of <field name>:<field type>'
        )
      })

      it('with no field type after :', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Type([typeName, '--fields', 'title:'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error: Error parsing field title:. Fields must be in the form of <field name>:<field type>')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(typePath)
      })

      it('with repeated fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Type([typeName, '--fields', 'title:string', 'title:string', 'quantity:number'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error: Error parsing field title. Fields cannot be duplicated')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(typePath)
      })
    })
  })
})
