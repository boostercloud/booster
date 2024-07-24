"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const read_models_searcher_adapter_1 = require("../../src/library/read-models-searcher-adapter");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const faker_1 = require("faker");
const aws_sdk_1 = require("aws-sdk");
describe('Read models searcher adapter', () => {
    describe('The "searchReadModel" method', () => {
        const config = new framework_types_1.BoosterConfig('test');
        const readModelName = faker_1.random.word();
        let database;
        const expectedParams = {
            TableName: config.resourceNames.forReadModel(readModelName),
            ConsistentRead: true,
            Limit: undefined,
            ExclusiveStartKey: undefined,
        };
        class Money {
            constructor(cents, currency) {
                this.cents = cents;
                this.currency = currency;
            }
        }
        class Item {
            constructor(sku, price) {
                this.sku = sku;
                this.price = price;
            }
        }
        class Product {
            constructor(id, stock, mainItem, items, buyers, days, pairs) {
                this.id = id;
                this.stock = stock;
                this.mainItem = mainItem;
                this.items = items;
                this.buyers = buyers;
                this.days = days;
                this.pairs = pairs;
            }
        }
        beforeEach(() => {
            database = (0, sinon_1.createStubInstance)(aws_sdk_1.DynamoDB.DocumentClient, {
                scan: {
                    promise: (0, sinon_1.stub)().returns({
                        result: (0, sinon_1.stub)().returns({}),
                    }),
                },
            });
        });
        after(() => {
            (0, sinon_1.restore)();
        });
        it('Executes query without filters', async () => {
            const result = await (0, read_models_searcher_adapter_1.searchReadModel)(database, config, readModelName, {});
            (0, expect_1.expect)(database.scan).to.have.been.calledWithExactly(expectedParams);
            (0, expect_1.expect)(result).to.be.deep.equal([]);
        });
        it('Executes query simple filters', async () => {
            const expectedInput = {
                ...expectedParams,
                FilterExpression: [
                    'contains(#id, :id_0)',
                    'AND #id IN (:id_1_0,:id_1_1,:id_1_2)',
                    'AND #stock > :stock_0',
                    'AND #stock <= :stock_1',
                ].join(' '),
                ExpressionAttributeNames: { '#id': 'id', '#stock': 'stock' },
                ExpressionAttributeValues: {
                    ':id_0': '3',
                    ':id_1_0': 'test1',
                    ':id_1_1': 'test2',
                    ':id_1_2': 'test3',
                    ':stock_0': 0,
                    ':stock_1': 10,
                },
            };
            const filters = {
                id: { contains: '3', in: ['test1', 'test2', 'test3'] },
                stock: { gt: 0, lte: 10 },
            };
            await (0, read_models_searcher_adapter_1.searchReadModel)(database, config, readModelName, filters);
            (0, expect_1.expect)(database.scan).to.have.been.calledWithExactly(expectedInput);
        });
        it('Executes query using NOT in filters', async () => {
            const expectedInput = {
                ...expectedParams,
                FilterExpression: 'contains(#id, :id_0) AND NOT (#id = :id_1)',
                ExpressionAttributeNames: { '#id': 'id' },
                ExpressionAttributeValues: {
                    ':id_0': '3',
                    ':id_1': '333',
                },
            };
            const filters = {
                id: { contains: '3' },
                not: { id: { eq: '333' } },
            };
            await (0, read_models_searcher_adapter_1.searchReadModel)(database, config, readModelName, filters);
            (0, expect_1.expect)(database.scan).to.have.been.calledWithExactly(expectedInput);
        });
        it('Executes query using AND & OR filters', async () => {
            const expectedInput = {
                ...expectedParams,
                FilterExpression: [
                    '#id <> :id_0',
                    'AND (begins_with(#id, :id_1) or begins_with(#id, :id_2))',
                    'AND (contains(#id, :id_3) and contains(#id, :id_4))',
                ].join(' '),
                ExpressionAttributeNames: { '#id': 'id' },
                ExpressionAttributeValues: {
                    ':id_0': 'test',
                    ':id_1': '1',
                    ':id_2': '2',
                    ':id_3': '3',
                    ':id_4': '4',
                },
            };
            const filters = {
                id: { ne: 'test' },
                or: [{ id: { beginsWith: '1' } }, { id: { beginsWith: '2' } }],
                and: [{ id: { contains: '3' } }, { id: { contains: '4' } }],
            };
            await (0, read_models_searcher_adapter_1.searchReadModel)(database, config, readModelName, filters);
            (0, expect_1.expect)(database.scan).to.have.been.calledWithExactly(expectedInput);
        });
        it('Executes query using nested filters', async () => {
            const expectedInput = {
                ...expectedParams,
                FilterExpression: '#mainItem.#sku = :sku_0 AND #mainItem.#price.#cents >= :cents_0 AND #mainItem.#price.#cents < :cents_1',
                ExpressionAttributeNames: {
                    '#mainItem': 'mainItem',
                    '#sku': 'sku',
                    '#price': 'price',
                    '#cents': 'cents',
                },
                ExpressionAttributeValues: { ':sku_0': 'test', ':cents_0': 1000, ':cents_1': 100000 },
            };
            const filters = {
                mainItem: {
                    sku: { eq: 'test' },
                    price: {
                        cents: { gte: 1000, lt: 100000 },
                    },
                },
            };
            await (0, read_models_searcher_adapter_1.searchReadModel)(database, config, readModelName, filters);
            (0, expect_1.expect)(database.scan).to.have.been.calledWithExactly(expectedInput);
        });
        it('Executes query using array includes filters', async () => {
            const expectedInput = {
                ...expectedParams,
                FilterExpression: 'contains(#days, :days_0) AND contains(#items, :items_0)',
                ExpressionAttributeNames: {
                    '#days': 'days',
                    '#items': 'items',
                },
                ExpressionAttributeValues: {
                    ':days_0': 2,
                    ':items_0': { sku: 'test', price: { cents: 1000, currency: 'EUR' } },
                },
            };
            const filters = {
                days: { includes: 2 },
                items: { includes: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
            };
            await (0, read_models_searcher_adapter_1.searchReadModel)(database, config, readModelName, filters);
            (0, expect_1.expect)(database.scan).to.have.been.calledWithExactly(expectedInput);
        });
        it('Executes query using isDefined filters', async () => {
            const expectedInput = {
                ...expectedParams,
                FilterExpression: '(attribute_exists(#days) and attribute_not_exists(#mainItem) and attribute_exists(#mainItem.#sku) and attribute_exists(#mainItem.#price))',
                ExpressionAttributeNames: {
                    '#days': 'days',
                    '#mainItem': 'mainItem',
                    '#sku': 'sku',
                    '#price': 'price',
                },
            };
            const filters = {
                and: [
                    { days: { isDefined: true } },
                    { mainItem: { isDefined: false } },
                    { mainItem: { sku: { isDefined: true } } },
                    { mainItem: { price: { isDefined: true } } },
                ],
            };
            await (0, read_models_searcher_adapter_1.searchReadModel)(database, config, readModelName, filters);
            (0, expect_1.expect)(database.scan).to.have.been.calledWithExactly(expectedInput);
        });
        it('Throws an error with non supported filters', async () => {
            const unknownOperator = 'existsIn';
            const filters = {
                id: { [unknownOperator]: 'test' },
            };
            await (0, expect_1.expect)((0, read_models_searcher_adapter_1.searchReadModel)(database, config, readModelName, filters)).to.be.eventually.rejectedWith(`Operator "${unknownOperator}" is not supported`);
        });
        it('Executes query using isDefined filters with complex filters', async () => {
            const expectedInput = {
                ...expectedParams,
                FilterExpression: '(#id = :id_0 and #mainItem.#sku = :sku_0 and (attribute_exists(#days) or contains(#items, :items_0)) and #mainItem.#sku = :sku_1 and #mainItem.#price.#cents <> :cents_0)',
                ExpressionAttributeNames: {
                    '#id': 'id',
                    '#mainItem': 'mainItem',
                    '#sku': 'sku',
                    '#days': 'days',
                    '#items': 'items',
                    '#price': 'price',
                    '#cents': 'cents',
                },
                ExpressionAttributeValues: {
                    ':id_0': '3',
                    ':sku_0': 'test',
                    ':items_0': { sku: 'test', price: { cents: 1000, currency: 'EUR' } },
                    ':sku_1': null,
                    ':cents_0': null,
                },
            };
            const filters = {
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
            };
            await (0, read_models_searcher_adapter_1.searchReadModel)(database, config, readModelName, filters);
            (0, expect_1.expect)(database.scan).to.have.been.calledWithExactly(expectedInput);
        });
        it('Throws an error with non supported filters', async () => {
            const unknownOperator = 'existsIn';
            const filters = {
                id: { [unknownOperator]: 'test' },
            };
            await (0, expect_1.expect)((0, read_models_searcher_adapter_1.searchReadModel)(database, config, readModelName, filters)).to.be.eventually.rejectedWith(`Operator "${unknownOperator}" is not supported`);
        });
    });
});
