import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import Entity from '../../../src/commands/new/entity'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { Config } from '@oclif/core'
import { expect } from '../../expect'
import { template } from '../../../src/services/generator'

describe('new', (): void => {
  describe('Entity', () => {
    const entityName = 'ExampleEntity'
    const entitysRoot = 'src/entities/'
    const entityPath = `${entitysRoot}example-entity.ts`
    const defaultEntityImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'Entity',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'UUID',
      },
    ]
    const reducingEntityImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'Entity, Reduces',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'UUID',
      },
      {
        packagePath: '../events/post-created',
        commaSeparatedComponents: 'PostCreated',
      },
    ]
    const reducingTwoEntityImports = [
      {
        packagePath: '@boostercloud/framework-core',
        commaSeparatedComponents: 'Entity, Reduces',
      },
      {
        packagePath: '@boostercloud/framework-types',
        commaSeparatedComponents: 'UUID',
      },
      {
        packagePath: '../events/post-created',
        commaSeparatedComponents: 'PostCreated',
      },
      {
        packagePath: '../events/comment-created',
        commaSeparatedComponents: 'CommentCreated',
      },
    ]

    const renderEntity = (imports: any[], name: string, fields: any[], events: any[]): string => {
      return Mustache.render(template('entity'), {
        imports: imports,
        name: name,
        fields: fields,
        events: events,
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
      await new Entity([], config).init()
      expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
    })

    describe('Created correctly', () => {
      it('with no fields and no reduces', async () => {
        const config = await Config.load()
        await new Entity([entityName], config).run()
        const renderedEntity = renderEntity(defaultEntityImports, entityName, [], [])
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with a string field', async () => {
        const config = await Config.load()
        await new Entity([entityName, '--fields', 'title:string'], config).run()
        const renderedEntity = renderEntity(defaultEntityImports, entityName, [{ name: 'title', type: 'string' }], [])
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with a string field reducing PostCreated', async () => {
        const config = await Config.load()
        await new Entity([entityName, '--fields', 'title:string', '--reduces', 'PostCreated'], config).run()
        const renderedEntity = renderEntity(
          reducingEntityImports,
          entityName,
          [{ name: 'title', type: 'string' }],
          [{ eventName: 'PostCreated' }]
        )
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with a number field', async () => {
        const config = await Config.load()
        await new Entity([entityName, '--fields', 'quantity:number'], config).run()
        const renderedEntity = renderEntity(
          defaultEntityImports,
          entityName,
          [{ name: 'quantity', type: 'number' }],
          []
        )
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with a number field reducing PostCreated', async () => {
        const config = await Config.load()
        await new Entity([entityName, '--fields', 'quantity:number', '--reduces', 'PostCreated'], config).run()
        const renderedEntity = renderEntity(
          reducingEntityImports,
          entityName,
          [{ name: 'quantity', type: 'number' }],
          [{ eventName: 'PostCreated' }]
        )
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with UUID field', async () => {
        const config = await Config.load()
        await new Entity([entityName, '--fields', 'identifier:UUID'], config).run()
        const renderedEntity = renderEntity(
          defaultEntityImports,
          entityName,
          [{ name: 'identifier', type: 'UUID' }],
          []
        )
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with UUID field reducing PostCreated', async () => {
        const config = await Config.load()
        await new Entity([entityName, '--fields', 'identifier:UUID', '--reduces', 'PostCreated'], config).run()
        const renderedEntity = renderEntity(
          reducingEntityImports,
          entityName,
          [{ name: 'identifier', type: 'UUID' }],
          [{ eventName: 'PostCreated' }]
        )
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with multiple fields', async () => {
        const config = await Config.load()
        await new Entity(
          [entityName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'],
          config
        ).run()
        const fields = [
          { name: 'title', type: 'string' },
          { name: 'quantity', type: 'number' },
          { name: 'identifier', type: 'UUID' },
        ]
        const renderedEntity = renderEntity(defaultEntityImports, entityName, fields, [])
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with multiple fields reducing PostCreated', async () => {
        const config = await Config.load()
        await new Entity(
          [entityName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID', '--reduces', 'PostCreated'],
          config
        ).run()
        const fields = [
          { name: 'title', type: 'string' },
          { name: 'quantity', type: 'number' },
          { name: 'identifier', type: 'UUID' },
        ]
        const renderedEntity = renderEntity(reducingEntityImports, entityName, fields, [{ eventName: 'PostCreated' }])
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with multiple fields reducing PostCreated and CommentCreated', async () => {
        const config = await Config.load()
        await new Entity(
          [
            entityName,
            '--fields',
            'title:string',
            'quantity:number',
            'identifier:UUID',
            '--reduces',
            'PostCreated',
            'CommentCreated',
          ],
          config
        ).run()
        const fields = [
          { name: 'title', type: 'string' },
          { name: 'quantity', type: 'number' },
          { name: 'identifier', type: 'UUID' },
        ]
        const renderedEntity = renderEntity(reducingTwoEntityImports, entityName, fields, [
          { eventName: 'PostCreated' },
          { eventName: 'CommentCreated' },
        ])
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })
    })

    describe('displays an error', () => {
      it('with empty Entity name', async () => {
        replace(console, 'error', fake.resolves({}))
        const config = await Config.load()
        await new Entity([], config).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(entitysRoot)
        expect(console.error).to.have.been.calledWithMatch(/You haven't provided an entity name/)
      })

      it('with empty fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Entity([entityName, '--fields'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('--fields expects a value')
      })

      it('with empty reduces', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Entity([entityName, '--fields', 'title:string', '--reduces'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('--reduces expects a value')
      })

      it('with empty fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Entity([entityName, '--fields', '--reduces'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Flag --fields expects a value')
      })

      it('with empty reduces', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Entity([entityName, '--fields', 'title', '--reduces'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Flag --reduces expects a value')
      })

      it('with field with no type', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Entity([entityName, '--fields', 'title'], config).run()
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
          await new Entity([entityName, '--fields', 'title:'], config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing field title')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(entityPath)
      })

      it('with repeated fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          const config = await Config.load()
          await new Entity(
            [entityName, '--fields', 'title:string', 'title:string', 'quantity:number'],
            config
          ).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Error parsing field title')
        expect(fs.outputFile).to.have.not.been.calledWithMatch(entityPath)
      })
    })
  })
})
