"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const keys_helper_1 = require("../../src/library/keys-helper");
const expect_1 = require("../expect");
describe('AWS keys helpers', () => {
    describe('partitionKeyForEvent', () => {
        it('should return the correct partition key for an event', () => {
            (0, expect_1.expect)((0, keys_helper_1.partitionKeyForEvent)('User', '123')).to.be.equal('User-123-event');
        });
    });
    describe('partitionKeyForEntitySnapshot', () => {
        it('should return the correct partition key for an entity snapshot', () => {
            (0, expect_1.expect)((0, keys_helper_1.partitionKeyForEntitySnapshot)('User', '123')).to.be.equal('User-123-snapshot');
        });
    });
    describe('partitionKeyForIndexByEntity', () => {
        it('should return the correct partition key for an index by entity for an event', () => {
            (0, expect_1.expect)((0, keys_helper_1.partitionKeyForIndexByEntity)('User', 'event')).to.be.equal('User-event');
        });
        it('should return the correct partition key for an index by entity for a snapshot', () => {
            (0, expect_1.expect)((0, keys_helper_1.partitionKeyForIndexByEntity)('User', 'snapshot')).to.be.equal('User-snapshot');
        });
    });
    describe('sortKeyForSubscription', () => {
        it('should return the correct sort key for a subscription', () => {
            (0, expect_1.expect)((0, keys_helper_1.sortKeyForSubscription)('123', '456')).to.be.equal('123-456');
        });
    });
});
