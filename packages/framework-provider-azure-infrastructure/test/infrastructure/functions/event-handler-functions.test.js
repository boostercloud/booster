"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const framework_types_1 = require("@boostercloud/framework-types");
const mocha_1 = require("mocha");
const event_handler_function_1 = require("../../../src/infrastructure/functions/event-handler-function");
const expect_1 = require("../../expect");
(0, mocha_1.describe)('Creating event-handler-functions', () => {
    const config = new framework_types_1.BoosterConfig('test');
    config.resourceNames.applicationStack = 'applicationStack';
    config.resourceNames.eventsStore = 'eventsStore';
    it('create the expected EventHandlerFunctionDefinition', () => {
        const definition = new event_handler_function_1.EventHandlerFunction(config).getFunctionDefinition();
        (0, expect_1.expect)(definition).not.to.be.null;
        (0, expect_1.expect)(definition.name).to.be.equal('eventHandler');
        (0, expect_1.expect)(definition.config.bindings[0].type).to.be.equal('cosmosDBTrigger');
        (0, expect_1.expect)(definition.config.bindings[0].name).to.be.equal('rawEvent');
        (0, expect_1.expect)(definition.config.bindings[0].direction).to.be.equal('in');
        (0, expect_1.expect)(definition.config.bindings[0].leaseContainerName).to.be.equal('leases');
        (0, expect_1.expect)(definition.config.bindings[0].connection).to.be.equal('COSMOSDB_CONNECTION_STRING');
        (0, expect_1.expect)(definition.config.bindings[0].databaseName).to.be.equal('new-booster-app-app');
        (0, expect_1.expect)(definition.config.bindings[0].containerName).to.be.equal('new-booster-app-app-events-store');
        (0, expect_1.expect)(definition.config.bindings[0].createLeaseContainerIfNotExists).to.be.equal('true');
        (0, expect_1.expect)(definition.config.scriptFile).to.be.equal('../dist/index.js');
        (0, expect_1.expect)(definition.config.entryPoint).to.be.equal('boosterEventDispatcher');
    });
});
