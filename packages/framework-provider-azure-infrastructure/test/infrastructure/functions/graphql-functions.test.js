"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const framework_types_1 = require("@boostercloud/framework-types");
const mocha_1 = require("mocha");
const graphql_function_1 = require("../../../src/infrastructure/functions/graphql-function");
const expect_1 = require("../../expect");
(0, mocha_1.describe)('Creating graphql-functions', () => {
    const config = new framework_types_1.BoosterConfig('test');
    config.resourceNames.applicationStack = 'applicationStack';
    config.resourceNames.eventsStore = 'eventsStore';
    it('create the expected GraphQLFunctionDefiniton', () => {
        var _a, _b, _c;
        const definition = new graphql_function_1.GraphqlFunction(config).getFunctionDefinition();
        (0, expect_1.expect)(definition).not.to.be.null;
        (0, expect_1.expect)(definition.name).to.be.equal('graphql');
        (0, expect_1.expect)(definition.config.bindings[0].type).to.be.equal('httpTrigger');
        (0, expect_1.expect)(definition.config.bindings[0].name).to.be.equal('rawRequest');
        (0, expect_1.expect)(definition.config.bindings[0].direction).to.be.equal('in');
        (0, expect_1.expect)(definition.config.bindings[0].authLevel).to.be.equal('anonymous');
        (0, expect_1.expect)((_b = (_a = definition.config.bindings[0]) === null || _a === void 0 ? void 0 : _a.methods) === null || _b === void 0 ? void 0 : _b.length).to.be.equal(1);
        (0, expect_1.expect)((_c = definition.config.bindings[0].methods) === null || _c === void 0 ? void 0 : _c.pop()).to.be.equal('post');
        (0, expect_1.expect)(definition.config.bindings[1].type).to.be.equal('http');
        (0, expect_1.expect)(definition.config.bindings[1].name).to.be.equal('$return');
        (0, expect_1.expect)(definition.config.bindings[1].direction).to.be.equal('out');
        (0, expect_1.expect)(definition.config.scriptFile).to.be.equal('../dist/index.js');
        (0, expect_1.expect)(definition.config.entryPoint).to.be.equal('boosterServeGraphQL');
    });
});
