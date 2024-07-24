"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_1 = require("sinon");
const ts_morph_1 = require("ts-morph");
const ProjectChecker = require("../../../src/services/project-checker");
const expect_1 = require("../../expect");
const reducer_1 = require("../../../src/commands/add/reducer");
const MethodGenerator = require("../../../src/services/method-generator");
const Filenames = require("../../../src/common/filenames");
const logger_1 = require("../../../src/services/logger");
describe('add', async () => {
    describe('reducer', async () => {
        const entityName = 'Post';
        const sourceFileText = `
    import { Entity } from '@boostercloud/framework-core'
    import { UUID } from '@boostercloud/framework-types'
    
    @Entity
    export class Post {
      public constructor(public id: UUID, readonly title: string, readonly content: string, readonly author: string) {}
    }
    `;
        beforeEach(() => {
            (0, sinon_1.stub)(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis();
            (0, sinon_1.replace)(ProjectChecker, 'checkCurrentDirBoosterVersion', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(Filenames, 'fileNameWithExtension', sinon_1.fake.returns('post.ts'));
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new reducer_1.default([], {}).init();
            (0, expect_1.expect)(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        describe('Created correctly', () => {
            const project = new ts_morph_1.Project();
            const fakeSourceFile = project.createSourceFile('post.ts', sourceFileText);
            beforeEach(() => {
                (0, sinon_1.stub)(ts_morph_1.Project.prototype, 'getSourceFileOrThrow').returns(fakeSourceFile);
                (0, sinon_1.stub)(MethodGenerator, 'generateReducers').returns([{}]);
                (0, sinon_1.stub)(ts_morph_1.SourceFile.prototype, 'getClassOrThrow').returns({
                    addMethods: (0, sinon_1.stub)(),
                });
                (0, sinon_1.stub)(ts_morph_1.SourceFile.prototype, 'fixMissingImports').returnsThis();
                (0, sinon_1.stub)(ts_morph_1.SourceFile.prototype, 'save').resolves();
                (0, sinon_1.replace)(logger_1.oraLogger, 'info', sinon_1.fake.resolves({}));
            });
            afterEach(() => {
                (0, sinon_1.restore)();
            });
            it('generates reducer correctly', async () => {
                await new reducer_1.default(['--entity', entityName, '--event', 'PostCreated'], {}).run();
                (0, expect_1.expect)(Filenames.fileNameWithExtension).to.have.been.calledWith(entityName);
                (0, expect_1.expect)(ts_morph_1.Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post.ts');
                (0, expect_1.expect)(MethodGenerator.generateReducers).to.have.been.calledOnceWith(entityName, [{ eventName: 'PostCreated' }]);
                (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.getClassOrThrow).to.have.been.calledOnceWith(entityName);
                (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.fixMissingImports).to.have.been.calledOnce;
                (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.save).to.have.been.calledOnce;
                (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch(/Reducer generated!/);
            });
            it('generates multiple reducers correctly', async () => {
                await new reducer_1.default(['--entity', entityName, '--event', 'PostCreated', 'PostUpdated'], {}).run();
                (0, expect_1.expect)(Filenames.fileNameWithExtension).to.have.been.calledWith(entityName);
                (0, expect_1.expect)(ts_morph_1.Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post.ts');
                (0, expect_1.expect)(MethodGenerator.generateReducers).to.have.been.calledOnceWith(entityName, [
                    { eventName: 'PostCreated' },
                    { eventName: 'PostUpdated' },
                ]);
                (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.getClassOrThrow).to.have.been.calledOnceWith(entityName);
                (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.fixMissingImports).to.have.been.calledOnce;
                (0, expect_1.expect)(ts_morph_1.SourceFile.prototype.save).to.have.been.calledOnce;
                (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch(/Reducers generated!/);
            });
        });
        describe('displays an error', () => {
            it('with flags missing', async () => {
                let exceptionThrown = false;
                let exceptionMessage = null;
                try {
                    await new reducer_1.default([], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(exceptionMessage).to.contain('Missing required flag entity');
            });
            it('with empty --entity flag', async () => {
                let exceptionThrown = false;
                let exceptionMessage = null;
                try {
                    await new reducer_1.default(['--event', 'PostCreated', '--entity'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(exceptionMessage).to.contain('--entity expects a value');
            });
            it('with empty --event flag', async () => {
                let exceptionThrown = false;
                let exceptionMessage = null;
                try {
                    await new reducer_1.default(['--entity', entityName, '--event'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(exceptionMessage).to.contain('--event expects a value');
            });
            it("when source file doesn't exist", async () => {
                (0, sinon_1.stub)(ts_morph_1.Project.prototype, 'getSourceFileOrThrow').throws();
                const sourceFileSpy = (0, sinon_1.spy)(ts_morph_1.SourceFile.prototype);
                const methodGeneratorSpy = (0, sinon_1.spy)(MethodGenerator.generateProjection);
                const classDeclarationSpy = (0, sinon_1.spy)(ts_morph_1.ClassDeclaration.prototype);
                let exceptionThrown = false;
                try {
                    await new reducer_1.default(['--entity', entityName, '--event', 'PostCreated'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(Filenames.fileNameWithExtension).to.have.been.calledWith(entityName);
                (0, expect_1.expect)(ts_morph_1.Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post.ts');
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
                    await new reducer_1.default(['--entity', entityName, '--event', 'PostUpdated'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.true;
                (0, expect_1.expect)(Filenames.fileNameWithExtension).to.have.been.calledWith(entityName);
                (0, expect_1.expect)(ts_morph_1.Project.prototype.getSourceFileOrThrow).to.have.been.calledOnceWith('post.ts');
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
