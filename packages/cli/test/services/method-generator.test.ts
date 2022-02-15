import { HasName, HasProjection, HasReaction } from '../../src/services/generator/target'
import { expect } from '../expect'
import { generateProjection, generateReducers, getResourceSourceFile } from '../../src/services/method-generator'
import { fake, replace, restore, stub } from 'sinon'
import * as Filenames from '../../src/common/filenames'
import { fileNameWithExtension } from '../../src/common/filenames'
import { Project } from 'ts-morph'

describe('method generator', (): void => {
  it('generates projection', async () => {
    type ProjectionInfo = HasName & HasProjection

    const info: ProjectionInfo = {
      name: 'PostReadModel',
      projection: { entityName: 'Post', entityId: 'id' },
    }

    const projectionMethodParams = await generateProjection(info.name, info.projection)

    expect(projectionMethodParams)
      .to.be.an('object')
      .that.has.all.keys(['decorators', 'scope', 'isStatic', 'name', 'parameters', 'returnType', 'statements'])

    expect(projectionMethodParams).to.be.deep.equal({
      decorators: [
        {
          name: 'Projects',
          arguments: [info.projection.entityName, `'${info.projection.entityId}'`],
        },
      ],
      scope: 'public',
      isStatic: true,
      name: `project${info.projection.entityName}`,
      parameters: [
        {
          name: 'entity',
          type: info.projection.entityName,
        },
        {
          name: `current${info.name}`,
          type: info.name,
          hasQuestionToken: true,
        },
      ],
      returnType: `ProjectionResult<${info.name}>`,
      statements: [`return /* NEW ${info.name} HERE */`],
    })
  })

  it('generates reducers', async () => {
    type ReducerInfo = HasName & HasReaction

    it('generates single reducer', async () => {
      const info: ReducerInfo = {
        name: 'Post',
        events: [{ eventName: 'PostCreated' }],
      }

      const reducerMethodParams = await generateReducers(info.name, info.events)

      expect(reducerMethodParams).to.have.lengthOf(1)
      expect(reducerMethodParams[0]).to.deep.include({ name: `reduce${info.events[0].eventName}` })
      expect(reducerMethodParams[0])
        .to.be.an('object')
        .that.has.all.keys(['decorators', 'scope', 'isStatic', 'name', 'parameters', 'returnType', 'statements'])

      expect(reducerMethodParams).to.have.deep.members([
        {
          decorators: [
            {
              name: 'Reduces',
              arguments: [info.events[0].eventName],
            },
          ],
          scope: 'public',
          isStatic: true,
          name: `reduce${info.events[0].eventName}`,
          returnType: info.name,
          parameters: [
            {
              name: 'event',
              type: info.events[0].eventName,
            },
            {
              name: `current${info.name}`,
              type: info.name,
              hasQuestionToken: true,
            },
          ],
          statements: [`return /* NEW ${info.name} HERE */`],
        },
      ])
    })

    it('generates multiple reducers', async () => {
      const info: ReducerInfo = {
        name: 'Post',
        events: [{ eventName: 'PostCreated' }, { eventName: 'PostUpdated' }],
      }

      const reducerMethodParams = await generateReducers(info.name, info.events)

      expect(reducerMethodParams).to.have.lengthOf(2)
      reducerMethodParams.forEach((member, index) => {
        expect(member.name).to.be.equal(`reduce${info.events[index].eventName}`)

        expect(member)
          .to.be.an('object')
          .that.has.all.keys(['decorators', 'scope', 'isStatic', 'name', 'parameters', 'returnType', 'statements'])

        expect(member).to.deep.include({
          scope: 'public',
          isStatic: true,
          returnType: info.name,
          statements: [`return /* NEW ${info.name} HERE */`],
          decorators: [
            {
              name: 'Reduces',
              arguments: [info.events[index].eventName],
            },
          ],
          name: `reduce${info.events[index].eventName}`,
          parameters: [
            {
              name: 'event',
              type: info.events[index].eventName,
            },
            {
              name: `current${info.name}`,
              type: info.name,
              hasQuestionToken: true,
            },
          ],
        })
      })

      expect(reducerMethodParams).to.have.deep.members([
        {
          scope: 'public',
          isStatic: true,
          returnType: info.name,
          statements: [`return /* NEW ${info.name} HERE */`],
          decorators: [
            {
              name: 'Reduces',
              arguments: [info.events[0].eventName],
            },
          ],
          name: `reduce${info.events[0].eventName}`,
          parameters: [
            {
              name: 'event',
              type: info.events[0].eventName,
            },
            {
              name: `current${info.name}`,
              type: info.name,
              hasQuestionToken: true,
            },
          ],
        },
        {
          scope: 'public',
          isStatic: true,
          returnType: info.name,
          statements: [`return /* NEW ${info.name} HERE */`],
          decorators: [
            {
              name: 'Reduces',
              arguments: [info.events[1].eventName],
            },
          ],
          name: `reduce${info.events[1].eventName}`,
          parameters: [
            {
              name: 'event',
              type: info.events[1].eventName,
            },
            {
              name: `current${info.name}`,
              type: info.name,
              hasQuestionToken: true,
            },
          ],
        },
      ])
    })
  })

  describe('getResourceSourceFile', () => {
    afterEach(() => {
      restore()
    })

    it('should return source file', () => {
      const resourceName = 'Post'
      replace(Filenames, 'fileNameWithExtension', fake.returns('post.ts'))

      const project = new Project()
      const fileText = `
      import { Entity } from '@boostercloud/framework-core'
      import { UUID } from '@boostercloud/framework-types'
      
      @Entity
      export class Post {
        public constructor(public id: UUID, readonly title: string, readonly content: string, readonly author: string) {}
      }`

      const fakeSourceFile = project.createSourceFile('src/entities/post.ts', fileText)
      stub(Project.prototype, 'getSourceFileOrThrow').returns(fakeSourceFile)

      const sourceFile = getResourceSourceFile(resourceName)
      const sourceFileClasses = sourceFile.getClasses().map((className) => className.getName())

      expect(Project.prototype.getSourceFileOrThrow).to.have.been.called
      expect(Project.prototype.getSourceFileOrThrow).to.have.been.calledOnce
      expect(Project.prototype.getSourceFileOrThrow).to.not.have.been.calledTwice
      expect(Project.prototype.getSourceFileOrThrow).to.have.been.calledWith(fileNameWithExtension(resourceName))

      expect(sourceFile).to.be.equal(fakeSourceFile)
      expect(fileNameWithExtension).to.have.been.calledWith(resourceName)
      expect(sourceFileClasses).to.contain(resourceName)
      expect(sourceFile.getFilePath()).to.match(/src\/entities\/post.ts/)
    })

    it("should throw error if source file doesn't exist", () => {
      replace(Filenames, 'fileNameWithExtension', fake.returns('fake-post.ts'))
      stub(Project.prototype, 'getSourceFileOrThrow').throws(
        new Error('Could not find source file in project with the provided file name: fake-post.ts')
      )

      const resourceName = 'FakePost'
      let exceptionThrown = false
      let exceptionMessage = ''

      try {
        getResourceSourceFile(resourceName)
      } catch (e) {
        exceptionThrown = true
        exceptionMessage = e.message
      }

      expect(fileNameWithExtension).to.have.been.calledWith(resourceName)
      expect(getResourceSourceFile).to.throw()
      expect(exceptionThrown).to.be.true
      expect(exceptionMessage).to.be.equal(
        'Could not find source file in project with the provided file name: fake-post.ts'
      )
    })
  })
})
