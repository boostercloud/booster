"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const framework_types_1 = require("@boostercloud/framework-types");
const graphql_adapter_1 = require("../../src/library/graphql-adapter");
describe('GraphQL adapter', () => {
    describe('The "rawGraphQLRequestToEnvelope"', () => {
        it('Generates an envelope correctly from an Azure event', async () => {
            const config = new framework_types_1.BoosterConfig('test');
            config.logger = console;
            const expectedQuery = 'GraphQL query';
            const expectedToken = 'token';
            const expectedVariables = {
                varOne: 3,
                varTwo: 'test',
            };
            const request = {
                req: {
                    body: {
                        query: expectedQuery,
                        variables: expectedVariables,
                    },
                    headers: {
                        authorization: expectedToken,
                    },
                },
                executionContext: {
                    invocationId: '123',
                },
            };
            const expectedOutput = {
                requestID: '123',
                eventType: 'MESSAGE',
                token: expectedToken,
                value: {
                    query: expectedQuery,
                    variables: expectedVariables,
                },
                context: {
                    request: {
                        body: {
                            query: expectedQuery,
                            variables: expectedVariables,
                        },
                        headers: {
                            authorization: expectedToken,
                        },
                    },
                    rawContext: request,
                },
                connectionID: undefined,
            };
            const gotOutput = await (0, graphql_adapter_1.rawGraphQLRequestToEnvelope)(config, request);
            (0, expect_1.expect)(gotOutput).to.be.deep.equal(expectedOutput);
        });
    });
});
