import * as ProjectChecker from '../../../src/services/project-checker'
import { restore, replace, fake, stub } from 'sinon'
import Entity from '../../../src/commands/new/entity'
import { templates } from '../../../src/templates'
import Mustache = require('mustache')
import * as fs from 'fs-extra'
import { IConfig } from '@oclif/config'
import { expect } from '../../expect'

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

    beforeEach(() => {
      stub(ProjectChecker, 'checkItIsABoosterProject').returnsThis()
      replace(fs, 'outputFile', fake.resolves({}))
    })

    afterEach(() => {
      restore()
    })

    describe('Created correctly', () => {
      it('with no fields and no reduces', async () => {
        await new Entity([entityName], {} as IConfig).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: defaultEntityImports,
          name: entityName,
          fields: [],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with a string field', async () => {
        await new Entity([entityName, '--fields', 'title:string'], {} as IConfig).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: defaultEntityImports,
          name: entityName,
          fields: [{ name: 'title', type: 'string' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with a string field reducing PostCreated', async () => {
        await new Entity([entityName, '--fields', 'title:string', '--reduces', 'PostCreated'], {} as IConfig).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: reducingEntityImports,
          name: entityName,
          fields: [{ name: 'title', type: 'string' }],
          events: [{ eventName: 'PostCreated' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with a number field', async () => {
        await new Entity([entityName, '--fields', 'quantity:number'], {} as IConfig).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: defaultEntityImports,
          name: entityName,
          fields: [{ name: 'quantity', type: 'number' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with a number field reducing PostCreated', async () => {
        await new Entity([entityName, '--fields', 'quantity:number', '--reduces', 'PostCreated'], {} as IConfig).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: reducingEntityImports,
          name: entityName,
          fields: [{ name: 'quantity', type: 'number' }],
          events: [{ eventName: 'PostCreated' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with UUID field', async () => {
        await new Entity([entityName, '--fields', 'identifier:UUID'], {} as IConfig).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: defaultEntityImports,
          name: entityName,
          fields: [{ name: 'identifier', type: 'UUID' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with UUID field reducing PostCreated', async () => {
        await new Entity([entityName, '--fields', 'identifier:UUID', '--reduces', 'PostCreated'], {} as IConfig).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: reducingEntityImports,
          name: entityName,
          fields: [{ name: 'identifier', type: 'UUID' }],
          events: [{ eventName: 'PostCreated' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with multiple fields', async () => {
        await new Entity(
          [entityName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'],
          {} as IConfig
        ).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: defaultEntityImports,
          name: entityName,
          fields: [
            { name: 'title', type: 'string' },
            { name: 'quantity', type: 'number' },
            { name: 'identifier', type: 'UUID' },
          ],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with multiple fields reducing PostCreated', async () => {
        await new Entity(
          [entityName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID', '--reduces', 'PostCreated'],
          {} as IConfig
        ).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: reducingEntityImports,
          name: entityName,
          fields: [
            { name: 'title', type: 'string' },
            { name: 'quantity', type: 'number' },
            { name: 'identifier', type: 'UUID' },
          ],
          events: [{ eventName: 'PostCreated' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('creates Entity with multiple fields reducing PostCreated and CommentCreated', async () => {
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
          {} as IConfig
        ).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: reducingTwoEntityImports,
          name: entityName,
          fields: [
            { name: 'title', type: 'string' },
            { name: 'quantity', type: 'number' },
            { name: 'identifier', type: 'UUID' },
          ],
          events: [{ eventName: 'PostCreated' }, { eventName: 'CommentCreated' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })
    })

    describe('displays an error', () => {
      it('with empty Entity name', async () => {
        replace(console, 'error', fake.resolves({}))
        await new Entity([], {} as IConfig).run()
        expect(fs.outputFile).to.have.not.been.calledWithMatch(entitysRoot)
        expect(console.error).to.have.been.calledWith(
          "You haven't provided an entity name, but it is required, run with --help for usage"
        )
      })

      it('with empty fields', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Entity([entityName, '--fields'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.be.equal('Flag --fields expects a value')
      })

      it('with empty reduces', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Entity([entityName, '--fields', 'title:string', '--reduces'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.be.equal('Flag --reduces expects a value')
      })

      it('with empty fields and reduces', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Entity([entityName, '--fields', '--reduces'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain(
          'Error: Error parsing field --reduces. Fields must be in the form of <field name>:<field type>'
        )
      })

      it('with field with no type', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Entity([entityName, '--fields', 'title'], {} as IConfig).run()
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
        await new Entity(
          [entityName, '--fields', 'title:string', 'title:string', 'quantity:number'],
          {} as IConfig
        ).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: defaultEntityImports,
          name: entityName,
          fields: [
            { name: 'title', type: 'string' },
            { name: 'title', type: 'string' },
            { name: 'quantity', type: 'number' },
          ],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })

      it('with no field type after :', async () => {
        await new Entity([entityName, '--fields', 'title:'], {} as IConfig).run()
        const renderedEntity = Mustache.render(templates.entity, {
          imports: defaultEntityImports,
          name: entityName,
          fields: [{ name: 'title', type: '' }],
        })
        expect(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity)
      })
    })
  })
})
