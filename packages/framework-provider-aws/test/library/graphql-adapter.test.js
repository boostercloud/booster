"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const graphql_adapter_1 = require("../../src/library/graphql-adapter");
const sinon_1 = require("sinon");
const faker_1 = require("faker");
const framework_types_1 = require("@boostercloud/framework-types");
describe('AWS Provider graphql-adapter', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('the `rawGraphQLRequestToEnvelope`', () => {
        let mockRequestId;
        let mockConnectionId;
        let mockToken;
        let expectedQuery;
        let expectedVariables;
        let request;
        let expectedOutput;
        beforeEach(() => {
            mockRequestId = faker_1.random.number().toString();
            mockConnectionId = faker_1.random.uuid();
            mockToken = faker_1.random.uuid();
            expectedQuery = 'GraphQL query';
            expectedVariables = {
                varOne: faker_1.random.number(),
                varTwo: faker_1.random.alphaNumeric(10),
            };
            request = {
                headers: {
                    Authorization: mockToken,
                },
                requestContext: {
                    requestId: mockRequestId,
                    eventType: 'CONNECT',
                    connectionId: mockConnectionId,
                },
                body: JSON.stringify({
                    query: expectedQuery,
                    variables: expectedVariables,
                }),
            };
            expectedOutput = {
                requestID: mockRequestId,
                eventType: 'CONNECT',
                connectionID: mockConnectionId,
                token: mockToken,
                value: {
                    query: expectedQuery,
                    variables: expectedVariables,
                },
                context: {
                    request: {
                        headers: {
                            Authorization: mockToken,
                        },
                        body: JSON.stringify({
                            query: expectedQuery,
                            variables: expectedVariables,
                        }),
                    },
                    rawContext: request,
                },
            };
        });
        it('generates an envelope correctly from an AWS event', async () => {
            const config = new framework_types_1.BoosterConfig('test');
            const gotOutput = await (0, graphql_adapter_1.rawGraphQLRequestToEnvelope)(config, request);
            (0, expect_1.expect)(gotOutput).to.be.deep.equal(expectedOutput);
        });
    });
});
