"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const ProjectChecker = require("../../../src/services/project-checker");
const sinon_1 = require("sinon");
const command_1 = require("../../../src/commands/new/command");
const Mustache = require("mustache");
const fs = require("fs-extra");
const expect_1 = require("../../expect");
const generator_1 = require("../../../src/services/generator");
describe('new', () => {
    describe('Command', () => {
        const command = 'ExampleCommand';
        const commandsRoot = 'src/commands/';
        const commandPath = `${commandsRoot}example-command.ts`;
        const defaultCommandImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'Command',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'Register',
            },
        ];
        const uuidCommandImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'Command',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'Register, UUID',
            },
        ];
        const renderCommand = (imports, name, fields) => {
            return Mustache.render((0, generator_1.template)('command'), {
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
            await new command_1.default([], {}).init();
            (0, expect_1.expect)(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        describe('Created correctly', () => {
            it('with no fields', async () => {
                await new command_1.default([command], {}).run();
                const renderedCommand = renderCommand(defaultCommandImports, command, []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand);
            });
            it('creates command with a string field', async () => {
                await new command_1.default([command, '--fields', 'title:string'], {}).run();
                const renderedCommand = renderCommand(defaultCommandImports, command, [{ name: 'title', type: 'string' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand);
            });
            it('creates command with a number field', async () => {
                await new command_1.default([command, '--fields', 'quantity:number'], {}).run();
                const renderedCommand = renderCommand(defaultCommandImports, command, [{ name: 'quantity', type: 'number' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand);
            });
            it('creates command with UUID field', async () => {
                await new command_1.default([command, '--fields', 'identifier:UUID'], {}).run();
                const renderedCommand = renderCommand(uuidCommandImports, command, [{ name: 'identifier', type: 'UUID' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand);
            });
            it('creates command with multiple fields', async () => {
                await new command_1.default([command, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'], {}).run();
                const fields = [
                    { name: 'title', type: 'string' },
                    { name: 'quantity', type: 'number' },
                    { name: 'identifier', type: 'UUID' },
                ];
                const renderedCommand = renderCommand(uuidCommandImports, command, fields);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(commandPath, renderedCommand);
            });
        });
        describe('displays an error', () => {
            it('with empty command name', async () => {
                (0, sinon_1.replace)(console, 'error', sinon_1.fake.resolves({}));
                await new command_1.default([], {}).run();
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(commandsRoot);
                (0, expect_1.expect)(console.error).to.have.been.calledWithMatch(/You haven't provided a command name/);
            });
            it('with empty fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new command_1.default([command, '--fields'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('--fields expects a value');
            });
            it('with field with no type', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new command_1.default([command, '--fields', 'title'], {}).run();
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
                    await new command_1.default([command, '--fields', 'title:'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(commandPath);
            });
            it('with repeated fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new command_1.default([command, '--fields', 'title:string', 'title:string', 'quantity:number'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(commandPath);
            });
        });
    });
});
