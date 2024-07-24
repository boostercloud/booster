"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const query_helper_1 = require("../../src/helpers/query-helper");
const sinon_1 = require("sinon");
const cosmos_1 = require("@azure/cosmos");
const framework_types_1 = require("@boostercloud/framework-types");
const faker_1 = require("faker");
describe('Query helper', () => {
    describe('The "search" method', () => {
        let mockConfig;
        let mockReadModelName;
        let mockCosmosDbClient;
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
            mockConfig = new framework_types_1.BoosterConfig('test');
            mockCosmosDbClient = (0, sinon_1.createStubInstance)(cosmos_1.CosmosClient, {
                database: (0, sinon_1.stub)().returns({
                    container: (0, sinon_1.stub)().returns({
                        items: {
                            query: (0, sinon_1.stub)().returns({
                                fetchAll: sinon_1.fake.resolves({ resources: [] }),
                            }),
                        },
                    }),
                }),
            });
            mockReadModelName = faker_1.random.word();
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('Executes a SQL query without filters in the read model table', async () => {
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, {});
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(`${mockReadModelName}`);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c ',
                parameters: [],
            }));
        });
        it('Executes a SQL query with a string projection in the read model table', async () => {
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, {}, undefined, undefined, false, undefined, 'DISTINCT field');
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(`${mockReadModelName}`);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT DISTINCT field FROM c ',
                parameters: [],
            }));
        });
        it('Executes a SQL query with a projectionFor projection in the read model table', async () => {
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, {}, undefined, undefined, false, undefined, ['id', 'other', 'first.second.third']);
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(`${mockReadModelName}`);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT c.id, c.other, c.first.second.third AS "first.second.third" FROM c ',
                parameters: [],
            }));
        });
        it('Executes a SQL query with a projectionFor projection that has array fields and nested objects in the read model table', async () => {
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, {}, undefined, undefined, false, undefined, [
                'id',
                'other',
                'arrayProp[].prop1',
                'arrayProp[].prop2',
                'a.b.c1',
                'a.b.c2',
                'arr[].x.y',
                'arr[].x.z',
            ]);
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(`${mockReadModelName}`);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT c.id, c.other, ARRAY(SELECT item.prop1, item.prop2 FROM item IN c.arrayProp) AS arrayProp, c.a.b.c1 AS "a.b.c1", c.a.b.c2 AS "a.b.c2", ARRAY(SELECT item.x.y, item.x.z FROM item IN c.arr) AS arr FROM c ',
                parameters: [],
            }));
        });
        it('Executes a SQL query with a star projection in the read model table', async () => {
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, {}, undefined, undefined, false, undefined, undefined);
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(`${mockReadModelName}`);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c ',
                parameters: [],
            }));
        });
        it('Executes a SQL query with filters in the read model table', async () => {
            const filters = {
                id: { eq: '3', in: ['test1', 'test2', 'test3'] },
                stock: { gt: 0, lte: 10 },
            };
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters);
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(`${mockReadModelName}`);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c WHERE ' +
                    'c["id"] = @id_0 AND c["id"] IN (@id_1_0,@id_1_1,@id_1_2) AND ' +
                    'c["stock"] > @stock_0 AND c["stock"] <= @stock_1',
                parameters: [
                    {
                        name: '@id_0',
                        value: '3',
                    },
                    {
                        name: '@id_1_0',
                        value: 'test1',
                    },
                    {
                        name: '@id_1_1',
                        value: 'test2',
                    },
                    {
                        name: '@id_1_2',
                        value: 'test3',
                    },
                    {
                        name: '@stock_0',
                        value: 0,
                    },
                    {
                        name: '@stock_1',
                        value: 10,
                    },
                ],
            }));
        });
        it('Supports NOT filter combinator', async () => {
            const filters = {
                id: { contains: '3' },
                not: { id: { eq: '333' } },
            };
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c WHERE CONTAINS(c["id"], @id_0) AND NOT (c["id"] = @id_1)',
                parameters: [
                    {
                        name: '@id_0',
                        value: '3',
                    },
                    {
                        name: '@id_1',
                        value: '333',
                    },
                ],
            }));
        });
        it('Supports AND & OR filter combinators', async () => {
            const filters = {
                id: { ne: 'test' },
                or: [{ id: { beginsWith: '1' } }, { id: { beginsWith: '2' } }],
                and: [{ id: { contains: '3' } }, { id: { contains: '4' } }],
            };
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c WHERE c["id"] <> @id_0 ' +
                    'AND (STARTSWITH(c["id"], @id_1) or STARTSWITH(c["id"], @id_2)) ' +
                    'AND (CONTAINS(c["id"], @id_3) and CONTAINS(c["id"], @id_4))',
                parameters: [
                    {
                        name: '@id_0',
                        value: 'test',
                    },
                    {
                        name: '@id_1',
                        value: '1',
                    },
                    {
                        name: '@id_2',
                        value: '2',
                    },
                    {
                        name: '@id_3',
                        value: '3',
                    },
                    {
                        name: '@id_4',
                        value: '4',
                    },
                ],
            }));
        });
        it('Supports nested properties filters', async () => {
            const filters = {
                mainItem: {
                    sku: { eq: 'test' },
                    price: {
                        cents: { gte: 1000, lt: 100000 },
                    },
                },
            };
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c WHERE c["mainItem"]["sku"] = @sku_0 ' +
                    'AND c["mainItem"]["price"]["cents"] >= @cents_0 AND c["mainItem"]["price"]["cents"] < @cents_1',
                parameters: [
                    {
                        name: '@sku_0',
                        value: 'test',
                    },
                    {
                        name: '@cents_0',
                        value: 1000,
                    },
                    {
                        name: '@cents_1',
                        value: 100000,
                    },
                ],
            }));
        });
        it('Supports array includes filter', async () => {
            const filters = {
                days: { includes: 2 },
                items: { includes: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
            };
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c WHERE ARRAY_CONTAINS(c["days"], @days_0, true) AND ARRAY_CONTAINS(c["items"], @items_0, true)',
                parameters: [
                    {
                        name: '@days_0',
                        value: 2,
                    },
                    {
                        name: '@items_0',
                        value: { sku: 'test', price: { cents: 1000, currency: 'EUR' } },
                    },
                ],
            }));
        });
        it('Supports order for 1 field', async () => {
            const filters = {};
            const order = { sku: 'DESC' };
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters, undefined, undefined, undefined, order);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c  ORDER BY c.sku DESC',
                parameters: [],
            }));
        });
        it('Supports order for any number of fields', async () => {
            const filters = {};
            const order = { sku: 'DESC', price: 'ASC' };
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters, undefined, undefined, undefined, order);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c  ORDER BY c.sku DESC, c.price ASC',
                parameters: [],
            }));
        });
        it('Supports order for nested fields', async () => {
            const filters = {};
            const order = { sku: 'DESC', address: { street: 'ASC' } };
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters, undefined, undefined, undefined, order);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c  ORDER BY c.sku DESC, c.address.street ASC',
                parameters: [],
            }));
        });
        it('Supports limited results', async () => {
            const filters = {
                days: { includes: 2 },
                items: { includes: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
            };
            const order = { sku: 'DESC', price: 'ASC' };
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters, 3, undefined, false, order);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c WHERE ARRAY_CONTAINS(c["days"], @days_0, true) AND ARRAY_CONTAINS(c["items"], @items_0, true) ORDER BY c.sku DESC, c.price ASC OFFSET 0 LIMIT 3 ',
                parameters: [
                    {
                        name: '@days_0',
                        value: 2,
                    },
                    {
                        name: '@items_0',
                        value: { sku: 'test', price: { cents: 1000, currency: 'EUR' } },
                    },
                ],
            }));
        });
        it('Supports paginated and limited results', async () => {
            const filters = {
                days: { includes: 2 },
                items: { includes: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
            };
            const order = { sku: 'DESC', price: 'ASC' };
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters, 3, { id: '3' }, true, order);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c WHERE ARRAY_CONTAINS(c["days"], @days_0, true) AND ARRAY_CONTAINS(c["items"], @items_0, true) ORDER BY c.sku DESC, c.price ASC OFFSET 3 LIMIT 3 ',
                parameters: [
                    {
                        name: '@days_0',
                        value: 2,
                    },
                    {
                        name: '@items_0',
                        value: { sku: 'test', price: { cents: 1000, currency: 'EUR' } },
                    },
                ],
            }));
        });
        it('supports isDefine filters', async () => {
            const filters = {
                and: [
                    { days: { isDefined: true } },
                    { mainItem: { isDefined: false } },
                    { mainItem: { sku: { isDefined: true } } },
                ],
            };
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters, undefined, undefined, undefined, undefined);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c WHERE (IS_DEFINED(c["days"]) and NOT IS_DEFINED(c["mainItem"]) and IS_DEFINED(c["mainItem"]["sku"]))',
                parameters: [],
            }));
        });
        it('supports isDefine filters with complex filters', async () => {
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
            await (0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters, undefined, undefined, undefined, undefined);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container(`${mockReadModelName}`).items
                .query).to.have.been.calledWith((0, sinon_1.match)({
                query: 'SELECT * FROM c WHERE (c["id"] = @id_0 and c["mainItem"]["sku"] = @sku_0 and (IS_DEFINED(c["days"]) or ARRAY_CONTAINS(c["items"], @items_0, true)) and c["mainItem"]["sku"] = @sku_1 and c["mainItem"]["price"]["cents"] <> @cents_0)',
                parameters: [
                    { name: '@id_0', value: '3' },
                    { name: '@sku_0', value: 'test' },
                    { name: '@items_0', value: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
                    { name: '@sku_1', value: null },
                    { name: '@cents_0', value: null },
                ],
            }));
        });
        it('Throws an error with non supported filters', async () => {
            const unknownOperator = 'existsIn';
            const filters = {
                id: { [unknownOperator]: 'test' },
            };
            // eslint-disable-next-line @typescript-eslint/no-floating-promises
            (0, expect_1.expect)((0, query_helper_1.search)(mockCosmosDbClient, mockConfig, mockReadModelName, filters)).to.be.eventually.rejectedWith(`Operator "${unknownOperator}" is not supported`);
        });
    });
});
