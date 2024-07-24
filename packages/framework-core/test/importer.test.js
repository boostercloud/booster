"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const sinon_1 = require("sinon");
const fs = require("fs");
const path = require("path");
const importer_1 = require("../src/importer");
const expect_1 = require("./expect");
describe('the `importer` service', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    // FIXME
    describe('the `importUserProjectFiles` function', () => {
        it('calls `require` for each import file', () => {
            const codeRelativePath = 'dist';
            const fakeStatSync = (fileName) => ({
                isDirectory: () => !fileName.includes('.'),
            });
            (0, sinon_1.replace)(fs, 'statSync', fakeStatSync);
            const fakeReaddirSync = (0, sinon_1.stub)()
                .onFirstCall()
                .returns(['src', 'index.js', 'lol.js'])
                .onSecondCall()
                .returns(['index.js', 'test.ts', 'types.d.ts', 'lol.js']);
            (0, sinon_1.replace)(fs, 'readdirSync', fakeReaddirSync);
            const fakeImportWithoutExtension = (0, sinon_1.spy)();
            (0, sinon_1.replace)(importer_1.Importer, 'importWithoutExtension', fakeImportWithoutExtension);
            importer_1.Importer.importUserProjectFiles(codeRelativePath);
            (0, expect_1.expect)(fakeImportWithoutExtension).to.have.been.calledTwice;
            (0, expect_1.expect)(fakeImportWithoutExtension.firstCall).to.have.been.calledWith(path.join(codeRelativePath, 'src', 'lol.js'));
            (0, expect_1.expect)(fakeImportWithoutExtension.secondCall).to.have.been.calledWith(path.join(codeRelativePath, 'lol.js'));
        });
    });
});
