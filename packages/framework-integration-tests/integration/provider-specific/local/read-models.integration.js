"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const utils_1 = require("./utils");
const faker_1 = require("faker");
const chai_1 = require("chai");
const nedb_1 = require("@seald-io/nedb");
const constants_1 = require("./constants");
const path = require("path");
const sleep_1 = require("../../helper/sleep");
describe('read-models', () => {
    let readModels;
    let client;
    before(async () => {
        readModels = new nedb_1.default(path.join(constants_1.sandboxPath, '.booster', 'read_models.json'));
        client = await (0, utils_1.graphQLClient)();
    });
    context('valid command', () => {
        let mockCartId;
        let mockProductId;
        let mockQuantity;
        beforeEach(() => {
            mockCartId = faker_1.random.uuid();
            mockProductId = faker_1.random.uuid();
            mockQuantity = faker_1.random.number({ min: 1 });
        });
        xit('should store read-models in the database', async () => {
            await (0, utils_1.changeCartItem)(client, mockCartId, mockProductId, mockQuantity);
            // Wait until event is stored in database
            await (0, sleep_1.waitForIt)(async () => readModels.loadDatabase(), () => readModels.getAllData().some((readModel) => readModel.value.id === mockCartId));
            // Verify the event content
            const result = await new Promise((resolve, reject) => readModels.findOne({ 'value.id': mockCartId }, (err, docs) => {
                err ? reject(err) : resolve(docs);
            }));
            const expectedResult = {
                typeName: 'CartReadModel',
                value: {
                    id: mockCartId,
                    cartItems: [{ productId: mockProductId, quantity: mockQuantity }],
                    checks: 0,
                },
            };
            (0, chai_1.expect)(result).to.deep.include(expectedResult);
        });
    });
});
