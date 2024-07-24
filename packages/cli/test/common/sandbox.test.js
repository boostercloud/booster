"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_1 = require("sinon");
const expect_1 = require("../expect");
const fs = require("fs");
const fse = require("fs-extra");
const sandbox_1 = require("../../src/common/sandbox");
const path = require("path");
describe('sandbox', () => {
    let fakeRmSync;
    let fakeMkdirSync;
    let fakeCopySync;
    beforeEach(() => {
        fakeRmSync = (0, sinon_1.fake)();
        fakeMkdirSync = (0, sinon_1.fake)();
        fakeCopySync = (0, sinon_1.fake)();
        const fakeStatSync = (fileName) => ({
            isDirectory: () => !fileName.includes('.'),
        });
        (0, sinon_1.replace)(fs, 'rmSync', fakeRmSync);
        (0, sinon_1.replace)(fs, 'mkdirSync', fakeMkdirSync);
        (0, sinon_1.replace)(fse, 'copySync', fakeCopySync);
        (0, sinon_1.replace)(fs, 'statSync', fakeStatSync);
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('createSandboxProject', () => {
        it('copy all needed files', async () => {
            const fakeReaddirSync = (0, sinon_1.stub)()
                .onFirstCall()
                .returns([fakeDirent('commands', true), fakeDirent('index.js')])
                .onSecondCall()
                .returns([fakeDirent('commandA.js'), fakeDirent('commandB.js')])
                .onThirdCall()
                .returns([fakeDirent('assetFile1.txt'), fakeDirent('assetFile2.txt')]);
            (0, sinon_1.replace)(fs, 'readdirSync', fakeReaddirSync);
            const sandboxPath = 'testProjectPath';
            const projectAssets = ['assetFolder', 'assetFile3.txt'];
            (0, sandbox_1.createSandboxProject)(sandboxPath, projectAssets);
            (0, expect_1.expect)(fakeRmSync).to.have.been.calledOnceWith(sandboxPath);
            (0, expect_1.expect)(fakeMkdirSync).to.have.been.calledTwice;
            (0, expect_1.expect)(fakeMkdirSync).to.have.been.calledWith(sandboxPath);
            (0, expect_1.expect)(fakeMkdirSync).to.have.been.calledWith(path.join(sandboxPath, 'src', 'commands'));
            (0, expect_1.expect)(fakeReaddirSync).to.have.been.calledThrice;
            (0, expect_1.expect)(fakeReaddirSync).to.have.been.calledWith('src');
            (0, expect_1.expect)(fakeReaddirSync).to.have.been.calledWith(path.join('src', 'commands'));
            (0, expect_1.expect)(fakeReaddirSync).to.have.been.calledWith(path.join('assetFolder'));
            (0, expect_1.expect)(fakeCopySync).to.have.callCount(8);
            const copyFileCallsArguments = [
                ['package.json', path.join(sandboxPath, 'package.json')],
                ['tsconfig.json', path.join(sandboxPath, 'tsconfig.json')],
                [path.join('src', 'index.js'), path.join(sandboxPath, 'src', 'index.js')],
                [path.join('src', 'commands', 'commandA.js'), path.join(sandboxPath, 'src', 'commands', 'commandA.js')],
                [path.join('src', 'commands', 'commandB.js'), path.join(sandboxPath, 'src', 'commands', 'commandB.js')],
                [path.join('assetFolder', 'assetFile1.txt'), path.join(sandboxPath, 'assetFolder', 'assetFile1.txt')],
                [path.join('assetFolder', 'assetFile2.txt'), path.join(sandboxPath, 'assetFolder', 'assetFile2.txt')],
                ['assetFile3.txt', path.join(sandboxPath, 'assetFile3.txt')],
            ];
            copyFileCallsArguments.forEach((args) => {
                (0, expect_1.expect)(fakeCopySync).to.have.been.calledWith(...args);
            });
        });
    });
});
function fakeDirent(name, isDirectory = false) {
    return {
        name,
        isFile: () => !isDirectory,
        isDirectory: () => isDirectory,
    };
}
