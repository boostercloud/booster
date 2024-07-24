"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const fs = require("fs-extra");
const path_1 = require("path");
const sinon_1 = require("sinon");
const ProjectChecker = require("../../../src/services/project-checker");
const expect_1 = require("../../expect");
const publish_1 = require("../../../src/commands/stub/publish");
const user_prompt_1 = require("../../../src/services/user-prompt");
const stub_publisher_1 = require("../../../src/services/stub-publisher");
const inquirer = require("inquirer");
describe('stub', async () => {
    describe('publish', async () => {
        let fakeMkdirSync;
        let fakeWriteFileSync;
        let fakeReadFileSync;
        const directoryFileMocks = [
            {
                name: 'fake-command.stub',
                path: '/someDir',
                isFile: () => true,
                isDirectory: () => false,
                isBlockDevice: () => false,
                isCharacterDevice: () => false,
                isSymbolicLink: () => false,
                isFIFO: () => false,
                isSocket: () => false,
            },
            {
                name: 'fake-event.stub',
                path: '/someDir',
                isFile: () => true,
                isDirectory: () => false,
                isBlockDevice: () => false,
                isCharacterDevice: () => false,
                isSymbolicLink: () => false,
                isFIFO: () => false,
                isSocket: () => false,
            },
            {
                name: 'fake-directory',
                path: '/someDir',
                isFile: () => false,
                isDirectory: () => true,
                isBlockDevice: () => false,
                isCharacterDevice: () => false,
                isSymbolicLink: () => false,
                isFIFO: () => false,
                isSocket: () => false,
            },
        ];
        beforeEach(() => {
            fakeMkdirSync = (0, sinon_1.fake)();
            fakeWriteFileSync = (0, sinon_1.fake)();
            fakeReadFileSync = (0, sinon_1.fake)();
            (0, sinon_1.stub)(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis();
            (0, sinon_1.replace)(ProjectChecker, 'checkCurrentDirBoosterVersion', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(fs, 'outputFile', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(fs, 'mkdirSync', fakeMkdirSync);
            (0, sinon_1.replace)(fs, 'writeFileSync', fakeWriteFileSync);
            (0, sinon_1.replace)(fs, 'readFileSync', fakeReadFileSync);
            (0, sinon_1.replace)(fs, 'readdirSync', sinon_1.fake.returns(directoryFileMocks));
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new publish_1.default([], {}).init();
            (0, expect_1.expect)(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        describe('Publishes stub files correctly', () => {
            it('when the `/stubs` folder does not yet exist', async () => {
                (0, sinon_1.stub)(fs, 'existsSync').returns(false);
                (0, sinon_1.spy)(user_prompt_1.default, 'confirmPrompt');
                await new publish_1.default([], {}).run();
                (0, expect_1.expect)(fs.existsSync).to.have.been.calledOnce;
                (0, expect_1.expect)(fs.existsSync).to.have.been.returned(false);
                (0, expect_1.expect)(fs.existsSync).to.have.been.calledWithMatch((0, path_1.join)(process.cwd(), 'stubs'));
                (0, expect_1.expect)(fakeMkdirSync).to.have.been.calledOnce;
                (0, expect_1.expect)(fakeMkdirSync).to.have.been.calledOnceWith((0, path_1.join)(process.cwd(), 'stubs'));
                (0, expect_1.expect)(ProjectChecker.checkCurrentDirIsABoosterProject).to.have.been.calledOnce;
                (0, expect_1.expect)(user_prompt_1.default.confirmPrompt).not.to.have.been.called;
                (0, expect_1.expect)(fs.readdirSync).to.have.been.calledOnceWith(stub_publisher_1.resourceTemplatesPath, { withFileTypes: true });
                (0, expect_1.expect)(fs.readdirSync).to.have.returned(directoryFileMocks);
                (0, expect_1.expect)(fakeReadFileSync).to.have.been.calledTwice;
                (0, expect_1.expect)(fakeWriteFileSync).to.have.been.calledTwice;
            });
            it('when the `/stubs` folder already exists', async () => {
                (0, sinon_1.stub)(fs, 'existsSync').returns(true);
                (0, sinon_1.stub)(inquirer, 'prompt').resolves({ confirm: true });
                (0, sinon_1.spy)(user_prompt_1.default, 'confirmPrompt');
                await new publish_1.default([], {}).run();
                (0, expect_1.expect)(fs.existsSync).to.have.been.calledOnce;
                (0, expect_1.expect)(fs.existsSync).to.have.been.returned(true);
                (0, expect_1.expect)(fs.existsSync).to.have.been.calledWithMatch((0, path_1.join)(process.cwd(), 'stubs'));
                (0, expect_1.expect)(fakeMkdirSync).not.to.have.been.calledOnce;
                (0, expect_1.expect)(fakeMkdirSync).not.to.have.been.calledOnceWith((0, path_1.join)(process.cwd(), 'stubs'));
                (0, expect_1.expect)(ProjectChecker.checkCurrentDirIsABoosterProject).to.have.been.calledOnce;
                (0, expect_1.expect)(user_prompt_1.default.confirmPrompt).to.have.been.called;
                (0, expect_1.expect)(fs.readdirSync).to.have.been.calledOnceWith(stub_publisher_1.resourceTemplatesPath, { withFileTypes: true });
                (0, expect_1.expect)(fs.readdirSync).to.have.returned(directoryFileMocks);
                (0, expect_1.expect)(fakeReadFileSync).to.have.been.calledTwice;
                (0, expect_1.expect)(fakeWriteFileSync).to.have.been.calledTwice;
            });
            it('when the `/stubs` folder already exists, but the --force flag is set', async () => {
                (0, sinon_1.stub)(fs, 'existsSync').returns(true);
                (0, sinon_1.spy)(user_prompt_1.default, 'confirmPrompt');
                await new publish_1.default(['--force'], {}).run();
                (0, expect_1.expect)(fs.existsSync).to.have.been.calledOnce;
                (0, expect_1.expect)(fs.existsSync).to.have.been.returned(true);
                (0, expect_1.expect)(fs.existsSync).to.have.been.calledWithMatch((0, path_1.join)(process.cwd(), 'stubs'));
                (0, expect_1.expect)(fakeMkdirSync).not.to.have.been.calledOnce;
                (0, expect_1.expect)(fakeMkdirSync).not.to.have.been.calledOnceWith((0, path_1.join)(process.cwd(), 'stubs'));
                (0, expect_1.expect)(ProjectChecker.checkCurrentDirIsABoosterProject).to.have.been.calledOnce;
                (0, expect_1.expect)(user_prompt_1.default.confirmPrompt).not.to.have.been.called;
                (0, expect_1.expect)(fs.readdirSync).to.have.been.calledOnceWith(stub_publisher_1.resourceTemplatesPath, { withFileTypes: true });
                (0, expect_1.expect)(fs.readdirSync).to.have.returned(directoryFileMocks);
                (0, expect_1.expect)(fakeReadFileSync).to.have.been.calledTwice;
                (0, expect_1.expect)(fakeWriteFileSync).to.have.been.calledTwice;
            });
        });
        describe('Displays error', () => {
            it('when the /stubs folder already exists and the user has not confirmed the overwrite', async () => {
                (0, sinon_1.stub)(fs, 'existsSync').returns(true);
                (0, sinon_1.stub)(inquirer, 'prompt').resolves({ confirm: false });
                (0, sinon_1.spy)(user_prompt_1.default, 'confirmPrompt');
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new publish_1.default([], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(user_prompt_1.default.confirmPrompt).to.have.been.called;
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.be.contain('Stubs folder already exists. Use --force option to overwrite files in it');
                (0, expect_1.expect)(fs.readdirSync).not.to.have.been.called;
                (0, expect_1.expect)(fakeReadFileSync).not.to.have.been.called;
                (0, expect_1.expect)(fakeWriteFileSync).not.to.have.been.called;
            });
        });
    });
});
