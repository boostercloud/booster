"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_1 = require("sinon");
const ts_morph_1 = require("ts-morph");
const ProjectChecker = require("../../../src/services/project-checker");
const expect_1 = require("../../expect");
const projection_1 = require("../../../src/commands/add/projection");
const MethodGenerator = require("../../../src/services/method-generator");
const Filenames = require("../../../src/common/filenames");
const target_1 = require("../../../src/services/generator/target");
describe('add', async () => {
    describe('projection', async () => {
        const readModelName = 'PostReadModel';
        const projectionName = 'Post:id';
        const sourceFileText = `
      import { ReadModel } from '@boostercloud/framework-core'
      import { UUID } from '@boostercloud/framework-types'
      
      @ReadModel({
        authorize: 'all',
      })
      export class PostReadModel {
        public constructor(public id: UUID, readonly title: string, readonly author: string) {}
      }
    `;
        beforeEach(() => {
            (0, sinon_1.stub)(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis();
            (0, sinon_1.replace)(ProjectChecker, 'checkCurrentDirBoosterVersion', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(Filenames, 'fileNameWithExtension', sinon_1.fake.returns('post-read-model.ts'));
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new projection_1.default([], {}).init();
            (0, expect_1.expect)(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        it('generates projection correctly', async () => {
            const { projection } = await (0, target_1.parseProjectionField)(projectionName);
            const project = new ts_morph_1.Project();
            const fakeSourceFile = project.createSourceFile('post.ts', sourceFileText);
            (0, sinon_1.stub)(ts_morph_1.Project.prototype, 'getSourceFileOrThrow').returns(fakeSourceFile);
            (0, sinon_1.stub)(MethodGenerator, 'generateProjection').returns({});
            (0, sinon_1.stub)(ts_morph_1.SourceFile.prototype, 'getClassOrThrow').returns({
                addMethod: (0, sinon_1.stub)(),
            });
            (0, sinon_1.stub)(ts_morph_1.SourceFile.prototype, 'fixMissingImports').returnsThis();
            (0, sinon_1.stub)(ts_morph_1.SourceFile.prototype, 'save').resolves();
            await new projection_1.default(['--read-model', readModelName, '--entity', projectionName], {}).run();
            (0, expect_1.expect)(Filenames.fileNameWithExtension).to.have.been.calledWith(readModelName);
            (0, expect_1.expect)(ts_morph_1.Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post-read-model.ts');
            (0, expect_1.expect)(MethodGenerator.generateProjection).to.have.been.calledOnceWith(readModelName, projection);
            (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.getClassOrThrow).to.have.been.calledOnceWith(readModelName);
            (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.fixMissingImports).to.have.been.calledOnce;
            (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.save).to.have.been.calledOnce;
        });
        describe('displays an error', () => {
            it('with flags missing', async () => {
                let exceptionThrown = false;
                let exceptionMessage = null;
                try {
                    await new projection_1.default([], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                console.log(exceptionMessage);
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(exceptionMessage).to.contain('Missing required flag read-model');
            });
            it('with empty --read-model', async () => {
                let exceptionThrown = false;
                let exceptionMessage = null;
                try {
                    await new projection_1.default(['--entity', projectionName, '--read-model'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(exceptionMessage).to.contain('--read-model expects a value');
            });
            it('with empty --entity', async () => {
                let exceptionThrown = false;
                let exceptionMessage = null;
                try {
                    await new projection_1.default(['--read-model', readModelName, '--entity'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(exceptionMessage).to.contain('--entity expects a value');
            });
            it("when projection doesn't have entity id", async () => {
                let exceptionThrown = false;
                let exceptionMessage = null;
                try {
                    await new projection_1.default(['--read-model', readModelName, '--entity', 'Post'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection Post. Projections must be in the form of <entity name>:<entity id>');
            });
            it('when projection has empty entity id', async () => {
                let exceptionThrown = false;
                let exceptionMessage = null;
                try {
                    await new projection_1.default(['--read-model', readModelName, '--entity', 'Post:'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection Post:. Projections must be in the form of <entity name>:<entity id>');
            });
            it('when projection has empty entity name', async () => {
                let exceptionThrown = false;
                let exceptionMessage = null;
                try {
                    await new projection_1.default(['--read-model', readModelName, '--entity', ':id'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection :id. Projections must be in the form of <entity name>:<entity id>');
            });
            it("when source file doesn't exist", async () => {
                (0, sinon_1.stub)(ts_morph_1.Project.prototype, 'getSourceFileOrThrow').throws();
                const sourceFileSpy = (0, sinon_1.spy)(ts_morph_1.SourceFile.prototype);
                const methodGeneratorSpy = (0, sinon_1.spy)(MethodGenerator.generateProjection);
                const classDeclarationSpy = (0, sinon_1.spy)(ts_morph_1.ClassDeclaration.prototype);
                let exceptionThrown = false;
                try {
                    await new projection_1.default(['--read-model', readModelName, '--entity', projectionName], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(Filenames.fileNameWithExtension).to.have.been.calledWith(readModelName);
                (0, expect_1.expect)(ts_morph_1.Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post-read-model.ts');
                (0, expect_1.expect)(classDeclarationSpy.addMethod).to.have.not.been.calledOnce;
                (0, expect_1.expect)(sourceFileSpy.getClassOrThrow).to.have.not.been.called;
                (0, expect_1.expect)(sourceFileSpy.fixMissingImports).to.have.not.been.called;
                (0, expect_1.expect)(sourceFileSpy.save).to.have.not.been.called;
                (0, expect_1.expect)(methodGeneratorSpy).to.have.not.been.called;
            });
            it("when class doesn't exist in source file", async () => {
                const project = new ts_morph_1.Project();
                const fakeSourceFile = project.createSourceFile('post.ts', sourceFileText);
                (0, sinon_1.stub)(ts_morph_1.Project.prototype, 'getSourceFileOrThrow').returns(fakeSourceFile);
                (0, sinon_1.replace)(ts_morph_1.SourceFile.prototype, 'getClassOrThrow', sinon_1.fake.throws(new Error()));
                (0, sinon_1.replace)(ts_morph_1.SourceFile.prototype, 'fixMissingImports', (0, sinon_1.spy)());
                (0, sinon_1.replace)(ts_morph_1.SourceFile.prototype, 'save', (0, sinon_1.spy)());
                const methodGeneratorSpy = (0, sinon_1.spy)(MethodGenerator.generateProjection);
                const classDeclarationSpy = (0, sinon_1.spy)(ts_morph_1.ClassDeclaration.prototype);
                let exceptionThrown = false;
                try {
                    await new projection_1.default(['--read-model', readModelName, '--entity', projectionName], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(Filenames.fileNameWithExtension).to.have.been.calledWith(readModelName);
                (0, expect_1.expect)(ts_morph_1.Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post-read-model.ts');
                (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.getClassOrThrow).to.throw;
                (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.fixMissingImports).to.have.not.been.called;
                (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.save).to.have.not.been.called;
                (0, expect_1.expect)(methodGeneratorSpy).to.have.not.been.called;
                (0, expect_1.expect)(methodGeneratorSpy).to.have.not.been.called;
                (0, expect_1.expect)(classDeclarationSpy.addMethod).to.have.not.been.calledOnce;
            });
        });
    });
});
