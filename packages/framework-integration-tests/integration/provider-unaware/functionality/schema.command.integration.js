"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const chai_1 = require("chai");
const client_1 = require("@apollo/client");
const setup_1 = require("../end-to-end/setup");
describe('schemas', async () => {
    let client;
    before(async () => {
        client = await setup_1.applicationUnderTest.graphql.client();
    });
    describe('should return the expected schema for commands', async () => {
        it('When there is an ignored parameter', async () => {
            const queryResult = await client.query({
                query: (0, client_1.gql) `
          query UniversalQuery {
            __type(name: "ChangeCartItemInput") {
              __typename
              name
              kind
              inputFields {
                __typename
                name
                type {
                  __typename
                  name
                  kind
                }
              }
            }
          }
        `,
            });
            const fieldsNames = queryResult.data.__type.inputFields.map((field) => field.name);
            (0, chai_1.expect)(fieldsNames).to.have.members(['cartId', 'productId', 'quantity']);
        });
    });
});
