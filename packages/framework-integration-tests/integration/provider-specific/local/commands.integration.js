"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const utils_1 = require("./utils");
const faker_1 = require("faker");
const chai_1 = require("chai");
const nedb_1 = require("@seald-io/nedb");
const constants_1 = require("./constants");
const util = require("util");
const path = require("path");
const sleep_1 = require("../../helper/sleep");
describe('commands', () => {
    let events;
    let client;
    before(async () => {
        events = new nedb_1.default(path.join(constants_1.sandboxPath, '.booster', 'events.json'));
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
        xit('should successfully process a command', async () => {
            const mutationResult = await (0, utils_1.changeCartItem)(client, mockCartId, mockProductId, mockQuantity);
            (0, chai_1.expect)(mutationResult).not.to.be.null;
            (0, chai_1.expect)(mutationResult.data.ChangeCartItem).to.be.true;
        });
        xit('should store event in the database', async () => {
            await (0, utils_1.changeCartItem)(client, mockCartId, mockProductId, mockQuantity);
            // Wait until event is stored in database
            await (0, sleep_1.waitForIt)(async () => events.loadDatabase(), () => events.getAllData().some((value) => value.entityID === mockCartId));
            // Verify the event content
            const result = await new Promise((resolve, reject) => events.findOne({ entityID: mockCartId }, (err, docs) => {
                err ? reject(err) : resolve(docs);
            }));
            const expectedResult = {
                version: 1,
                kind: 'event',
                superKind: 'domain',
                entityID: mockCartId,
                currentUser: {
                    username: 'test@test.com',
                    role: '',
                },
                entityTypeName: 'Cart',
                typeName: 'CartItemChanged',
                value: {
                    cartId: mockCartId,
                    productId: mockProductId,
                    quantity: mockQuantity,
                },
            };
            (0, chai_1.expect)(result).to.deep.include(expectedResult);
        });
        xit('should create a snapshot after 5 events', async () => {
            let mockQuantity;
            let expectedSnapshotQuantity = 0;
            for (let i = 0; i < 5; i++) {
                mockQuantity = faker_1.random.number();
                expectedSnapshotQuantity += mockQuantity;
                await (0, utils_1.changeCartItem)(client, mockCartId, mockProductId, mockQuantity);
            }
            // Sixth event - Quantity shouldn't be added to snapshot
            mockQuantity = faker_1.random.number();
            await (0, utils_1.changeCartItem)(client, mockCartId, mockProductId, mockQuantity);
            await (0, sleep_1.waitForIt)(async () => events.loadDatabase(), () => events
                .getAllData()
                .some((record) => {
                var _a, _b, _c, _d;
                return record.entityID === mockCartId &&
                    record.kind === 'snapshot' &&
                    ((_b = (_a = record.value) === null || _a === void 0 ? void 0 : _a.cartItems[0]) === null || _b === void 0 ? void 0 : _b.productId) === mockProductId &&
                    ((_d = (_c = record.value) === null || _c === void 0 ? void 0 : _c.cartItems[0]) === null || _d === void 0 ? void 0 : _d.quantity) === expectedSnapshotQuantity;
            }));
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const countPromise = util.promisify((query, callback) => events.count(query, callback));
            (0, chai_1.expect)(await countPromise({ kind: 'snapshot', entityID: mockCartId, entityTypeName: 'Cart' })).to.be.gte(1);
        });
    });
});
