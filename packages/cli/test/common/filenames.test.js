"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const filenames_1 = require("../../src/common/filenames");
const expect_1 = require("../expect");
const rewire = require('rewire');
const filenames = rewire('../../src/common/filenames');
const formatResourceName = filenames.__get__('formatResourceName');
const titleCaseString = filenames.__get__('titleCaseString');
describe('filenames', () => {
    describe('checkResourceNameIsValid', () => {
        it('should do nothing if resource name is valid PascalCase', () => {
            let exceptionThrown = false;
            try {
                (0, filenames_1.checkResourceNameIsValid)('TestResource');
            }
            catch (e) {
                exceptionThrown = false;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(false);
        });
        it('should throw error if resource name is not valid PascalCase ', () => {
            const resourceName = 'test resource';
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                (0, filenames_1.checkResourceNameIsValid)(resourceName);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.be.equal(`'${resourceName}' is not valid resource name. Please use PascalCase name with valid characters.`);
        });
    });
    describe('formatResourceName', () => {
        describe('valid PascalCase', () => {
            it('from string with spaces', () => {
                (0, expect_1.expect)(formatResourceName('test case')).to.be.equal('TestCase');
            });
            it('from single character string', () => {
                (0, expect_1.expect)(formatResourceName('t')).to.be.equal('T');
            });
            it('from string with spaces and invalid characters ', () => {
                (0, expect_1.expect)(formatResourceName('_ =& test resource^name*With 21   =Fields')).to.be.equal('TestResourceNameWith21Fields');
            });
            it('from string with underline characters', () => {
                (0, expect_1.expect)(formatResourceName('test_resource_name')).to.be.equal('TestResourceName');
            });
        });
        describe('should return null when', () => {
            it('string is empty', () => {
                (0, expect_1.expect)(formatResourceName('')).to.be.null;
            });
            it('string is empty after removing invalid characters', () => {
                (0, expect_1.expect)(formatResourceName('###!##-==-_-')).to.be.null;
            });
        });
    });
    describe('titleCaseString', () => {
        it('should transform string to title case', () => {
            (0, expect_1.expect)(titleCaseString('test')).to.be.equal('Test');
        });
        it('should transform string[] to expected title case', () => {
            const stringsMap = [
                ['first', 'First'],
                ['second', 'Second'],
                ['third', 'Third'],
            ];
            stringsMap.forEach((item) => {
                (0, expect_1.expect)(titleCaseString(item[0])).to.be.equal(item[1]);
            });
        });
    });
    describe('classNameToFileName', () => {
        it('transforms passed resource name to correct file name', () => {
            const generatedClassName = (0, filenames_1.classNameToFileName)('testResource');
            (0, expect_1.expect)(generatedClassName).to.equal('test-resource');
        });
        it('transforms PascalCased resource name to correct file name', () => {
            const generatedClassName = (0, filenames_1.classNameToFileName)('TestResource');
            (0, expect_1.expect)(generatedClassName).to.equal('test-resource');
        });
    });
    describe('fileNameWithExtension', () => {
        it('transforms passed name to correct file name with default extension', function () {
            const generatedFilename = (0, filenames_1.fileNameWithExtension)('TestFileName');
            (0, expect_1.expect)(generatedFilename).to.equal('test-file-name.ts');
        });
        it('transforms passed name and extension to correct file name with extension', function () {
            const generatedFilename = (0, filenames_1.fileNameWithExtension)('TestFileName', 'ts');
            const generatedFilenameTwo = (0, filenames_1.fileNameWithExtension)('TestFileName', 'js');
            (0, expect_1.expect)(generatedFilename).to.equal('test-file-name.ts');
            (0, expect_1.expect)(generatedFilenameTwo).to.equal('test-file-name.js');
        });
    });
});
