"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const framework_types_1 = require("@boostercloud/framework-types");
const expect_1 = require("../../expect");
const mocha_1 = require("mocha");
const scheduled_functions_1 = require("../../../src/infrastructure/functions/scheduled-functions");
(0, mocha_1.describe)('Creating scheduled-functions', () => {
    (0, mocha_1.describe)('without scheduledCommandHandlers', () => {
        const config = buildConfig();
        it('is an undefined object', async () => {
            const definitions = new scheduled_functions_1.ScheduledFunctions(config).getFunctionDefinitions();
            (0, expect_1.expect)(definitions).to.be.undefined;
        });
    });
    (0, mocha_1.describe)('with one scheduledCommandHandlers', () => {
        const config = buildConfig();
        const scheduleCommandInterface = {};
        const scheduleInterface = { day: '1' };
        const scheduleCommandName = 'test';
        config.scheduledCommandHandlers[scheduleCommandName] = {
            class: scheduleCommandInterface,
            scheduledOn: scheduleInterface,
        };
        it('is one definition with the proper fields', () => {
            const definitions = new scheduled_functions_1.ScheduledFunctions(config).getFunctionDefinitions();
            (0, expect_1.expect)(definitions).not.to.be.null;
            (0, expect_1.expect)(definitions.length).to.be.equal(1);
            expectDefinition(definitions[0], scheduleCommandName, '0 * * 1 * *');
        });
    });
    (0, mocha_1.describe)('with two scheduledCommandHandlers', () => {
        const config = buildConfig();
        const scheduleCommandInterface = {};
        const scheduleInterface = { day: '1' };
        const scheduleCommandName1 = 'test-1';
        config.scheduledCommandHandlers[scheduleCommandName1] = {
            class: scheduleCommandInterface,
            scheduledOn: scheduleInterface,
        };
        const scheduleCommandName2 = 'test-2';
        config.scheduledCommandHandlers[scheduleCommandName2] = {
            class: scheduleCommandInterface,
            scheduledOn: scheduleInterface,
        };
        it('is two definitions with the proper fields', () => {
            const definitions = new scheduled_functions_1.ScheduledFunctions(config).getFunctionDefinitions();
            (0, expect_1.expect)(definitions).not.to.be.null;
            (0, expect_1.expect)(definitions.length).to.be.equal(2);
            expectDefinition(definitions[0], scheduleCommandName1, '0 * * 1 * *');
            expectDefinition(definitions[1], scheduleCommandName2, '0 * * 1 * *');
        });
    });
    (0, mocha_1.describe)('with all scheduled fields', () => {
        const config = buildConfig();
        const scheduleCommandInterface = {};
        const scheduleInterface = {
            day: 'day',
            hour: 'hour',
            weekDay: 'weekDay',
            minute: 'minute',
            month: 'month',
            year: 'year',
        };
        const scheduleCommandName = 'test';
        config.scheduledCommandHandlers[scheduleCommandName] = {
            class: scheduleCommandInterface,
            scheduledOn: scheduleInterface,
        };
        it('create the expected nCronTab', () => {
            const definitions = new scheduled_functions_1.ScheduledFunctions(config).getFunctionDefinitions();
            expectDefinition(definitions[0], scheduleCommandName, '0 minute hour day month weekDay');
        });
    });
    (0, mocha_1.describe)('without scheduled fields', () => {
        const config = buildConfig();
        const scheduleCommandInterface = {};
        const scheduleInterface = {};
        const scheduleCommandName = 'test';
        config.scheduledCommandHandlers[scheduleCommandName] = {
            class: scheduleCommandInterface,
            scheduledOn: scheduleInterface,
        };
        it('skip the function', () => {
            const definitions = new scheduled_functions_1.ScheduledFunctions(config).getFunctionDefinitions();
            (0, expect_1.expect)(definitions.length).to.be.equal(0);
        });
    });
    function expectDefinition(definition, scheduleCommandName, nCronTabExpression = '0 * * * * *') {
        (0, expect_1.expect)(definition.name).to.be.equal(`scheduleFunction-${scheduleCommandName}`);
        (0, expect_1.expect)(definition.config.bindings.length).to.be.equal(1);
        (0, expect_1.expect)(definition.config.bindings[0].type).to.be.equal('timerTrigger');
        (0, expect_1.expect)(definition.config.bindings[0].name).to.be.equal(scheduleCommandName);
        (0, expect_1.expect)(definition.config.bindings[0].direction).to.be.equal('in');
        (0, expect_1.expect)(definition.config.bindings[0].schedule).to.be.equal(nCronTabExpression);
        (0, expect_1.expect)(definition.config.scriptFile).to.be.equal('../dist/index.js');
        (0, expect_1.expect)(definition.config.entryPoint).to.be.equal('boosterTriggerScheduledCommand');
    }
    function buildConfig() {
        return new framework_types_1.BoosterConfig('test');
    }
});
