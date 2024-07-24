"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const chai_1 = require("chai");
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
const CART_READ_MODEL_NAME = 'CartReadModel';
describe('entities', async () => {
    let client;
    before(async () => {
        client = setup_1.applicationUnderTest.graphql.client();
    });
    it('should be projected into a read model', async () => {
        var _a;
        const readModelItemsCount = await setup_1.applicationUnderTest.count.readModels(CART_READ_MODEL_NAME);
        const mockCartId = faker_1.random.uuid();
        const mockProductId = faker_1.random.uuid();
        const mockQuantity = faker_1.random.number({ min: 1 });
        const mockPaymentId = faker_1.random.uuid();
        const mockConfirmationToken = faker_1.random.alphaNumeric(10);
        await client.mutate({
            variables: {
                cartId: mockCartId,
                productId: mockProductId,
                quantity: mockQuantity,
            },
            mutation: (0, client_1.gql) `
        mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
          ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
        }
      `,
        });
        await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.query.readModels(mockCartId, CART_READ_MODEL_NAME), 
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (readModel) => { var _a, _b, _c; return readModel && ((_a = readModel[0]) === null || _a === void 0 ? void 0 : _a.id) === mockCartId && ((_c = (_b = readModel[0]) === null || _b === void 0 ? void 0 : _b.cartItems[0]) === null || _c === void 0 ? void 0 : _c.productId) === mockProductId; });
        const ConfirmPaymentResponse = await client.mutate({
            variables: {
                paymentId: mockPaymentId,
                cartId: mockCartId,
                confirmationToken: mockConfirmationToken,
            },
            mutation: (0, client_1.gql) `
        mutation ConfirmPayment($paymentId: ID!, $cartId: ID!, $confirmationToken: String!) {
          ConfirmPayment(input: { paymentId: $paymentId, cartId: $cartId, confirmationToken: $confirmationToken })
        }
      `,
        });
        (0, chai_1.expect)(ConfirmPaymentResponse).not.to.be.null;
        (0, chai_1.expect)((_a = ConfirmPaymentResponse === null || ConfirmPaymentResponse === void 0 ? void 0 : ConfirmPaymentResponse.data) === null || _a === void 0 ? void 0 : _a.ConfirmPayment).to.be.true;
        const expectedReadModelItemsCount = readModelItemsCount + 1;
        await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.count.readModels(CART_READ_MODEL_NAME), (newReadModelItemsCount) => newReadModelItemsCount === expectedReadModelItemsCount);
        const latestReadModelItem = await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.query.readModels(mockCartId, CART_READ_MODEL_NAME), (readModel) => {
            var _a, _b, _c, _d;
            return readModel &&
                ((_a = readModel[0]) === null || _a === void 0 ? void 0 : _a.id) === mockCartId &&
                ((_b = readModel[0]) === null || _b === void 0 ? void 0 : _b.payment) &&
                ((_d = (_c = readModel[0]) === null || _c === void 0 ? void 0 : _c.cartItems[0]) === null || _d === void 0 ? void 0 : _d.productId) === mockProductId;
        });
        (0, chai_1.expect)(latestReadModelItem).not.to.be.null;
        (0, chai_1.expect)(latestReadModelItem[0].id).to.be.equal(mockCartId);
        (0, chai_1.expect)(latestReadModelItem[0].cartItems[0].productId).to.be.equal(mockProductId);
        (0, chai_1.expect)(latestReadModelItem[0].cartItems[0].quantity).to.be.equal(mockQuantity);
        (0, chai_1.expect)(latestReadModelItem[0].cartItems[0].shippingAddress).to.be.undefined;
        (0, chai_1.expect)(latestReadModelItem[0].payment.id).to.be.equal(mockPaymentId);
        (0, chai_1.expect)(latestReadModelItem[0].payment.cartId).to.be.equal(mockCartId);
        (0, chai_1.expect)(latestReadModelItem[0].payment.confirmationToken).to.be.equal(mockConfirmationToken);
    });
});
