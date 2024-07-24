"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const expect_1 = require("../../helper/expect");
const setup_1 = require("./setup");
require("mocha");
const sleep_1 = require("../../helper/sleep");
describe('Concurrency end-to-end tests', () => {
    let client;
    before(async () => {
        client = setup_1.applicationUnderTest.graphql.client();
    });
    context('ReadModels', () => {
        describe('With one projection', () => {
            it('insert and update generate one ReadModel with version 2', async () => {
                var _a, _b;
                const entityId = faker_1.random.uuid();
                const insertedReadModel = await addConcurrency(client, entityId, 1, 'ConcurrencyReadModel');
                (0, expect_1.expect)(insertedReadModel.id).to.be.eq(entityId);
                (0, expect_1.expect)((_a = insertedReadModel.boosterMetadata) === null || _a === void 0 ? void 0 : _a.version).to.be.eq(1);
                const updatedReadModel = await addConcurrency(client, entityId, 2, 'ConcurrencyReadModel');
                (0, expect_1.expect)(updatedReadModel.id).to.be.eq(entityId);
                (0, expect_1.expect)((_b = updatedReadModel.boosterMetadata) === null || _b === void 0 ? void 0 : _b.version).to.be.eq(2);
            });
        });
        describe('With two projections for the same ReadModel', () => {
            if (process.env.TESTED_PROVIDER === 'AWS') {
                console.log('AWS Provider is not working properly when inserting a ReadModel with two projections'); // TODO: Fix AWS Provider
                return;
            }
            it('insert and update generate one ReadModel with version 4', async () => {
                var _a, _b;
                const entityId = faker_1.random.uuid();
                const insertedReadModel = await addConcurrency(client, entityId, 2, 'OtherConcurrencyReadModel');
                (0, expect_1.expect)(insertedReadModel.id).to.be.eq(entityId);
                (0, expect_1.expect)(insertedReadModel.otherId).to.be.eq(entityId);
                (0, expect_1.expect)((_a = insertedReadModel.boosterMetadata) === null || _a === void 0 ? void 0 : _a.version).to.be.eq(2);
                const updatedReadModel = await addConcurrency(client, entityId, 4, 'OtherConcurrencyReadModel');
                (0, expect_1.expect)(updatedReadModel.id).to.be.eq(entityId);
                (0, expect_1.expect)(updatedReadModel.otherId).to.be.eq(entityId);
                (0, expect_1.expect)((_b = updatedReadModel.boosterMetadata) === null || _b === void 0 ? void 0 : _b.version).to.be.eq(4);
            });
        });
    });
});
async function addConcurrency(client, entityId, expectedVersion, readModelName) {
    var _a;
    await client.mutate({
        variables: {
            id: entityId,
            otherId: entityId,
        },
        mutation: (0, client_1.gql) `
      mutation AddConcurrency($id: ID!, $otherId: ID!) {
        AddConcurrency(input: { id: $id, otherId: $otherId })
      }
    `,
    });
    const mutateResult = await (0, sleep_1.waitForIt)(() => {
        return client.mutate({
            variables: {
                id: entityId,
                readModelName: readModelName,
            },
            mutation: (0, client_1.gql) `
          mutation GetConcurrency($id: ID!, $readModelName: String!) {
            GetConcurrency(input: { id: $id, readModelName: $readModelName })
          }
        `,
        });
    }, (result) => {
        var _a, _b, _c, _d;
        return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.GetConcurrency) &&
            ((_b = result === null || result === void 0 ? void 0 : result.data) === null || _b === void 0 ? void 0 : _b.GetConcurrency.length) > 0 &&
            ((_c = result === null || result === void 0 ? void 0 : result.data) === null || _c === void 0 ? void 0 : _c.GetConcurrency[0]) &&
            ((_d = result === null || result === void 0 ? void 0 : result.data) === null || _d === void 0 ? void 0 : _d.GetConcurrency).find((value) => { var _a; return ((_a = value.boosterMetadata) === null || _a === void 0 ? void 0 : _a.version) === expectedVersion; }) !== undefined;
    });
    const concurrency = ((_a = mutateResult === null || mutateResult === void 0 ? void 0 : mutateResult.data) === null || _a === void 0 ? void 0 : _a.GetConcurrency).find((value) => { var _a; return ((_a = value.boosterMetadata) === null || _a === void 0 ? void 0 : _a.version) === expectedVersion; });
    (0, expect_1.expect)(concurrency.id).to.be.eq(entityId);
    return concurrency;
}
