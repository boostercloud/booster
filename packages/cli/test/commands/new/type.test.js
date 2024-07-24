"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const ProjectChecker = require("../../../src/services/project-checker");
const sinon_1 = require("sinon");
const type_1 = require("../../../src/commands/new/type");
const Mustache = require("mustache");
const fs = require("fs-extra");
const expect_1 = require("../../expect");
const generator_1 = require("../../../src/services/generator");
describe('new', () => {
    describe('Type', () => {
        const typeName = 'ExampleType';
        const typesRoot = 'src/common/';
        const typePath = `${typesRoot}example-type.ts`;
        const defaultTypeImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'Type',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'Register',
            },
        ];
        const uuidTypeImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'Type',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'Register, UUID',
            },
        ];
        const renderType = (imports, name, fields) => {
            return Mustache.render((0, generator_1.template)('type'), {
                imports: imports,
                name: name,
                fields: fields,
            });
        };
        beforeEach(() => {
            (0, sinon_1.stub)(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis();
            (0, sinon_1.replace)(fs, 'outputFile', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(ProjectChecker, 'checkCurrentDirBoosterVersion', sinon_1.fake.resolves({}));
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new type_1.default([], {}).init();
            (0, expect_1.expect)(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        describe('Created correctly', () => {
            it('with no fields', async () => {
                await new type_1.default([typeName], {}).run();
                const renderedType = renderType(defaultTypeImports, typeName, []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(typePath, renderedType);
            });
            it('creates Type with a string field', async () => {
                await new type_1.default([typeName, '--fields', 'title:string'], {}).run();
                const renderedType = renderType(defaultTypeImports, typeName, [{ name: 'title', type: 'string' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(typePath, renderedType);
            });
            it('creates Type with a number field', async () => {
                await new type_1.default([typeName, '--fields', 'quantity:number'], {}).run();
                const renderedType = renderType(defaultTypeImports, typeName, [{ name: 'quantity', type: 'number' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(typePath, renderedType);
            });
            it('creates Type with UUID field', async () => {
                await new type_1.default([typeName, '--fields', 'identifier:UUID'], {}).run();
                const renderedType = renderType(uuidTypeImports, typeName, [{ name: 'identifier', type: 'UUID' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(typePath, renderedType);
            });
            it('creates Type with multiple fields', async () => {
                await new type_1.default([typeName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'], {}).run();
                const fields = [
                    { name: 'title', type: 'string' },
                    { name: 'quantity', type: 'number' },
                    { name: 'identifier', type: 'UUID' },
                ];
                const renderedType = renderType(uuidTypeImports, typeName, fields);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(typePath, renderedType);
            });
        });
        describe('displays an error', () => {
            it('with empty Type name', async () => {
                (0, sinon_1.replace)(console, 'error', sinon_1.fake.resolves({}));
                await new type_1.default([], {}).run();
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(typesRoot);
                (0, expect_1.expect)(console.error).to.have.been.calledWithMatch(/You haven't provided a type name/);
            });
            it('with empty fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new type_1.default([typeName, '--fields'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.be.equal('Flag --fields expects a value');
            });
            it('with field with no type', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new type_1.default([typeName, '--fields', 'title'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
            });
            it('with no field type after :', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new type_1.default([typeName, '--fields', 'title:'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(typePath);
            });
            it('with repeated fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new type_1.default([typeName, '--fields', 'title:string', 'title:string', 'quantity:number'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(typePath);
            });
        });
    });
});
