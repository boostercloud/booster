import { fake, replace, restore, spy, stub } from 'sinon'
import { Config } from '@oclif/core'
import { ClassDeclaration, MethodDeclarationStructure, Project, SourceFile } from 'ts-morph'
import * as ProjectChecker from '../../../src/services/project-checker'
import { expect } from '../../expect'
import Projection from '../../../src/commands/add/projection'
import * as MethodGenerator from '../../../src/services/method-generator'
import * as Filenames from '../../../src/common/filenames'
import { parseProjectionField } from '../../../src/services/generator/target'

describe('add', async () => {
  describe('projection', async () => {
    const readModelName = 'PostReadModel'
    const projectionName = 'Post:id'
    const sourceFileText = `
      import { ReadModel } from '@boostercloud/framework-core'
      import { UUID } from '@boostercloud/framework-types'
      
      @ReadModel({
        authorize: 'all',
      })
      export class PostReadModel {
        public constructor(public id: UUID, readonly title: string, readonly author: string) {}
      }
    `

    beforeEach(() => {
      stub(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis()
      replace(ProjectChecker, 'checkCurrentDirBoosterVersion', fake.resolves({}))
      replace(Filenames, 'fileNameWithExtension', fake.returns('post-read-model.ts'))
    })

    afterEach(() => {
      restore()
    })

    it('init calls checkCurrentDirBoosterVersion', async () => {
      await new Projection([], {} as Config).init()
      expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
    })

    it('generates projection correctly', async () => {
      const { projection } = await parseProjectionField(projectionName)

      const project = new Project()
      const fakeSourceFile = project.createSourceFile('post.ts', sourceFileText)

      stub(Project.prototype, 'getSourceFileOrThrow').returns(fakeSourceFile)
      stub(MethodGenerator, 'generateProjection').returns({} as MethodDeclarationStructure)
      stub(SourceFile.prototype, 'getClassOrThrow').returns({
        addMethod: stub(),
      } as unknown as ClassDeclaration)

      stub(SourceFile.prototype, 'fixMissingImports').returnsThis()
      stub(SourceFile.prototype, 'save').resolves()

      await new Projection(['--read-model', readModelName, '--entity', projectionName], {} as Config).run()

      expect(Filenames.fileNameWithExtension).to.have.been.calledWith(readModelName)
      expect(Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post-read-model.ts')
      expect(MethodGenerator.generateProjection).to.have.been.calledOnceWith(readModelName, projection)
      expect(SourceFile.prototype.getClassOrThrow).to.have.been.calledOnceWith(readModelName)
      expect(SourceFile.prototype.fixMissingImports).to.have.been.calledOnce
      expect(SourceFile.prototype.save).to.have.been.calledOnce
    })

    describe('displays an error', () => {
      it('with flags missing', async () => {
        let exceptionThrown = false
        let exceptionMessage = null

        try {
          await new Projection([], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        console.log(exceptionMessage)
        expect(exceptionThrown).to.be.true
        expect(exceptionMessage).to.contain('Missing required flag read-model')
      })

      it('with empty --read-model', async () => {
        let exceptionThrown = false
        let exceptionMessage = null

        try {
          await new Projection(['--entity', projectionName, '--read-model'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }

        expect(exceptionThrown).to.be.true
        expect(exceptionMessage).to.contain('--read-model expects a value')
      })

      it('with empty --entity', async () => {
        let exceptionThrown = false
        let exceptionMessage = null

        try {
          await new Projection(['--read-model', readModelName, '--entity'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }

        expect(exceptionThrown).to.be.true
        expect(exceptionMessage).to.contain('--entity expects a value')
      })

      it("when projection doesn't have entity id", async () => {
        let exceptionThrown = false
        let exceptionMessage = null

        try {
          await new Projection(['--read-model', readModelName, '--entity', 'Post'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }

        expect(exceptionThrown).to.be.true
        expect(exceptionMessage).to.contain(
          'Error parsing projection Post. Projections must be in the form of <entity name>:<entity id>'
        )
      })

      it('when projection has empty entity id', async () => {
        let exceptionThrown = false
        let exceptionMessage = null

        try {
          await new Projection(['--read-model', readModelName, '--entity', 'Post:'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }

        expect(exceptionThrown).to.be.true
        expect(exceptionMessage).to.contain(
          'Error parsing projection Post:. Projections must be in the form of <entity name>:<entity id>'
        )
      })

      it('when projection has empty entity name', async () => {
        let exceptionThrown = false
        let exceptionMessage = null

        try {
          await new Projection(['--read-model', readModelName, '--entity', ':id'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }

        expect(exceptionThrown).to.be.true
        expect(exceptionMessage).to.contain(
          'Error parsing projection :id. Projections must be in the form of <entity name>:<entity id>'
        )
      })

      it("when source file doesn't exist", async () => {
        stub(Project.prototype, 'getSourceFileOrThrow').throws()
        const sourceFileSpy = spy(SourceFile.prototype)
        const methodGeneratorSpy = spy(MethodGenerator.generateProjection)
        const classDeclarationSpy = spy(ClassDeclaration.prototype)

        let exceptionThrown = false

        try {
          await new Projection(['--read-model', readModelName, '--entity', projectionName], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
        }

        expect(exceptionThrown).to.be.true
        expect(Filenames.fileNameWithExtension).to.have.been.calledWith(readModelName)
        expect(Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post-read-model.ts')
        expect(classDeclarationSpy.addMethod).to.have.not.been.calledOnce
        expect(sourceFileSpy.getClassOrThrow).to.have.not.been.called
        expect(sourceFileSpy.fixMissingImports).to.have.not.been.called
        expect(sourceFileSpy.save).to.have.not.been.called
        expect(methodGeneratorSpy).to.have.not.been.called
      })

      it("when class doesn't exist in source file", async () => {
        const project = new Project()
        const fakeSourceFile = project.createSourceFile('post.ts', sourceFileText)

        stub(Project.prototype, 'getSourceFileOrThrow').returns(fakeSourceFile)
        replace(SourceFile.prototype, 'getClassOrThrow', fake.throws(new Error()))
        replace(SourceFile.prototype, 'fixMissingImports', spy())
        replace(SourceFile.prototype, 'save', spy())

        const methodGeneratorSpy = spy(MethodGenerator.generateProjection)
        const classDeclarationSpy = spy(ClassDeclaration.prototype)

        let exceptionThrown = false

        try {
          await new Projection(['--read-model', readModelName, '--entity', projectionName], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
        }

        expect(exceptionThrown).to.be.true
        expect(Filenames.fileNameWithExtension).to.have.been.calledWith(readModelName)
        expect(Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post-read-model.ts')
        expect(SourceFile.prototype.getClassOrThrow).to.throw
        expect(SourceFile.prototype.fixMissingImports).to.have.not.been.called
        expect(SourceFile.prototype.save).to.have.not.been.called
        expect(methodGeneratorSpy).to.have.not.been.called
        expect(methodGeneratorSpy).to.have.not.been.called
        expect(classDeclarationSpy.addMethod).to.have.not.been.calledOnce
      })
    })
  })
})
