"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
describe('Data consistency on entities', () => {
    let client;
    let token;
    before(async () => {
        const userEmail = faker_1.internet.email();
        token = setup_1.applicationUnderTest.token.forUser(userEmail, 'Admin');
        client = setup_1.applicationUnderTest.graphql.client(token);
    });
    context('with 1000 products ready to be stocked', () => {
        const idPrefix = faker_1.random.alpha({ count: 10 });
        const destinationWarehouse = 'GC';
        const numberOfProducts = 1000;
        let productIDs;
        before(async () => {
            productIDs = [];
            for (let i = 0; i < numberOfProducts; i++) {
                productIDs[i] = idPrefix + faker_1.random.uuid();
            }
        });
        it('adds stock to all of them with many events without corrupting data', async () => {
            const durationWarmup = 10;
            const arrivalRateWarmup = 500;
            const durationBurst = 10;
            const arrivalRateBurst = 1200;
            const expectedStock = durationWarmup * arrivalRateWarmup + durationBurst * arrivalRateBurst;
            await setup_1.scriptExecutor.executeScript('move-product-stock.yml', {
                variables: { token, productID: productIDs, destinationWarehouse },
                phases: [
                    {
                        duration: durationWarmup,
                        arrivalRate: arrivalRateWarmup,
                    },
                    {
                        duration: durationBurst,
                        arrivalRate: arrivalRateBurst,
                    },
                ],
            });
            await (0, sleep_1.waitForIt)(() => queryStocks(client, idPrefix), (result) => {
                const totalStock = result.data.StockReadModels.map((stock) => stock.warehouses[destinationWarehouse]).reduce((stockProductA, stockProductB) => stockProductA + stockProductB, 0);
                console.debug(`Total stock. Got: ${totalStock}, expected: ${expectedStock}`);
                return totalStock === expectedStock;
            });
        });
    });
});
async function queryStocks(client, idPrefix) {
    return client.query({
        query: (0, client_1.gql) `
        query {
            StockReadModels(filter: { id: { beginsWith: "${idPrefix}"}}) {
                id
                warehouses
            }
        }
    `,
    });
}
