"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const expect_1 = require("../../helper/expect");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
describe('Data consistency on read models', () => {
    let client;
    let token;
    before(async () => {
        const userEmail = faker_1.internet.email();
        token = setup_1.applicationUnderTest.token.forUser(userEmail, 'UserWithEmail');
        client = setup_1.applicationUnderTest.graphql.client(token);
    });
    context('with 400 products on the same SKU', () => {
        it('processes the events without corrupting read models data', async () => {
            const duration = 2;
            const arrivalRate = 200;
            const expectedProductsBySku = duration * arrivalRate;
            const sku = `ABC_${faker_1.random.uuid()}`;
            await setup_1.scriptExecutor.executeScript('create-products-same-sku.yml', {
                variables: { token, sku },
                phases: [{ duration, arrivalRate }],
            });
            const result = await (0, sleep_1.waitForIt)(() => client.query({
                variables: { sku },
                query: (0, client_1.gql) `
              query ProductsBySKU($sku: ID!) {
                ProductsBySKU(id: $sku) {
                  id
                  products
                }
              }
            `,
            }), (result) => {
                var _a;
                const currentProducts = (_a = result.data.ProductsBySKU) === null || _a === void 0 ? void 0 : _a.products.length;
                console.debug(`Products with the same SKU. Got: ${currentProducts}, expected: ${expectedProductsBySku}`);
                return currentProducts === expectedProductsBySku;
            });
            (0, expect_1.expect)(result.data.ProductsBySKU.id).to.be.equal(sku);
            (0, expect_1.expect)(result.data.ProductsBySKU.products.length).to.be.equal(expectedProductsBySku);
        });
    });
});
