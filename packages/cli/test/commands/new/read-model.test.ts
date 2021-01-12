import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import ReadModel from '../../../src/commands/new/read-model'
import { templates } from '../../../src/templates'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { IConfig } from '@oclif/config'
import { expect } from '../../expect'

describe('new', (): void => {
  describe('ReadModel', () => {
    const readModelName = 'ExampleReadModel'
    const readModelsRoot = 'src/read-models/'
    const readModelPath = `${readModelsRoot}example-read-model.ts`
    const defaultReadModelImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'ReadModel',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'UUID',
      },
    ]
    const projectingReadModelImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'ReadModel, Projects',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'UUID, ProjectionResult',
      },
      {
        packagePath: '../entities/post',
        commaSeparatedComponents: 'Post',
      },
    ]
    const projectingTwoReadModelImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'ReadModel, Projects',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'UUID, ProjectionResult',
      },
      {
        packagePath: '../entities/post',
        commaSeparatedComponents: 'Post',
      },
      {
        packagePath: '../entities/comment',
        commaSeparatedComponents: 'Comment',
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
      it('with no fields and no projects', async () => {
        await new ReadModel([readModelName], {} as IConfig).run()
        const renderedReadModel = Mustache.render(templates.readModel, {
          imports: defaultReadModelImports,
          name: readModelName,
          fields: [],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with a string field', async () => {
        await new ReadModel([readModelName, '--fields', 'title:string'], {} as IConfig).run()
        const renderedReadModel = Mustache.render(templates.readModel, {
          imports: defaultReadModelImports,
          name: readModelName,
          fields: [{ name: 'title', type: 'string' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with a string field projecting Post:id', async () => {
        await new ReadModel([readModelName, '--fields', 'title:string', '--projects', 'Post:id'], {} as IConfig).run()
        const renderedReadModel = Mustache.render(templates.readModel, {
          imports: projectingReadModelImports,
          name: readModelName,
          fields: [{ name: 'title', type: 'string' }],
          projections: [{ entityName: 'Post', entityId: 'id' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with a number field', async () => {
        await new ReadModel([readModelName, '--fields', 'quantity:number'], {} as IConfig).run()
        const renderedReadModel = Mustache.render(templates.readModel, {
          imports: defaultReadModelImports,
          name: readModelName,
          fields: [{ name: 'quantity', type: 'number' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with a number field projecting Post:id', async () => {
        await new ReadModel(
          [readModelName, '--fields', 'quantity:number', '--projects', 'Post:id'],
          {} as IConfig
        ).run()
        const renderedReadModel = Mustache.render(templates.readModel, {
          imports: projectingReadModelImports,
          name: readModelName,
          fields: [{ name: 'quantity', type: 'number' }],
          projections: [{ entityName: 'Post', entityId: 'id' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with UUID field', async () => {
        await new ReadModel([readModelName, '--fields', 'identifier:UUID'], {} as IConfig).run()
        const renderedReadModel = Mustache.render(templates.readModel, {
          imports: defaultReadModelImports,
          name: readModelName,
          fields: [{ name: 'identifier', type: 'UUID' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with UUID field projecting Post:id', async () => {
        await new ReadModel(
          [readModelName, '--fields', 'identifier:UUID', '--projects', 'Post:id'],
          {} as IConfig
        ).run()
        const renderedReadModel = Mustache.render(templates.readModel, {
          imports: projectingReadModelImports,
          name: readModelName,
          fields: [{ name: 'identifier', type: 'UUID' }],
          projections: [{ entityName: 'Post', entityId: 'id' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with multiple fields', async () => {
        await new ReadModel(
          [readModelName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'],
          {} as IConfig
        ).run()
        const renderedReadModel = Mustache.render(templates.readModel, {
          imports: defaultReadModelImports,
          name: readModelName,
          fields: [
            { name: 'title', type: 'string' },
            { name: 'quantity', type: 'number' },
            { name: 'identifier', type: 'UUID' },
          ],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with multiple fields projecting Post:id', async () => {
        await new ReadModel(
          [readModelName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID', '--projects', 'Post:id'],
          {} as IConfig
        ).run()
        const renderedReadModel = Mustache.render(templates.readModel, {
          imports: projectingReadModelImports,
          name: readModelName,
          fields: [
            { name: 'title', type: 'string' },
            { name: 'quantity', type: 'number' },
            { name: 'identifier', type: 'UUID' },
          ],
          projections: [{ entityName: 'Post', entityId: 'id' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with multiple fields projecting Post:id and Comment:id', async () => {
        await new ReadModel(
          [
            readModelName,
            '--fields',
            'title:string',
            'quantity:number',
            'identifier:UUID',
            '--projects',
            'Post:id',
            'Comment:id',
          ],
          {} as IConfig
        ).run()
        const renderedReadModel = Mustache.render(templates.readModel, {
          imports: projectingTwoReadModelImports,
          name: readModelName,
          fields: [
            { name: 'title', type: 'string' },
            { name: 'quantity', type: 'number' },
            { name: 'identifier', type: 'UUID' },
          ],
          projections: [
            { entityName: 'Post', entityId: 'id' },
            { entityName: 'Comment', entityId: 'id' },
          ],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })
    })

    describe('displays an error', () => {
      it('with empty ReadModel name', async () => {
        replace(console, 'error', fake.resolves({}))
        await new ReadModel([], {} as IConfig).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelsRoot)
        expect(console.error).to.have.been.calledWith(
          "You haven't provided a read model name, but it is required, run with --help for usage"
        )
      })

      it('with empty fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.be.equal('Flag --fields expects a value')
      })

      it('with empty projection', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields', 'title:string', '--projects'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.be.equal('Flag --projects expects a value')
      })

      it('with empty fields and projection', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields', '--projects'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain(
          'Error: Error parsing field --projects. Fields must be in the form of <field name>:<field type>'
        )
      })

      it('with field with no type', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields', 'title'], {} as IConfig).run()
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
          await new ReadModel([readModelName, '--fields', 'title:'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error: Error parsing field title:. Fields must be in the form of <field name>:<field type>')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath)
      })

      it('with projection with no entity id', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields', 'title:string', '--projects', 'Post'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain(
          'Error parsing projection Post. Projections must be in the form of <entity name>:<entity id>'
        )
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath)
      })

      it('with projection with empty entity id', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields', 'title:string', '--projects', 'Post:'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain(
          'Error parsing projection Post:. Projections must be in the form of <entity name>:<entity id>'
        )
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath)
      })

      it('with projection with empty entity name', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields', 'title:string', '--projects', ':id'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain(
          'Error parsing projection :id. Projections must be in the form of <entity name>:<entity id>'
        )
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath)
      })

      it('with repeated fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel(
            [readModelName, '--fields', 'title:string', 'title:string', 'quantity:number'],
            {} as IConfig
          ).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error: Error parsing field title. Fields cannot be duplicated')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath)
      })
    })
  })
})
