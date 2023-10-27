import { fake, replace, restore, spy, stub } from 'sinon'
import { Config } from '@oclif/core'
import { ClassDeclaration, MethodDeclarationStructure, Project, SourceFile } from 'ts-morph'
import * as ProjectChecker from '../../../src/services/project-checker'
import { expect } from '../../expect'
import Reducer from '../../../src/commands/add/reducer'
import * as MethodGenerator from '../../../src/services/method-generator'
import * as Filenames from '../../../src/common/filenames'
import { oraLogger } from '../../../src/services/logger'

describe('add', async () => {
  describe('reducer', async () => {
    const entityName = 'Post'
    const sourceFileText = `
    import { Entity } from '@boostercloud/framework-core'
    import { UUID } from '@boostercloud/framework-types'
    
    @Entity
    export class Post {
      public constructor(public id: UUID, readonly title: string, readonly content: string, readonly author: string) {}
    }
    `

    beforeEach(() => {
      stub(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis()
      replace(ProjectChecker, 'checkCurrentDirBoosterVersion', fake.resolves({}))
      replace(Filenames, 'fileNameWithExtension', fake.returns('post.ts'))
    })

    afterEach(() => {
      restore()
    })

    it('init calls checkCurrentDirBoosterVersion', async () => {
      await new Reducer([], {} as Config).init()
      expect(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called
    })

    describe('Created correctly', () => {
      const project = new Project()
      const fakeSourceFile = project.createSourceFile('post.ts', sourceFileText)

      beforeEach(() => {
        stub(Project.prototype, 'getSourceFileOrThrow').returns(fakeSourceFile)
        stub(MethodGenerator, 'generateReducers').returns([{}] as MethodDeclarationStructure[])
        stub(SourceFile.prototype, 'getClassOrThrow').returns({
          addMethods: stub(),
        } as unknown as ClassDeclaration)

        stub(SourceFile.prototype, 'fixMissingImports').returnsThis()
        stub(SourceFile.prototype, 'save').resolves()

        replace(oraLogger, 'info', fake.resolves({}))
      })

      afterEach(() => {
        restore()
      })

      it('generates reducer correctly', async () => {
        await new Reducer(['--entity', entityName, '--event', 'PostCreated'], {} as Config).run()

        expect(Filenames.fileNameWithExtension).to.have.been.calledWith(entityName)
        expect(Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post.ts')
        expect(MethodGenerator.generateReducers).to.have.been.calledOnceWith(entityName, [{ eventName: 'PostCreated' }])
        expect(SourceFile.prototype.getClassOrThrow).to.have.been.calledOnceWith(entityName)
        expect(SourceFile.prototype.fixMissingImports).to.have.been.calledOnce
        expect(SourceFile.prototype.save).to.have.been.calledOnce
        expect(oraLogger.info).to.have.been.calledWithMatch(/Reducer generated!/)
      })

      it('generates multiple reducers correctly', async () => {
        await new Reducer(['--entity', entityName, '--event', 'PostCreated', 'PostUpdated'], {} as Config).run()

        expect(Filenames.fileNameWithExtension).to.have.been.calledWith(entityName)
        expect(Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post.ts')
        expect(MethodGenerator.generateReducers).to.have.been.calledOnceWith(entityName, [
          { eventName: 'PostCreated' },
          { eventName: 'PostUpdated' },
        ])
        expect(SourceFile.prototype.getClassOrThrow).to.have.been.calledOnceWith(entityName)
        expect(SourceFile.prototype.fixMissingImports).to.have.been.calledOnce
        expect(SourceFile.prototype.save).to.have.been.calledOnce
        expect(oraLogger.info).to.have.been.calledWithMatch(/Reducers generated!/)
      })
    })

    describe('displays an error', () => {
      it('with flags missing', async () => {
        let exceptionThrown = false
        let exceptionMessage = null

        try {
          await new Reducer([], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }

        expect(exceptionThrown).to.be.true
        expect(exceptionMessage).to.contain('Missing required flag entity')
      })

      it('with empty --entity flag', async () => {
        let exceptionThrown = false
        let exceptionMessage = null

        try {
          await new Reducer(['--event', 'PostCreated', '--entity'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }

        expect(exceptionThrown).to.be.true
        expect(exceptionMessage).to.contain('--entity expects a value')
      })

      it('with empty --event flag', async () => {
        let exceptionThrown = false
        let exceptionMessage = null

        try {
          await new Reducer(['--entity', entityName, '--event'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }

        expect(exceptionThrown).to.be.true
        expect(exceptionMessage).to.contain('--event expects a value')
      })

      it("when source file doesn't exist", async () => {
        stub(Project.prototype, 'getSourceFileOrThrow').throws()
        const sourceFileSpy = spy(SourceFile.prototype)
        const methodGeneratorSpy = spy(MethodGenerator.generateProjection)
        const classDeclarationSpy = spy(ClassDeclaration.prototype)

        let exceptionThrown = false

        try {
          await new Reducer(['--entity', entityName, '--event', 'PostCreated'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
        }

        expect(exceptionThrown).to.be.true
        expect(Filenames.fileNameWithExtension).to.have.been.calledWith(entityName)
        expect(Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post.ts')
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
          await new Reducer(['--entity', entityName, '--event', 'PostUpdated'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
        }

        expect(exceptionThrown).to.be.true
        expect(Filenames.fileNameWithExtension).to.have.been.calledWith(entityName)
        expect(Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post.ts')
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
