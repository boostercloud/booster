"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const partition_keys_1 = require("../../src/library/partition-keys");
const expect_1 = require("../expect");
describe('Azure keys helpers', () => {
    describe('partitionKeyForEvent', () => {
        it('should return the correct partition key for an event', () => {
            (0, expect_1.expect)((0, partition_keys_1.partitionKeyForEvent)('User', '123')).to.be.equal('User-123-event');
        });
    });
    describe('partitionKeyForSnapshot', () => {
        it('should return the correct partition key for an entity snapshot', () => {
            (0, expect_1.expect)((0, partition_keys_1.partitionKeyForSnapshot)('User', '123')).to.be.equal('User-123-snapshot');
        });
    });
});
