"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_1 = require("sinon");
const path_1 = require("path");
const fs = require("fs-extra");
const expect_1 = require("../expect");
const stub_publisher_1 = require("../../src/services/stub-publisher");
const rewire = require('rewire');
describe('stub publisher', () => {
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
            name: 'fake-stub.ts',
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
            name: 'fake-directory-1',
            path: '/someDir',
            isFile: () => false,
            isDirectory: () => true,
            isBlockDevice: () => false,
            isCharacterDevice: () => false,
            isSymbolicLink: () => false,
            isFIFO: () => false,
            isSocket: () => false,
        },
        {
            name: 'fake-directory-2',
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
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('resourceStubFilePath and resourceTemplateFilePath', () => {
        it('should return path to stub file', () => {
            const fileName = 'test-command.stub';
            const stubFilePath = (0, stub_publisher_1.resourceStubFilePath)(fileName);
            const expectedStubFilePath = (0, path_1.join)(process.cwd(), 'stubs', fileName);
            (0, expect_1.expect)(stubFilePath).to.be.equal(expectedStubFilePath);
        });
        it('should return path to template file', () => {
            const fileName = 'test-command.stub';
            const stubFilePath = (0, stub_publisher_1.resourceTemplateFilePath)(fileName);
            const expectedTemplateFilePath = (0, path_1.join)(stub_publisher_1.resourceTemplatesPath, fileName);
            (0, expect_1.expect)(stubFilePath).to.be.equal(expectedTemplateFilePath);
        });
    });
    describe('checkStubsFolderExists', () => {
        it('should return true if `/stubs` folder exists', () => {
            (0, sinon_1.stub)(fs, 'existsSync').returns(true);
            const result = (0, stub_publisher_1.checkStubsFolderExists)();
            (0, expect_1.expect)(result).to.be.true;
            (0, expect_1.expect)(fs.existsSync).to.have.been.calledOnce;
            (0, expect_1.expect)(fs.existsSync).to.be.calledOnceWith((0, path_1.join)(process.cwd(), 'stubs'));
        });
        it('should return false if `/stubs` folder does not exists', () => {
            (0, sinon_1.stub)(fs, 'existsSync').returns(false);
            const result = (0, stub_publisher_1.checkStubsFolderExists)();
            (0, expect_1.expect)(result).to.be.false;
            (0, expect_1.expect)(fs.existsSync).to.have.been.calledOnce;
            (0, expect_1.expect)(fs.existsSync).to.be.calledOnceWith((0, path_1.join)(process.cwd(), 'stubs'));
        });
    });
    describe('checkResourceStubFileExists', () => {
        it('should return true if given file exists in `/stubs` folder', () => {
            (0, sinon_1.stub)(fs, 'existsSync').returns(true);
            const filePath = (0, stub_publisher_1.resourceStubFilePath)('command.stub');
            const fileExists = (0, stub_publisher_1.checkResourceStubFileExists)(filePath);
            (0, expect_1.expect)(fileExists).to.be.true;
            (0, expect_1.expect)(fs.existsSync).to.have.been.calledOnce;
            (0, expect_1.expect)(fs.existsSync).to.be.calledOnceWith(filePath);
        });
        it('should return false if given file exists in `/stubs` folder', () => {
            (0, sinon_1.stub)(fs, 'existsSync').returns(false);
            const filePath = (0, stub_publisher_1.resourceStubFilePath)('command.stub');
            const fileExists = (0, stub_publisher_1.checkResourceStubFileExists)(filePath);
            (0, expect_1.expect)(fileExists).to.be.false;
            (0, expect_1.expect)(fs.existsSync).to.have.been.calledOnce;
            (0, expect_1.expect)(fs.existsSync).to.be.calledOnceWith(filePath);
        });
    });
    describe('createStubsFolder', () => {
        it('creates `/stubs` folder', () => {
            const fakeMkdirSync = (0, sinon_1.fake)();
            (0, sinon_1.replace)(fs, 'mkdirSync', fakeMkdirSync);
            (0, stub_publisher_1.createStubsFolder)();
            (0, expect_1.expect)(fakeMkdirSync).to.have.been.calledOnce;
            (0, expect_1.expect)(fakeMkdirSync).to.have.been.calledOnceWith((0, path_1.join)(process.cwd(), 'stubs'));
        });
    });
    describe('createTemplateFileMap', () => {
        describe('filters out directories', () => {
            it('when has no directory', () => {
                const filteredFiles = (0, stub_publisher_1.createTemplateFileMap)([
                    {
                        name: 'fake-file-1.stub',
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
                        name: 'fake-file-2.stub',
                        path: '/someDir',
                        isFile: () => true,
                        isDirectory: () => false,
                        isBlockDevice: () => false,
                        isCharacterDevice: () => false,
                        isSymbolicLink: () => false,
                        isFIFO: () => false,
                        isSocket: () => false,
                    },
                ]);
                (0, expect_1.expect)(Object.keys(filteredFiles)).to.have.lengthOf(2);
            });
            it('when has directory', () => {
                const filteredFiles = (0, stub_publisher_1.createTemplateFileMap)(directoryFileMocks);
                (0, expect_1.expect)(Object.keys(filteredFiles)).to.have.lengthOf(2);
            });
            it('when has no files', () => {
                const filteredFiles = (0, stub_publisher_1.createTemplateFileMap)([
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
                ]);
                (0, expect_1.expect)(Object.keys(filteredFiles)).to.have.lengthOf(0);
            });
            it('when there are files other than .stub', () => {
                const filteredFiles = (0, stub_publisher_1.createTemplateFileMap)([
                    {
                        name: 'fake-stub.ts',
                        path: '/someDir',
                        isFile: () => true,
                        isDirectory: () => false,
                        isBlockDevice: () => false,
                        isCharacterDevice: () => false,
                        isSymbolicLink: () => false,
                        isFIFO: () => false,
                        isSocket: () => false,
                    },
                ]);
                (0, expect_1.expect)(Object.keys(filteredFiles)).to.have.lengthOf(0);
            });
        });
        it('generates template file map', () => {
            const filteredFiles = (0, stub_publisher_1.createTemplateFileMap)(directoryFileMocks);
            (0, expect_1.expect)(Object.keys(filteredFiles)).to.have.lengthOf(2);
            (0, expect_1.expect)(filteredFiles).to.deep.equal({
                [(0, path_1.join)(stub_publisher_1.resourceTemplatesPath, 'fake-command.stub')]: (0, path_1.join)(process.cwd(), 'stubs', 'fake-command.stub'),
                [(0, path_1.join)(stub_publisher_1.resourceTemplatesPath, 'fake-event.stub')]: (0, path_1.join)(process.cwd(), 'stubs', 'fake-event.stub'),
            });
        });
    });
    describe('publishStubFiles', () => {
        beforeEach(() => {
            (0, sinon_1.replace)(fs, 'readdirSync', sinon_1.fake.returns(directoryFileMocks));
        });
        it('copies template files', () => {
            const fakeWriteFileSync = (0, sinon_1.fake)();
            const fakeReadFileSync = (0, sinon_1.fake)();
            (0, sinon_1.replace)(fs, 'writeFileSync', fakeWriteFileSync);
            (0, sinon_1.replace)(fs, 'readFileSync', fakeReadFileSync);
            void (0, stub_publisher_1.publishStubFiles)();
            (0, expect_1.expect)(fs.readdirSync).to.have.been.calledOnceWith(stub_publisher_1.resourceTemplatesPath, { withFileTypes: true });
            (0, expect_1.expect)(fs.readdirSync).to.have.returned(directoryFileMocks);
            (0, expect_1.expect)(fakeWriteFileSync).to.have.been.calledTwice;
            (0, expect_1.expect)(fakeReadFileSync).to.have.been.calledTwice;
        });
        it("throws error if can't copy stub file", async () => {
            (0, sinon_1.replace)(fs, 'writeFileSync', sinon_1.fake.throws(new Error()));
            let exceptionThrown = false;
            await (0, stub_publisher_1.publishStubFiles)().catch(() => {
                exceptionThrown = true;
            });
            (0, expect_1.expect)(exceptionThrown).to.be.true;
        });
    });
    describe('copyStubFile', () => {
        const filenames = rewire('../../src/services/stub-publisher');
        const copyStubFile = filenames.__get__('copyStubFile');
        it('should copy stub file', () => {
            const from = 'from/test.stub';
            const to = 'to/test.stub';
            const fakeContent = 'file content!';
            const fakeWriteFileSync = (0, sinon_1.fake)();
            (0, sinon_1.replace)(fs, 'writeFileSync', fakeWriteFileSync);
            (0, sinon_1.replace)(fs, 'readFileSync', sinon_1.fake.returns(fakeContent));
            copyStubFile(from, to);
            (0, expect_1.expect)(fakeWriteFileSync).to.have.been.calledOnce;
            (0, expect_1.expect)(fakeWriteFileSync).to.have.been.calledOnce;
            (0, expect_1.expect)(fakeWriteFileSync).to.have.been.calledOnceWith(to, fakeContent);
            (0, expect_1.expect)(fs.readFileSync).to.have.been.calledOnceWith(from);
            (0, expect_1.expect)(fs.readFileSync).to.returned(fakeContent);
        });
    });
});
