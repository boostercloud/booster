"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const path = require("path");
const project_checker_1 = require("../../src/services/project-checker");
const sinon_1 = require("sinon");
const logger_1 = require("../../src/services/logger");
const fs = require("fs-extra");
const process = require("process");
const project_initializer_1 = require("../../src/services/project-initializer");
const user_prompt_1 = require("../../src/services/user-prompt");
const expect_1 = require("../expect");
describe('project checker', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('checkCurrentDirIsABoosterProject', () => {
        beforeEach(() => {
            (0, sinon_1.restore)();
        });
        it('is a Booster project', async () => {
            (0, sinon_1.replace)(process, 'cwd', sinon_1.fake.returns(path.join(process.cwd(), 'test', 'fixtures', 'mock_project')));
            let exceptionThrown = false;
            await (0, project_checker_1.checkCurrentDirIsABoosterProject)().catch(() => (exceptionThrown = true));
            (0, expect_1.expect)(exceptionThrown).to.be.equal(false);
        });
        it('is a Booster project with bad index.ts', async () => {
            (0, sinon_1.replace)(process, 'cwd', sinon_1.fake.returns(path.join(process.cwd(), 'test', 'fixtures', 'mock_project_bad_index')));
            let exceptionThrown = false;
            await (0, project_checker_1.checkCurrentDirIsABoosterProject)().catch(() => (exceptionThrown = true));
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
        });
        it('is not a Booster project', async () => {
            (0, sinon_1.replace)(process, 'cwd', sinon_1.fake.returns(path.join(process.cwd(), 'test', 'fixtures')));
            let exceptionThrown = false;
            await (0, project_checker_1.checkCurrentDirIsABoosterProject)().catch(() => (exceptionThrown = true));
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
        });
    });
    describe('checkItIsABoosterProject', () => {
        it('is a Booster project', async () => {
            const projectPath = path.join(process.cwd(), 'test', 'fixtures', 'mock_project');
            let exceptionThrown = false;
            await (0, project_checker_1.checkItIsABoosterProject)(projectPath).catch(() => (exceptionThrown = true));
            (0, expect_1.expect)(exceptionThrown).to.be.equal(false);
        });
        it('is a Booster project with bad index.ts', async () => {
            const projectPath = path.join(process.cwd(), 'test', 'fixtures', 'mock_project_bad_index');
            let exceptionThrown = false;
            await (0, project_checker_1.checkItIsABoosterProject)(projectPath).catch(() => (exceptionThrown = true));
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
        });
        it('is not a Booster project', async () => {
            const projectPath = path.join(process.cwd(), 'test', 'fixtures');
            let exceptionThrown = false;
            await (0, project_checker_1.checkItIsABoosterProject)(projectPath).catch(() => (exceptionThrown = true));
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
        });
    });
    describe('checkProjectAlreadyExists', () => {
        it('should do nothing if project with given name does not exist', async () => {
            const existsSyncStub = (0, sinon_1.stub)(fs, 'existsSync');
            existsSyncStub.returns(false);
            (0, sinon_1.spy)(user_prompt_1.default, 'confirmPrompt');
            const projectName = path.join('test', 'fixtures', 'mock_project_test');
            const projectPath = (0, project_initializer_1.projectDir)({ projectName });
            await (0, project_checker_1.checkProjectAlreadyExists)(projectName);
            (0, expect_1.expect)(fs.existsSync).to.have.been.calledWithMatch(projectPath);
            (0, expect_1.expect)(user_prompt_1.default.confirmPrompt).not.to.have.been.called;
        });
        it('should throw error when project exists and user refuses to overwrite it', async () => {
            const existsSyncStub = (0, sinon_1.stub)(fs, 'existsSync');
            existsSyncStub.returns(true);
            const fakePrompter = sinon_1.fake.resolves(false);
            (0, sinon_1.replace)(user_prompt_1.default, 'confirmPrompt', fakePrompter);
            const projectName = path.join('test', 'fixtures', 'mock_project_test');
            const projectPath = (0, project_initializer_1.projectDir)({ projectName });
            let exceptionThrown = false;
            let exceptionMessage = '';
            await (0, project_checker_1.checkProjectAlreadyExists)(projectName).catch((e) => {
                exceptionThrown = true;
                exceptionMessage = e.message;
            });
            (0, expect_1.expect)(fs.existsSync).to.have.been.calledWithMatch(projectPath);
            (0, expect_1.expect)(user_prompt_1.default.confirmPrompt).to.have.been.called;
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.be.equal("The folder you're trying to use already exists. Please use another project name");
        });
        it('should remove project folder when project already exists and user agreed to overwrite it', async () => {
            (0, sinon_1.replace)(fs, 'removeSync', sinon_1.fake.resolves({}));
            const existsSyncStub = (0, sinon_1.stub)(fs, 'existsSync');
            existsSyncStub.returns(true);
            const fakePrompter = sinon_1.fake.resolves(true);
            (0, sinon_1.replace)(user_prompt_1.default, 'confirmPrompt', fakePrompter);
            const projectName = path.join('test', 'fixtures', 'mock_project_test');
            const projectPath = (0, project_initializer_1.projectDir)({ projectName });
            await (0, project_checker_1.checkProjectAlreadyExists)(projectName);
            (0, expect_1.expect)(fs.removeSync).to.have.been.calledWithMatch(projectPath);
        });
    });
    describe('checkResourceExists', () => {
        beforeEach(() => {
            (0, sinon_1.replace)(logger_1.logger, 'info', sinon_1.fake.resolves({}));
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it("should print info message and do nothing if resource doesn't exist", async () => {
            const resourcePath = path.join('test', 'fixtures', 'mock_project', 'src', 'entities');
            const existsSyncStub = (0, sinon_1.stub)(fs, 'existsSync');
            existsSyncStub.returns(false);
            (0, sinon_1.spy)(user_prompt_1.default, 'confirmPrompt');
            await (0, project_checker_1.checkResourceExists)('TestResource', resourcePath, '.ts');
            (0, expect_1.expect)(logger_1.logger.info).to.have.been.calledWithMatch('Checking if resource already exists...');
            (0, expect_1.expect)(fs.existsSync).to.have.been.calledWithMatch(resourcePath);
            (0, expect_1.expect)(user_prompt_1.default.confirmPrompt).not.to.have.been.called;
        });
        it('should throw error when resource exists and user refuses to overwrite it', async () => {
            const resourcePath = path.join('test', 'fixtures', 'mock_project', 'src', 'entities');
            const fakePrompter = sinon_1.fake.resolves(false);
            const existsSyncStub = (0, sinon_1.stub)(fs, 'existsSync');
            let exceptionThrown = false;
            let exceptionMessage = '';
            existsSyncStub.returns(true);
            (0, sinon_1.replace)(user_prompt_1.default, 'confirmPrompt', fakePrompter);
            await (0, project_checker_1.checkResourceExists)('TestResource', resourcePath, '.ts').catch((e) => {
                exceptionThrown = true;
                exceptionMessage = e.message;
            });
            (0, expect_1.expect)(fs.existsSync).to.have.been.calledWithMatch(resourcePath);
            (0, expect_1.expect)(user_prompt_1.default.confirmPrompt).to.have.been.called;
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.be.equal('The \'entities\' resource "test-resource.ts" already exists. Please use another resource name');
        });
        it('should remove resource when it exists and user agreed to overwrite it', async () => {
            const resourcePath = path.join('test', 'fixtures', 'mock_project', 'src', 'entities');
            const fakePrompter = sinon_1.fake.resolves(true);
            const existsSyncStub = (0, sinon_1.stub)(fs, 'existsSync');
            existsSyncStub.returns(true);
            (0, sinon_1.replace)(fs, 'removeSync', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(user_prompt_1.default, 'confirmPrompt', fakePrompter);
            await (0, project_checker_1.checkResourceExists)('TestResource', resourcePath, '.ts');
            (0, expect_1.expect)(fs.removeSync).to.have.been.calledWithMatch(resourcePath);
        });
    });
    describe('checkCurrentDirBoosterVersion', () => {
        beforeEach(() => {
            (0, sinon_1.replace)(logger_1.logger, 'info', sinon_1.fake.resolves({}));
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        describe('inside a Booster project', () => {
            //project version in mocked package.json is 1.11.2
            beforeEach(() => {
                (0, sinon_1.replace)(process, 'cwd', sinon_1.fake.returns(path.join(process.cwd(), 'test', 'fixtures', 'mock_project')));
            });
            afterEach(() => {
                (0, sinon_1.restore)();
            });
            it('versions match', async () => {
                const cliVersion = '1.11.2';
                let exceptionThrown = false;
                await (0, project_checker_1.checkCurrentDirBoosterVersion)(cliVersion).catch(() => (exceptionThrown = true));
                (0, expect_1.expect)(exceptionThrown).to.be.equal(false);
                (0, expect_1.expect)(logger_1.logger.info).have.not.been.called;
            });
            it('versions differs in fix number with cli version greater than project version', async () => {
                const cliVersion = '1.11.3';
                let exceptionThrown = false;
                await (0, project_checker_1.checkCurrentDirBoosterVersion)(cliVersion).catch(() => (exceptionThrown = true));
                (0, expect_1.expect)(exceptionThrown).to.be.equal(false);
                (0, expect_1.expect)(logger_1.logger.info).have.been.calledWithMatch(/WARNING: Project Booster version differs in the 'fix' section/);
            });
            it('versions differs in fix number with cli version lower than project version', async () => {
                const cliVersion = '1.11.0';
                let exceptionThrown = false;
                await (0, project_checker_1.checkCurrentDirBoosterVersion)(cliVersion).catch(() => (exceptionThrown = true));
                (0, expect_1.expect)(exceptionThrown).to.be.equal(false);
                (0, expect_1.expect)(logger_1.logger.info).have.been.calledWithMatch(/WARNING: Project Booster version differs in the 'fix' section/);
            });
            it('cli lower than project version in <feature> section', async () => {
                const cliVersion = '1.10.2';
                let exceptionThrown = false;
                let exceptionMessage = '';
                await (0, project_checker_1.checkCurrentDirBoosterVersion)(cliVersion).catch((e) => {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                });
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Please upgrade your @boostercloud/cli to the same version with "npm');
                (0, expect_1.expect)(logger_1.logger.info).have.not.been.called;
            });
            it('cli lower than project version in <breaking> section', async () => {
                const cliVersion = '0.11.2';
                let exceptionThrown = false;
                let exceptionMessage = '';
                await (0, project_checker_1.checkCurrentDirBoosterVersion)(cliVersion).catch((e) => {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                });
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Please upgrade your @boostercloud/cli to the same version with "npm');
                (0, expect_1.expect)(logger_1.logger.info).have.not.been.called;
            });
            it('cli version higher than project version in <feature> section', async () => {
                const cliVersion = '1.12.2';
                let exceptionThrown = false;
                let exceptionMessage = '';
                await (0, project_checker_1.checkCurrentDirBoosterVersion)(cliVersion).catch((e) => {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                });
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Please upgrade your project Booster dependencies');
                (0, expect_1.expect)(logger_1.logger.info).have.not.been.called;
            });
            it('cli version higher than project version in <breaking> section', async () => {
                const cliVersion = '2.11.2';
                let exceptionThrown = false;
                let exceptionMessage = '';
                await (0, project_checker_1.checkCurrentDirBoosterVersion)(cliVersion).catch((e) => {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                });
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Please upgrade your project Booster dependencies');
                (0, expect_1.expect)(logger_1.logger.info).have.not.been.called;
            });
            it('cli version wrong length shorter', async () => {
                const cliVersion = '1.11';
                let exceptionThrown = false;
                let exceptionMessage = '';
                await (0, project_checker_1.checkCurrentDirBoosterVersion)(cliVersion).catch((e) => {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                });
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Versions must follow semantic convention X.Y.Z');
                (0, expect_1.expect)(logger_1.logger.info).have.not.been.called;
            });
            it('cli version wrong length longer', async () => {
                const cliVersion = '1.11.2.1';
                let exceptionThrown = false;
                let exceptionMessage = '';
                await (0, project_checker_1.checkCurrentDirBoosterVersion)(cliVersion).catch((e) => {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                });
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Versions must follow semantic convention X.Y.Z');
                (0, expect_1.expect)(logger_1.logger.info).have.not.been.called;
            });
        });
        it('outside a Booster project', async () => {
            (0, sinon_1.replace)(process, 'cwd', sinon_1.fake.returns(path.join(process.cwd(), 'test', 'fixtures')));
            const cliVersion = '1.11.2';
            let exceptionThrown = false;
            let exceptionMessage = '';
            await (0, project_checker_1.checkCurrentDirBoosterVersion)(cliVersion).catch((e) => {
                exceptionThrown = true;
                exceptionMessage = e.message;
            });
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('There was an error when recognizing the application');
            (0, expect_1.expect)(logger_1.logger.info).have.not.been.called;
        });
    });
});
