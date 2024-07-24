"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const sinon_1 = require("sinon");
const faker_1 = require("faker");
const expect_1 = require("./expect");
const framework_types_1 = require("@boostercloud/framework-types");
const booster_graphql_dispatcher_1 = require("../src/booster-graphql-dispatcher");
const gqlParser = require("graphql/language/parser");
const gqlValidator = require("graphql/validation/validate");
const gqlExecutor = require("graphql/execution/execute");
const gqlSubscriptor = require("graphql/execution/subscribe");
const noop_read_model_pub_sub_1 = require("../src/services/pub-sub/noop-read-model-pub-sub");
const graphql_websocket_protocol_1 = require("../src/services/graphql/websocket-protocol/graphql-websocket-protocol");
const graphql_1 = require("graphql");
const booster_token_verifier_1 = require("../src/booster-token-verifier");
describe('the `BoosterGraphQLDispatcher`', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('Introspection in graphQL API', () => {
        context('on introspection message', () => {
            it('with default config introspection is enabled', async () => {
                const graphQLResult = { data: 'the result' };
                const messageEnvelope = {
                    requestID: faker_1.random.uuid(),
                    eventType: 'MESSAGE',
                    value: {
                        query: '{__schema {queryType {name},mutationType { name }  }}',
                        variables: {},
                        operationName: undefined,
                    },
                };
                const config = mockConfigForGraphQLEnvelope(messageEnvelope);
                const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                const parseSpy = (0, sinon_1.spy)(gqlParser.parse);
                (0, sinon_1.replace)(gqlParser, 'parse', parseSpy);
                (0, sinon_1.replace)(gqlValidator, 'validate', sinon_1.fake.returns([]));
                const executeFake = sinon_1.fake.returns(graphQLResult);
                (0, sinon_1.replace)(gqlExecutor, 'execute', executeFake);
                await dispatcher.dispatch({});
                (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(graphQLResult, {});
            });
            it('override the introspection configuration and disable it', async () => {
                const graphQLResult = { data: 'the result' };
                const messageEnvelope = {
                    requestID: faker_1.random.uuid(),
                    eventType: 'MESSAGE',
                    value: {
                        query: '{__schema {queryType {name},mutationType { name }  }}',
                        variables: {},
                        operationName: undefined,
                    },
                };
                const config = mockConfigForGraphQLEnvelope(messageEnvelope);
                config.enableGraphQLIntrospection = false;
                const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                const parseSpy = (0, sinon_1.spy)(gqlParser.parse);
                (0, sinon_1.replace)(gqlParser, 'parse', parseSpy);
                (0, sinon_1.replace)(gqlValidator, 'validate', sinon_1.fake.returns([]));
                const executeFake = sinon_1.fake.returns(graphQLResult);
                (0, sinon_1.replace)(gqlExecutor, 'execute', executeFake);
                await dispatcher.dispatch({});
                (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly((0, sinon_1.match)((result) => {
                    return (result.errors[0].message ==
                        'Instrospection queries are disabled. Check the configuration if you want to enable them.');
                }), {});
            });
        });
    });
    describe('the `dispatch` method', () => {
        context('on CONNECT message', () => {
            it('calls the provider "handleGraphQLResult" with the GraphQL websocket subprotocol headers', async () => {
                const config = mockConfigForGraphQLEnvelope({
                    requestID: faker_1.random.uuid(),
                    eventType: 'CONNECT',
                });
                const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                await dispatcher.dispatch({});
                (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(null, {
                    'Sec-WebSocket-Protocol': 'graphql-ws',
                });
            });
        });
        context('on DISCONNECT message', () => {
            it('does does not delete connection or subscription data when there is no connection ID', async () => {
                const config = mockConfigForGraphQLEnvelope({
                    requestID: faker_1.random.uuid(),
                    eventType: 'DISCONNECT',
                    connectionID: undefined,
                });
                const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                await dispatcher.dispatch({});
                (0, expect_1.expect)(config.provider.readModels.deleteAllSubscriptions).not.to.have.been.called;
                (0, expect_1.expect)(config.provider.connections.deleteData).not.to.have.been.called;
                (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(undefined);
            });
            it('calls deletes connection and subscription data', async () => {
                const mockConnectionID = faker_1.random.uuid();
                const config = mockConfigForGraphQLEnvelope({
                    requestID: faker_1.random.uuid(),
                    eventType: 'DISCONNECT',
                    connectionID: mockConnectionID,
                });
                const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                await dispatcher.dispatch({});
                (0, expect_1.expect)(config.provider.connections.deleteData).to.have.been.calledOnceWithExactly(config, mockConnectionID);
                (0, expect_1.expect)(config.provider.readModels.deleteAllSubscriptions).to.have.been.calledOnceWithExactly(config, mockConnectionID);
                (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly(undefined);
            });
        });
        context('on MESSAGE message', () => {
            describe('when the message came through socket', () => {
                it('calls the websocket handler', async () => {
                    const messageEnvelope = {
                        requestID: faker_1.random.uuid(),
                        eventType: 'MESSAGE',
                        connectionID: faker_1.random.uuid(), // A non-null connectionID means it came through socket
                    };
                    const config = mockConfigForGraphQLEnvelope(messageEnvelope);
                    const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                    const fakeWebsocketHandleMethod = (0, sinon_1.fake)();
                    (0, sinon_1.replace)(graphql_websocket_protocol_1.GraphQLWebsocketHandler.prototype, 'handle', fakeWebsocketHandleMethod);
                    await dispatcher.dispatch({});
                    (0, expect_1.expect)(fakeWebsocketHandleMethod).to.be.calledOnceWithExactly(messageEnvelope);
                });
            });
            describe('when the message came through HTTP', () => {
                it('does not call the websocket handler', async () => {
                    const config = mockConfigForGraphQLEnvelope({
                        requestID: faker_1.random.uuid(),
                        eventType: 'MESSAGE',
                    });
                    const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                    const fakeWebsocketHandleMethod = (0, sinon_1.fake)();
                    (0, sinon_1.replace)(graphql_websocket_protocol_1.GraphQLWebsocketHandler.prototype, 'handle', fakeWebsocketHandleMethod);
                    await dispatcher.dispatch({});
                    (0, expect_1.expect)(fakeWebsocketHandleMethod).not.to.be.called;
                });
                it('calls the provider "handleGraphQLResult" when the envelope contains errors', async () => {
                    const errorMessage = faker_1.lorem.sentences(1);
                    const config = mockConfigForGraphQLEnvelope({
                        requestID: faker_1.random.uuid(),
                        eventType: 'MESSAGE',
                        error: new Error(errorMessage),
                    });
                    const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                    await dispatcher.dispatch({});
                    (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly((0, sinon_1.match)((result) => {
                        return result.errors[0].message == errorMessage;
                    }), {});
                });
                it('calls the provider "handleGraphQLResult" with an error when there is an empty query', async () => {
                    const config = mockConfigForGraphQLEnvelope({
                        requestID: faker_1.random.uuid(),
                        eventType: 'MESSAGE',
                    });
                    const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                    await dispatcher.dispatch({});
                    (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly((0, sinon_1.match)((result) => {
                        return result.errors[0].message == 'Received an empty GraphQL body';
                    }), {});
                });
                it('calls the provider "handleGraphQLResult" with an error when there is an empty body', async () => {
                    const config = mockConfigForGraphQLEnvelope({
                        requestID: faker_1.random.uuid(),
                        eventType: 'MESSAGE',
                        value: {
                            query: undefined,
                            // eslint-disable-next-line @typescript-eslint/no-explicit-any
                        }, // If not, the compiler does not allow us to provide an empty query
                    });
                    const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                    await dispatcher.dispatch({});
                    (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly((0, sinon_1.match)((result) => {
                        return result.errors[0].message == 'Received an empty GraphQL query';
                    }), {});
                });
                it('calls the provider "handleGraphQLResult" with an error when a subscription operation is used', async () => {
                    const errorRegex = /This API and protocol does not support "subscription" operations/;
                    const config = mockConfigForGraphQLEnvelope({
                        requestID: faker_1.random.uuid(),
                        eventType: 'MESSAGE',
                        value: {
                            query: 'subscription { a { x }}',
                        },
                    });
                    const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                    (0, sinon_1.replace)(gqlValidator, 'validate', sinon_1.fake.returns([]));
                    //await expect(dispatcher.dispatch({})).to.be.rejectedWith(errorRegex)
                    await dispatcher.dispatch({});
                    (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledOnceWithExactly((0, sinon_1.match)((result) => {
                        return new RegExp(errorRegex).test(result.errors[0].message);
                    }), {});
                });
                it('calls the the GraphQL engine with the passed envelope and handles the result', async () => {
                    const graphQLBody = 'query { a { x }}';
                    const graphQLResult = { data: 'the result' };
                    const graphQLVariables = { productId: 'productId' };
                    const graphQLEnvelope = {
                        requestID: faker_1.random.uuid(),
                        eventType: 'MESSAGE',
                        value: {
                            query: graphQLBody,
                            variables: graphQLVariables,
                        },
                    };
                    const resolverContext = {
                        requestID: graphQLEnvelope.requestID,
                        operation: {
                            query: graphQLBody,
                            variables: graphQLVariables,
                        },
                        pubSub: new noop_read_model_pub_sub_1.NoopReadModelPubSub(),
                        storeSubscriptions: true,
                        responseHeaders: {},
                    };
                    const config = mockConfigForGraphQLEnvelope(graphQLEnvelope);
                    const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                    const executeFake = sinon_1.fake.returns(graphQLResult);
                    const parseSpy = (0, sinon_1.spy)(gqlParser.parse);
                    (0, sinon_1.replace)(gqlParser, 'parse', parseSpy);
                    (0, sinon_1.replace)(gqlValidator, 'validate', sinon_1.fake.returns([]));
                    (0, sinon_1.replace)(gqlExecutor, 'execute', executeFake);
                    await dispatcher.dispatch({});
                    (0, expect_1.expect)(parseSpy).to.have.been.calledWithExactly(graphQLBody);
                    (0, expect_1.expect)(executeFake).to.have.been.calledWithExactly({
                        schema: sinon_1.match.any,
                        document: sinon_1.match.any,
                        contextValue: (0, sinon_1.match)(resolverContext),
                        variableValues: (0, sinon_1.match)(graphQLVariables),
                        operationName: sinon_1.match.any,
                    });
                    (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLResult, {});
                });
                it('calls the the GraphQL engine with the passed envelope and handles the result including the `responseHeaders`', async () => {
                    const graphQLBody = 'query { a { x }}';
                    const graphQLResult = { data: 'the result' };
                    const graphQLVariables = { productId: 'productId' };
                    const graphQLEnvelope = {
                        requestID: faker_1.random.uuid(),
                        eventType: 'MESSAGE',
                        value: {
                            query: graphQLBody,
                            variables: graphQLVariables,
                        },
                    };
                    const resolverContext = {
                        requestID: graphQLEnvelope.requestID,
                        operation: {
                            query: graphQLBody,
                            variables: graphQLVariables,
                        },
                        pubSub: new noop_read_model_pub_sub_1.NoopReadModelPubSub(),
                        storeSubscriptions: true,
                        responseHeaders: {},
                    };
                    const config = mockConfigForGraphQLEnvelope(graphQLEnvelope);
                    const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                    const parseSpy = (0, sinon_1.spy)(gqlParser, 'parse');
                    (0, sinon_1.replace)(gqlValidator, 'validate', sinon_1.fake.returns([]));
                    const executeFake = (0, sinon_1.fake)((params) => {
                        // Simulates that the handler has added the `responseHeaders`
                        params.contextValue.responseHeaders['Test-Header'] = 'Test-Value';
                        return graphQLResult;
                    });
                    (0, sinon_1.replace)(gqlExecutor, 'execute', executeFake);
                    await dispatcher.dispatch({});
                    (0, expect_1.expect)(parseSpy).to.have.been.calledWithExactly(graphQLBody);
                    (0, expect_1.expect)(executeFake).to.have.been.calledWithExactly({
                        schema: sinon_1.match.any,
                        document: sinon_1.match.any,
                        contextValue: (0, sinon_1.match)(resolverContext),
                        variableValues: (0, sinon_1.match)(graphQLVariables),
                        operationName: sinon_1.match.any,
                    });
                    (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLResult, {
                        'Test-Header': 'Test-Value',
                    });
                });
                it('calls the the GraphQL engine with the passed envelope with an authorization token and handles the result', async () => {
                    const graphQLBody = 'query { a { x }}';
                    const graphQLResult = { data: 'the result' };
                    const graphQLVariables = { productId: 'productId' };
                    const graphQLEnvelope = {
                        requestID: faker_1.random.uuid(),
                        eventType: 'MESSAGE',
                        value: {
                            query: graphQLBody,
                            variables: graphQLVariables,
                        },
                        currentUser: undefined,
                        token: faker_1.random.uuid(),
                    };
                    const resolverContext = {
                        requestID: graphQLEnvelope.requestID,
                        operation: {
                            query: graphQLBody,
                            variables: graphQLVariables,
                        },
                        pubSub: new noop_read_model_pub_sub_1.NoopReadModelPubSub(),
                        storeSubscriptions: true,
                        responseHeaders: {},
                    };
                    const currentUser = {
                        username: faker_1.internet.email(),
                        roles: [faker_1.random.word()],
                        claims: {},
                    };
                    const config = mockConfigForGraphQLEnvelope(graphQLEnvelope);
                    const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                    const executeFake = sinon_1.fake.returns(graphQLResult);
                    const parseSpy = (0, sinon_1.spy)(gqlParser.parse);
                    (0, sinon_1.replace)(gqlParser, 'parse', parseSpy);
                    (0, sinon_1.replace)(gqlValidator, 'validate', sinon_1.fake.returns([]));
                    (0, sinon_1.replace)(gqlExecutor, 'execute', executeFake);
                    const fakeVerifier = sinon_1.fake.returns(currentUser);
                    (0, sinon_1.replace)(booster_token_verifier_1.BoosterTokenVerifier.prototype, 'verify', fakeVerifier);
                    resolverContext.user = currentUser;
                    await dispatcher.dispatch({});
                    (0, expect_1.expect)(fakeVerifier).to.have.been.calledWithExactly(graphQLEnvelope.token);
                    (0, expect_1.expect)(parseSpy).to.have.been.calledWithExactly(graphQLBody);
                    (0, expect_1.expect)(executeFake).to.have.been.calledWithExactly({
                        schema: sinon_1.match.any,
                        document: sinon_1.match.any,
                        contextValue: (0, sinon_1.match)(resolverContext),
                        variableValues: (0, sinon_1.match)(graphQLVariables),
                        operationName: sinon_1.match.any,
                    });
                    (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLResult, {});
                });
                context('with graphql execution returning errors', () => {
                    let graphQLErrorResult;
                    beforeEach(() => {
                        (0, sinon_1.replace)(gqlValidator, 'validate', sinon_1.fake.returns([]));
                        graphQLErrorResult = {
                            errors: [new graphql_1.GraphQLError('graphql error 1'), new graphql_1.GraphQLError('graphql error 2')],
                        };
                        (0, sinon_1.replace)(gqlExecutor, 'execute', sinon_1.fake.returns(graphQLErrorResult));
                        (0, sinon_1.replace)(gqlSubscriptor, 'subscribe', sinon_1.fake.returns(graphQLErrorResult));
                    });
                    it('calls the provider "handleGraphQLResult" with the error with a query', async () => {
                        const config = mockConfigForGraphQLEnvelope({
                            requestID: faker_1.random.uuid(),
                            eventType: 'MESSAGE',
                            value: {
                                query: 'query { a { x }}',
                            },
                        });
                        const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                        await dispatcher.dispatch({});
                        // Check that the handled error includes all the errors that GraphQL reported
                        (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLErrorResult, {});
                    });
                    it('calls the provider "handleGraphQLResult" with the error with a mutation', async () => {
                        const config = mockConfigForGraphQLEnvelope({
                            requestID: faker_1.random.uuid(),
                            eventType: 'MESSAGE',
                            value: {
                                query: 'mutation { a { x }}',
                            },
                        });
                        const dispatcher = new booster_graphql_dispatcher_1.BoosterGraphQLDispatcher(config);
                        await dispatcher.dispatch({});
                        // Check that the handled error includes all the errors that GraphQL reported
                        (0, expect_1.expect)(config.provider.graphQL.handleResult).to.have.been.calledWithExactly(graphQLErrorResult, {});
                    });
                });
            });
        });
    });
});
function mockConfigForGraphQLEnvelope(envelope) {
    const config = new framework_types_1.BoosterConfig('test');
    config.provider = {
        graphQL: {
            rawToEnvelope: sinon_1.fake.resolves(envelope),
            handleResult: (0, sinon_1.fake)(),
        },
        readModels: {
            notifySubscription: (0, sinon_1.fake)(),
            deleteAllSubscriptions: (0, sinon_1.fake)(),
        },
        connections: {
            storeData: (0, sinon_1.fake)(),
            fetchData: (0, sinon_1.fake)(),
            deleteData: (0, sinon_1.fake)(),
            sendMessage: (0, sinon_1.fake)(),
        },
    };
    return config;
}
