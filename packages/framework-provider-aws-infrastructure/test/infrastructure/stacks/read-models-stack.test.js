"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../../expect");
const mocha_1 = require("mocha");
const core_1 = require("@aws-cdk/core");
const framework_types_1 = require("@boostercloud/framework-types");
const read_models_stack_1 = require("../../../src/infrastructure/stacks/read-models-stack");
const aws_dynamodb_1 = require("@aws-cdk/aws-dynamodb");
(0, mocha_1.describe)('ReadModelsStack', () => {
    (0, mocha_1.describe)('the `build` method', () => {
        context('When no sequence key has been defined', () => {
            class SomeReadModel {
                constructor(id) {
                    this.id = id;
                }
            }
            const config = new framework_types_1.BoosterConfig('test');
            config.userProjectRootPath = '.';
            config.readModels['SomeReadModel'] = {
                class: SomeReadModel,
                authorizer: () => Promise.resolve(),
                properties: [],
                before: [],
            };
            const stack = new core_1.Stack(new core_1.App(), 'some-app');
            it('generates a DynamoDB table with the field `id` as the partitionKey and no `sequenceKey`', () => {
                const readModelsStack = new read_models_stack_1.ReadModelsStack(config, stack);
                const tables = readModelsStack.build();
                (0, expect_1.expect)(tables.length).to.be.equal(1);
                (0, expect_1.expect)(tables[0]).to.be.instanceOf(aws_dynamodb_1.Table);
                const someReadModelTable = tables[0];
                (0, expect_1.expect)(someReadModelTable === null || someReadModelTable === void 0 ? void 0 : someReadModelTable.tablePartitionKey['name']).to.equal('id');
                (0, expect_1.expect)(someReadModelTable === null || someReadModelTable === void 0 ? void 0 : someReadModelTable.tablePartitionKey['type']).to.equal('S');
                (0, expect_1.expect)(someReadModelTable.tableSortKey).to.be.undefined;
            });
        });
        context('When a sequence key has been defined', () => {
            class SomeReadModel {
                constructor(id) {
                    this.id = id;
                }
            }
            const config = new framework_types_1.BoosterConfig('test');
            config.userProjectRootPath = '.';
            config.readModels['SomeReadModel'] = {
                class: SomeReadModel,
                authorizer: () => Promise.resolve(),
                properties: [],
                before: [],
            };
            config.readModelSequenceKeys['SomeReadModel'] = 'timestamp';
            const stack = new core_1.Stack(new core_1.App(), 'some-app');
            it('generates a DynamoDB table with the field `id` as the partitionKey and the defined field as the `sequenceKey`', () => {
                var _a, _b, _c, _d;
                const readModelsStack = new read_models_stack_1.ReadModelsStack(config, stack);
                const tables = readModelsStack.build();
                (0, expect_1.expect)(tables.length).to.be.equal(1);
                (0, expect_1.expect)(tables[0]).to.be.instanceOf(aws_dynamodb_1.Table);
                const someReadModelTable = tables[0];
                (0, expect_1.expect)((_a = someReadModelTable === null || someReadModelTable === void 0 ? void 0 : someReadModelTable.tablePartitionKey) === null || _a === void 0 ? void 0 : _a['name']).to.equal('id');
                (0, expect_1.expect)((_b = someReadModelTable === null || someReadModelTable === void 0 ? void 0 : someReadModelTable.tablePartitionKey) === null || _b === void 0 ? void 0 : _b['type']).to.equal('S');
                (0, expect_1.expect)((_c = someReadModelTable === null || someReadModelTable === void 0 ? void 0 : someReadModelTable.tableSortKey) === null || _c === void 0 ? void 0 : _c['name']).to.equal('timestamp');
                (0, expect_1.expect)((_d = someReadModelTable === null || someReadModelTable === void 0 ? void 0 : someReadModelTable.tableSortKey) === null || _d === void 0 ? void 0 : _d['type']).to.equal('S');
            });
        });
    });
});
