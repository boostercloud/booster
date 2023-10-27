import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import ReadModel from '../../../src/commands/new/read-model'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { Config } from '@oclif/core'
import { expect } from '../../expect'
import { template } from '../../../src/services/generator'

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

    const renderReadModel = (imports: any[], name: string, fields: any[], projections: any[]): string => {
      return Mustache.render(template('read-model'), {
        imports: imports,
        name: name,
        fields: fields,
        projections: projections,
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
      await new ReadModel([], {} as Config).init()
      expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
    })

    describe('Created correctly', () => {
      it('with no fields and no projects', async () => {
        await new ReadModel([readModelName], {} as Config).run()
        const renderedReadModel = renderReadModel(defaultReadModelImports, readModelName, [], [])
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with a string field', async () => {
        await new ReadModel([readModelName, '--fields', 'title:string'], {} as Config).run()
        const renderedReadModel = renderReadModel(
          defaultReadModelImports,
          readModelName,
          [{ name: 'title', type: 'string' }],
          []
        )
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with a string field projecting Post:id', async () => {
        await new ReadModel([readModelName, '--fields', 'title:string', '--projects', 'Post:id'], {} as Config).run()
        const renderedReadModel = renderReadModel(
          projectingReadModelImports,
          readModelName,
          [{ name: 'title', type: 'string' }],
          [{ entityName: 'Post', entityId: 'id' }]
        )
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with a number field', async () => {
        await new ReadModel([readModelName, '--fields', 'quantity:number'], {} as Config).run()
        const renderedReadModel = renderReadModel(
          defaultReadModelImports,
          readModelName,
          [{ name: 'quantity', type: 'number' }],
          []
        )
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with a number field projecting Post:id', async () => {
        await new ReadModel(
          [readModelName, '--fields', 'quantity:number', '--projects', 'Post:id'],
          {} as Config
        ).run()
        const renderedReadModel = renderReadModel(
          projectingReadModelImports,
          readModelName,
          [{ name: 'quantity', type: 'number' }],
          [{ entityName: 'Post', entityId: 'id' }]
        )
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with UUID field', async () => {
        await new ReadModel([readModelName, '--fields', 'identifier:UUID'], {} as Config).run()
        const renderedReadModel = renderReadModel(
          defaultReadModelImports,
          readModelName,
          [{ name: 'identifier', type: 'UUID' }],
          []
        )
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with UUID field projecting Post:id', async () => {
        await new ReadModel(
          [readModelName, '--fields', 'identifier:UUID', '--projects', 'Post:id'],
          {} as Config
        ).run()
        const renderedReadModel = renderReadModel(
          projectingReadModelImports,
          readModelName,
          [{ name: 'identifier', type: 'UUID' }],
          [{ entityName: 'Post', entityId: 'id' }]
        )
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with multiple fields', async () => {
        await new ReadModel(
          [readModelName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'],
          {} as Config
        ).run()
        const fields = [
          { name: 'title', type: 'string' },
          { name: 'quantity', type: 'number' },
          { name: 'identifier', type: 'UUID' },
        ]
        const renderedReadModel = renderReadModel(defaultReadModelImports, readModelName, fields, [])
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })

      it('creates ReadModel with multiple fields projecting Post:id', async () => {
        await new ReadModel(
          [readModelName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID', '--projects', 'Post:id'],
          {} as Config
        ).run()
        const fields = [
          { name: 'title', type: 'string' },
          { name: 'quantity', type: 'number' },
          { name: 'identifier', type: 'UUID' },
        ]
        const renderedReadModel = renderReadModel(projectingReadModelImports, readModelName, fields, [
          { entityName: 'Post', entityId: 'id' },
        ])
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
          {} as Config
        ).run()
        const fields = [
          { name: 'title', type: 'string' },
          { name: 'quantity', type: 'number' },
          { name: 'identifier', type: 'UUID' },
        ]
        const projections = [
          { entityName: 'Post', entityId: 'id' },
          { entityName: 'Comment', entityId: 'id' },
        ]
        const renderedReadModel = renderReadModel(projectingTwoReadModelImports, readModelName, fields, projections)
        expect(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel)
      })
    })

    describe('displays an error', () => {
      it('with empty ReadModel name', async () => {
        replace(console, 'error', fake.resolves({}))
        await new ReadModel([], {} as Config).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelsRoot)
        expect(console.error).to.have.been.calledWithMatch(/You haven't provided a read model name/)
      })

      it('with empty fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('--fields expects a value')
      })

      it('with empty projection', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields', 'title:string', '--projects'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('--projects expects a value')
      })

      it('with empty fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Flag --fields expects a value')
      })

      it('with empty projection', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--projects'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Flag --projects expects a value')
      })


      it('with field with no type', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields', 'title'], {} as Config).run()
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
          await new ReadModel([readModelName, '--fields', 'title:'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing field title')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath)
      })

      it('with projection with no entity id', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields', 'title:string', '--projects', 'Post'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing projection Post')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath)
      })

      it('with projection with empty entity id', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields', 'title:string', '--projects', 'Post:'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing projection Post')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath)
      })

      it('with projection with empty entity name', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel([readModelName, '--fields', 'title:string', '--projects', ':id'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing projection :id')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath)
      })

      it('with repeated fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new ReadModel(
            [readModelName, '--fields', 'title:string', 'title:string', 'quantity:number'],
            {} as Config
          ).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Fields cannot be duplicated')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath)
      })
    })
  })
})
