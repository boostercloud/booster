"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const keys_helper_1 = require("../../src/library/keys-helper");
const faker_1 = require("faker");
describe('"partitionKeyForEvent" function', () => {
    it('returns a correctly formatted partition key', () => {
        const entityName = faker_1.lorem.word();
        const entityID = faker_1.random.uuid();
        const expected = `${entityName}-${entityID}-event`;
        const got = (0, keys_helper_1.partitionKeyForEvent)(entityName, entityID);
        (0, expect_1.expect)(got).to.equal(expected);
    });
});
describe('"partitionKeyForEntitySnapshot" function', () => {
    it('returns a correctly formatted partition key', () => {
        const entityName = faker_1.lorem.word();
        const entityID = faker_1.random.uuid();
        const expected = `${entityName}-${entityID}-snapshot`;
        const got = (0, keys_helper_1.partitionKeyForEntitySnapshot)(entityName, entityID);
        (0, expect_1.expect)(got).to.equal(expected);
    });
});
describe('"sortKeyForSubscription" function', () => {
    it('returns a correctly formatted sortKey key', () => {
        const connectionID = faker_1.random.uuid();
        const subscriptionID = faker_1.random.uuid();
        const expected = `${connectionID}-${subscriptionID}`;
        const got = (0, keys_helper_1.sortKeyForSubscription)(connectionID, subscriptionID);
        (0, expect_1.expect)(got).to.equal(expected);
    });
});
