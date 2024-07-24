"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const ProjectChecker = require("../../../src/services/project-checker");
const sinon_1 = require("sinon");
const scheduled_command_1 = require("../../../src/commands/new/scheduled-command");
const Mustache = require("mustache");
const fs = require("fs-extra");
const expect_1 = require("../../expect");
const generator_1 = require("../../../src/services/generator");
describe('new', () => {
    describe('ScheduledCommand', () => {
        const scheduledCommandName = 'ExampleScheduledCommand';
        const scheduledCommandRoot = 'src/scheduled-commands/';
        const scheduledCommandPath = `${scheduledCommandRoot}example-scheduled-command.ts`;
        const defaultScheduledCommandImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'ScheduledCommand',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'Register',
            },
        ];
        beforeEach(() => {
            (0, sinon_1.stub)(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis();
            (0, sinon_1.replace)(fs, 'outputFile', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(ProjectChecker, 'checkCurrentDirBoosterVersion', sinon_1.fake.resolves({}));
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new scheduled_command_1.default([], {}).init();
            (0, expect_1.expect)(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        describe('Created correctly', () => {
            it('with scheduled command name', async () => {
                await new scheduled_command_1.default([scheduledCommandName], {}).run();
                const renderedCommand = Mustache.render((0, generator_1.template)('scheduled-command'), {
                    imports: defaultScheduledCommandImports,
                    name: scheduledCommandName,
                });
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(scheduledCommandPath, renderedCommand);
            });
        });
        describe('displays an error', () => {
            it('with empty scheduled command name', async () => {
                (0, sinon_1.replace)(console, 'error', sinon_1.fake.resolves({}));
                await new scheduled_command_1.default([], {}).run();
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(scheduledCommandRoot);
                (0, expect_1.expect)(console.error).to.have.been.calledWithMatch(/You haven't provided a scheduled command name/);
            });
            it('with two scheduled command names', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new scheduled_command_1.default([scheduledCommandName, 'AnotherName'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Unexpected argument: AnotherName');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(scheduledCommandPath);
            });
        });
    });
});
