"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const chai_1 = require("chai");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
const client_1 = require("@apollo/client");
describe('Scheduled commands', () => {
    it('scheduled command ran and created a product', async () => {
        var _a;
        const client = await setup_1.applicationUnderTest.graphql.client();
        const checkedCartId = 'the-checked-cart';
        // Check that scheduled command created the new product
        const cartData = await (0, sleep_1.waitForIt)(() => {
            return client.query({
                query: (0, client_1.gql) `
            query {
              CartReadModel(id: "${checkedCartId}") {
                id
                checks
              }
            }
          `,
            });
        }, (result) => { var _a; return !!((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModel); }, 10000, 90000 // CheckCartCount is run every minute, we need to give this test enough time to make sure that the cloud provider does the first call
        );
        const cartReadModel = (_a = cartData === null || cartData === void 0 ? void 0 : cartData.data) === null || _a === void 0 ? void 0 : _a.CartReadModel;
        (0, chai_1.expect)(cartReadModel).not.to.be.null;
        (0, chai_1.expect)(cartReadModel.id).to.equal(checkedCartId);
        (0, chai_1.expect)(cartReadModel.checks).to.be.greaterThan(0);
    });
});
