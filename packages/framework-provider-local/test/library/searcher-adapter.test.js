"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
const searcher_adapter_1 = require("../../src/library/searcher-adapter");
describe('searcher-adapter', () => {
    describe('converts simple operators', () => {
        it('converts the "eq" operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { eq: 'one' } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field': 'one' });
        });
        it('converts the "ne" operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { ne: 'one' } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field': { $ne: 'one' } });
        });
        it('converts the "lt" operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { lt: 1 } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field': { $lt: 1 } });
        });
        it('converts the "gt" operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { gt: 1 } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field': { $gt: 1 } });
        });
        it('converts the "lte" operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { lte: 1 } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field': { $lte: 1 } });
        });
        it('converts the "gte" operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { gte: 1 } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field': { $gte: 1 } });
        });
        it('converts the "in" operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { in: ['one', 'two'] } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field': { $in: ['one', 'two'] } });
        });
    });
    describe('converts operators that rely on regexes', () => {
        it('converts the "contains" operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { contains: 'one' } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field': { $regex: new RegExp('one') } });
        });
        it('converts the "includes" operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { includes: 'one' } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field': { $regex: new RegExp('one') } });
        });
        it('converts the "includes" operator with objects', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ parentField: { includes: { children1: 'abc', children2: 2 } } });
            (0, expect_1.expect)(result).to.deep.equal({
                'value.parentField': { $elemMatch: { children1: 'abc', children2: 2 } },
            });
        });
        it('converts nested operator with objects', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ parentField: { children1: { eq: 'one' } } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.parentField.children1': 'one' });
        });
        it('converts the "beginsWith" operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { beginsWith: 'one' } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field': { $regex: new RegExp('^one') } });
        });
        it('Use AND as the default WHERE operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { contains: 'one' }, field2: { contains: 'two' } });
            (0, expect_1.expect)(result).to.deep.equal({
                'value.field': { $regex: new RegExp('one') },
                'value.field2': { $regex: new RegExp('two') },
            });
        });
        it('converts the "isDefined" operator', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { isDefined: true } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field': { $exists: true } });
        });
        it('converts the "isDefined" operator on nested fields', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({ field: { otherField: { isDefined: true } } });
            (0, expect_1.expect)(result).to.deep.equal({ 'value.field.otherField': { $exists: true } });
        });
        it('converts the "isDefined" operator for complex filters', () => {
            const result = (0, searcher_adapter_1.queryRecordFor)({
                and: [
                    {
                        id: { eq: '3' },
                    },
                    {
                        mainItem: {
                            sku: {
                                eq: 'test',
                            },
                        },
                    },
                    {
                        or: [
                            {
                                days: { isDefined: true },
                            },
                            {
                                items: { includes: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
                            },
                        ],
                    },
                    { mainItem: { sku: { eq: null } } },
                    { mainItem: { price: { cents: { ne: null } } } },
                ],
            });
            (0, expect_1.expect)(result).to.deep.equal({
                $and: [
                    { 'value.id': '3' },
                    { 'value.mainItem.sku': 'test' },
                    {
                        $or: [
                            { 'value.days': { $exists: true } },
                            {
                                'value.items': { $elemMatch: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
                            },
                        ],
                    },
                    { 'value.mainItem.sku': null },
                    { 'value.mainItem.price.cents': { $ne: null } },
                ],
            });
        });
    });
});
