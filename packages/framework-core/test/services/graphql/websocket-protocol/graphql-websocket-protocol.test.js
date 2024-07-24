"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const sinon_1 = require("sinon");
const faker_1 = require("faker");
const framework_types_1 = require("@boostercloud/framework-types");
const graphql_websocket_protocol_1 = require("../../../../src/services/graphql/websocket-protocol/graphql-websocket-protocol");
const expect_1 = require("../../../expect");
const booster_token_verifier_1 = require("../../../../src/booster-token-verifier");
describe('the `GraphQLWebsocketHandler`', () => {
    let config;
    let websocketHandler;
    let connectionsManager;
    let onStartCallback;
    let onStopCallback;
    let onTerminateCallback;
    let envelope;
    let boosterTokenVerifier;
    beforeEach(() => {
        config = new framework_types_1.BoosterConfig('test');
        config.logger = {
            debug: (0, sinon_1.fake)(),
            info: (0, sinon_1.fake)(),
            warn: (0, sinon_1.fake)(),
            error: (0, sinon_1.fake)(),
        };
        boosterTokenVerifier = new booster_token_verifier_1.BoosterTokenVerifier(config);
        connectionsManager = {
            sendMessage: (0, sinon_1.stub)(),
            deleteData: (0, sinon_1.stub)(),
            fetchData: (0, sinon_1.stub)(),
            storeData: (0, sinon_1.stub)(),
        };
        onStartCallback = (0, sinon_1.stub)();
        onStopCallback = (0, sinon_1.stub)();
        onTerminateCallback = (0, sinon_1.stub)();
        websocketHandler = new graphql_websocket_protocol_1.GraphQLWebsocketHandler(config, connectionsManager, {
            onStartOperation: onStartCallback,
            onStopOperation: onStopCallback,
            onTerminate: onTerminateCallback,
        }, boosterTokenVerifier);
        envelope = {
            currentUser: undefined,
            eventType: 'MESSAGE',
            requestID: faker_1.random.alphaNumeric(10),
        };
    });
    describe('the "handle" method', () => {
        let resultPromise;
        beforeEach(() => {
            resultPromise = undefined;
        });
        afterEach(async () => {
            // The handle method must never fail, just log or send the error to the connection ID.
            // We ensure here that the returned promise from the method is always fulfilled
            (0, expect_1.expect)(resultPromise, "The test didn't set the 'resultPromise' variable with the result of 'handle' method").not
                .to.be.undefined;
            await (0, expect_1.expect)(resultPromise).to.eventually.be.fulfilled;
        });
        describe('with an envelope with no connectionID', () => {
            beforeEach(() => {
                envelope.connectionID = undefined;
            });
            it('just logs an error', async () => {
                var _a;
                resultPromise = websocketHandler.handle(envelope);
                await resultPromise;
                (0, expect_1.expect)((_a = config.logger) === null || _a === void 0 ? void 0 : _a.error).to.be.calledOnceWithExactly(sinon_1.match.any, 'Missing websocket connectionID');
            });
        });
        describe('with an envelope with connectionID', () => {
            beforeEach(() => {
                envelope.connectionID = faker_1.random.alphaNumeric(10);
            });
            describe('with an error in the envelope', () => {
                const errorMessage = faker_1.lorem.sentences(1);
                let envelopeWithError;
                beforeEach(() => {
                    envelopeWithError = {
                        ...envelope,
                        error: new Error(errorMessage),
                    };
                });
                it('sends the error to the client', async () => {
                    resultPromise = websocketHandler.handle(envelopeWithError);
                    await resultPromise;
                    (0, expect_1.expect)(connectionsManager.sendMessage).to.be.calledOnceWithExactly(config, envelopeWithError.connectionID, (0, sinon_1.match)({
                        type: framework_types_1.MessageTypes.GQL_CONNECTION_ERROR,
                        payload: errorMessage,
                    }));
                });
            });
            describe('with an empty value', () => {
                beforeEach(() => {
                    envelope.value = undefined;
                });
                it('sends the right error', async () => {
                    resultPromise = websocketHandler.handle(envelope);
                    await resultPromise;
                    (0, expect_1.expect)(connectionsManager.sendMessage).to.be.calledOnceWithExactly(config, envelope.connectionID, (0, sinon_1.match)({
                        type: framework_types_1.MessageTypes.GQL_CONNECTION_ERROR,
                        payload: 'Received an empty GraphQL body',
                    }));
                });
            });
            describe('with a value with GQL_CONNECTION_INIT message', () => {
                beforeEach(() => {
                    envelope.value = {
                        type: framework_types_1.MessageTypes.GQL_CONNECTION_INIT,
                        payload: {},
                    };
                });
                it('sends back a GQL_CONNECTION_ACK', async () => {
                    resultPromise = websocketHandler.handle(envelope);
                    await resultPromise;
                    (0, expect_1.expect)(connectionsManager.sendMessage).to.be.calledOnceWithExactly(config, envelope.connectionID, (0, sinon_1.match)({ type: framework_types_1.MessageTypes.GQL_CONNECTION_ACK }));
                });
                it('stores connection data', async () => {
                    resultPromise = websocketHandler.handle(envelope);
                    await resultPromise;
                    (0, expect_1.expect)(connectionsManager.storeData).to.be.calledOnceWithExactly(config, envelope.connectionID, (0, sinon_1.match)({
                        user: undefined,
                        expirationTime: sinon_1.match.number,
                    }));
                });
                describe('with an access token', () => {
                    beforeEach(() => {
                        envelope.value = {
                            type: framework_types_1.MessageTypes.GQL_CONNECTION_INIT,
                            payload: {
                                Authorization: faker_1.random.uuid(),
                            },
                        };
                    });
                    it('stores connection data including the user', async () => {
                        const expectedUser = {
                            username: faker_1.internet.email(),
                            roles: [faker_1.lorem.word()],
                            claims: {},
                        };
                        const fakeVerifier = sinon_1.fake.returns(expectedUser);
                        (0, sinon_1.replace)(boosterTokenVerifier, 'verify', fakeVerifier);
                        resultPromise = websocketHandler.handle(envelope);
                        await resultPromise;
                        (0, expect_1.expect)(connectionsManager.storeData).to.be.calledOnceWithExactly(config, envelope.connectionID, (0, sinon_1.match)({
                            user: expectedUser,
                            expirationTime: sinon_1.match.number,
                        }));
                    });
                });
            });
            describe('with a value with GQL_START message', () => {
                beforeEach(() => {
                    envelope.value = {
                        id: faker_1.random.alphaNumeric(10),
                        type: framework_types_1.MessageTypes.GQL_START,
                        payload: {
                            query: faker_1.random.alphaNumeric(20),
                            variables: { aField: faker_1.random.alphaNumeric(5) },
                            operationName: faker_1.random.alphaNumeric(10),
                        },
                    };
                });
                it('fails if there is no "id"', async () => {
                    const value = envelope.value;
                    value.id = undefined; // Force "id" to be undefined
                    resultPromise = websocketHandler.handle(envelope);
                    await resultPromise;
                    (0, expect_1.expect)(connectionsManager.sendMessage).to.be.calledOnceWithExactly(config, envelope.connectionID, (0, sinon_1.match)({
                        type: framework_types_1.MessageTypes.GQL_CONNECTION_ERROR,
                        payload: `Missing "id" in ${framework_types_1.MessageTypes.GQL_START} message`,
                    }));
                });
                it('fails if there is no "payload"', async () => {
                    const value = envelope.value;
                    value.payload = undefined;
                    resultPromise = websocketHandler.handle(envelope);
                    await resultPromise;
                    (0, expect_1.expect)(connectionsManager.sendMessage).to.be.calledOnceWithExactly(config, envelope.connectionID, (0, sinon_1.match)({
                        type: framework_types_1.MessageTypes.GQL_ERROR,
                        id: value.id,
                        payload: {
                            errors: sinon_1.match.some(sinon_1.match.has('message', 'Message payload is invalid it must contain at least the "query" property')),
                        },
                    }));
                });
                it('fails if there is no "query"', async () => {
                    const message = envelope.value;
                    message.payload.query = undefined;
                    resultPromise = websocketHandler.handle(envelope);
                    await resultPromise;
                    (0, expect_1.expect)(connectionsManager.sendMessage).to.be.calledOnceWithExactly(config, envelope.connectionID, (0, sinon_1.match)({
                        type: framework_types_1.MessageTypes.GQL_ERROR,
                        id: message.id,
                        payload: {
                            errors: sinon_1.match.some(sinon_1.match.has('message', 'Message payload is invalid it must contain at least the "query" property')),
                        },
                    }));
                });
                it('calls "onStartOperation" with the right parameters', async () => {
                    const message = envelope.value;
                    const connectionData = {
                        user: {
                            username: faker_1.internet.email(),
                            roles: [faker_1.lorem.word()],
                            claims: {},
                        },
                        expirationTime: faker_1.random.number(),
                    };
                    const fetchDataFake = connectionsManager.fetchData;
                    fetchDataFake.withArgs(config, envelope.connectionID).returns(connectionData);
                    resultPromise = websocketHandler.handle(envelope);
                    await resultPromise;
                    (0, expect_1.expect)(onStartCallback).to.be.calledOnceWithExactly({
                        ...envelope,
                        currentUser: connectionData.user,
                        value: {
                            ...message.payload,
                            id: message.id,
                        },
                    });
                });
                context('when "onStartOperation" returns the result of a subscription', () => {
                    beforeEach(() => {
                        onStartCallback = (0, sinon_1.stub)().returns({ next: () => { } });
                        websocketHandler = new graphql_websocket_protocol_1.GraphQLWebsocketHandler(config, connectionsManager, {
                            onStartOperation: onStartCallback,
                            onStopOperation: undefined,
                            onTerminate: undefined,
                        }, boosterTokenVerifier);
                    });
                    it('does not send anything back', async () => {
                        resultPromise = websocketHandler.handle(envelope);
                        await resultPromise;
                        (0, expect_1.expect)(connectionsManager.sendMessage).not.to.be.called;
                    });
                });
                context('when "onStartOperation" returns the result of a query or mutation', () => {
                    const result = {
                        data: 'The result',
                    };
                    beforeEach(() => {
                        onStartCallback = (0, sinon_1.stub)().returns(result);
                        websocketHandler = new graphql_websocket_protocol_1.GraphQLWebsocketHandler(config, connectionsManager, {
                            onStartOperation: onStartCallback,
                            onStopOperation: undefined,
                            onTerminate: undefined,
                        }, boosterTokenVerifier);
                    });
                    it('sends back the expected messages', async () => {
                        resultPromise = websocketHandler.handle(envelope);
                        await resultPromise;
                        const sendMessageFake = connectionsManager.sendMessage;
                        (0, expect_1.expect)(sendMessageFake).to.be.calledTwice;
                        (0, expect_1.expect)(sendMessageFake.getCall(0).args).to.be.deep.equal([
                            config,
                            envelope.connectionID,
                            {
                                type: framework_types_1.MessageTypes.GQL_DATA,
                                id: envelope.value.id,
                                payload: result,
                            },
                        ]);
                        (0, expect_1.expect)(sendMessageFake.getCall(1).args).to.be.deep.equal([
                            config,
                            envelope.connectionID,
                            {
                                type: framework_types_1.MessageTypes.GQL_COMPLETE,
                                id: envelope.value.id,
                            },
                        ]);
                    });
                });
            });
            describe('with a value with GQL_STOP message', () => {
                beforeEach(() => {
                    envelope.value = {
                        type: framework_types_1.MessageTypes.GQL_STOP,
                        id: faker_1.random.alphaNumeric(10),
                    };
                });
                it('fails if there is no "id"', async () => {
                    const value = envelope.value;
                    value.id = undefined; // Force "id" to be undefined
                    resultPromise = websocketHandler.handle(envelope);
                    await resultPromise;
                    (0, expect_1.expect)(connectionsManager.sendMessage).to.be.calledOnceWithExactly(config, envelope.connectionID, (0, sinon_1.match)({
                        type: framework_types_1.MessageTypes.GQL_CONNECTION_ERROR,
                        payload: `Missing "id" in ${framework_types_1.MessageTypes.GQL_STOP} message`,
                    }));
                });
                it('calls "onStopOperation" with the right parameters', async () => {
                    const value = envelope.value;
                    resultPromise = websocketHandler.handle(envelope);
                    await resultPromise;
                    (0, expect_1.expect)(onStopCallback).to.have.been.calledOnceWithExactly(envelope.connectionID, value.id);
                });
                it('sends back a GQL_COMPLETE message', async () => {
                    const value = envelope.value;
                    resultPromise = websocketHandler.handle(envelope);
                    await resultPromise;
                    (0, expect_1.expect)(connectionsManager.sendMessage).to.have.been.calledOnceWithExactly(config, envelope.connectionID, (0, sinon_1.match)({
                        type: framework_types_1.MessageTypes.GQL_COMPLETE,
                        id: value.id,
                    }));
                });
            });
            describe('with a value with GQL_CONNECTION_TERMINATE message', () => {
                beforeEach(() => {
                    envelope.value = {
                        type: framework_types_1.MessageTypes.GQL_CONNECTION_TERMINATE,
                    };
                });
                it('calls "onTerminateOperation" with the right parameters and sends nothing back', async () => {
                    resultPromise = websocketHandler.handle(envelope);
                    await resultPromise;
                    (0, expect_1.expect)(onTerminateCallback).to.have.been.calledOnceWithExactly(envelope.connectionID);
                    (0, expect_1.expect)(connectionsManager.sendMessage).not.to.have.been.called;
                });
            });
        });
    });
});
