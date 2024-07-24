"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const faker_1 = require("faker");
const sinon_1 = require("sinon");
const connections_adapter_1 = require("../../src/library/connections-adapter");
const dynamodb_1 = require("aws-sdk/clients/dynamodb");
const framework_types_1 = require("@boostercloud/framework-types");
const config = new framework_types_1.BoosterConfig('test');
describe('The "storeConnectionData" method', () => {
    it('stores connection data correctly', async () => {
        const fakeDB = new dynamodb_1.DocumentClient();
        const fakePut = (0, sinon_1.stub)().returns({
            promise: (0, sinon_1.stub)(),
        });
        (0, sinon_1.replace)(fakeDB, 'put', fakePut);
        const connectionID = faker_1.random.uuid();
        const expectedData = {
            expirationTime: faker_1.random.number(),
            user: {
                roles: [faker_1.lorem.word()],
                username: faker_1.internet.email(),
                claims: {},
            },
        };
        await (0, connections_adapter_1.storeConnectionData)(fakeDB, config, connectionID, expectedData);
        (0, expect_1.expect)(fakePut).to.be.calledWith({
            TableName: config.resourceNames.connectionsStore,
            Item: {
                ...expectedData,
                connectionID,
            },
        });
    });
});
describe('The "fetchConnectionData" method', () => {
    it('returns the expected data', async () => {
        const fakeDB = new dynamodb_1.DocumentClient();
        const expectedData = {
            expirationTime: faker_1.random.number(),
            user: {
                roles: [faker_1.lorem.word()],
                username: faker_1.internet.email(),
                claims: {},
            },
        };
        const fakeGet = (0, sinon_1.stub)().returns({
            promise: (0, sinon_1.stub)().returns({ Item: expectedData }),
        });
        (0, sinon_1.replace)(fakeDB, 'get', fakeGet);
        const connectionID = faker_1.random.uuid();
        const gotData = await (0, connections_adapter_1.fetchConnectionData)(fakeDB, config, connectionID);
        (0, expect_1.expect)(fakeGet).to.be.calledWith({
            TableName: config.resourceNames.connectionsStore,
            Key: { connectionID },
            ConsistentRead: true,
        });
        (0, expect_1.expect)(gotData).to.be.deep.equal(expectedData);
    });
});
describe('The "deleteConnectionData" method', () => {
    it('deletes connection data correctly', async () => {
        const fakeDB = new dynamodb_1.DocumentClient();
        const fakeDelete = (0, sinon_1.stub)().returns({
            promise: (0, sinon_1.stub)(),
        });
        (0, sinon_1.replace)(fakeDB, 'delete', fakeDelete);
        const connectionID = faker_1.random.uuid();
        await (0, connections_adapter_1.deleteConnectionData)(fakeDB, config, connectionID);
        (0, expect_1.expect)(fakeDelete).to.be.calledWith({
            TableName: config.resourceNames.connectionsStore,
            Key: { connectionID },
        });
    });
});
describe('The "sendMessageToConnection" method', () => {
    it('sends the proper data to the connectionID', () => {
        // TODO: I didn't find a way to properly mock ApiGatewayManagementApi and check it is being called correctly
    });
});
