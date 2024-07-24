"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("./expect");
const framework_types_1 = require("@boostercloud/framework-types");
const sinon_1 = require("sinon");
const booster_read_models_reader_1 = require("../src/booster-read-models-reader");
const faker_1 = require("faker");
const booster_1 = require("../src/booster");
const booster_authorizer_1 = require("../src/booster-authorizer");
const read_model_schema_migrator_1 = require("../src/read-model-schema-migrator");
describe('BoosterReadModelReader', () => {
    const config = new framework_types_1.BoosterConfig('test');
    config.provider = {
        readModels: {
            search: (0, sinon_1.fake)(),
            subscribe: (0, sinon_1.fake)(),
            deleteSubscription: (0, sinon_1.fake)(),
            deleteAllSubscriptions: (0, sinon_1.fake)(),
        },
    };
    // Avoid printing logs during tests
    config.logger = {
        debug: (0, sinon_1.fake)(),
        info: (0, sinon_1.fake)(),
        warn: (0, sinon_1.fake)(),
        error: (0, sinon_1.fake)(),
    };
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    class TestReadModel {
        constructor() {
            this.id = '∂';
        }
    }
    class SequencedReadModel {
        constructor() {
            this.id = 'π';
        }
    }
    class UserRole {
    }
    // Why sorting by salmon? Salmons are fun! https://youtu.be/dDj7DuHVV9E
    config.readModelSequenceKeys[SequencedReadModel.name] = 'salmon';
    const readModelReader = new booster_read_models_reader_1.BoosterReadModelsReader(config);
    const noopGraphQLOperation = {
        query: '',
    };
    context('requests by Id', () => {
        beforeEach(() => {
            config.readModels[TestReadModel.name] = {
                class: TestReadModel,
                authorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
                properties: [],
                before: [],
            };
            config.readModels[SequencedReadModel.name] = {
                class: SequencedReadModel,
                authorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
                properties: [],
                before: [],
            };
        });
        afterEach(() => {
            delete config.readModels[TestReadModel.name];
            delete config.readModels[SequencedReadModel.name];
        });
        describe('the `validateByIdRequest` function', () => {
            const validateByIdRequest = readModelReader.validateByIdRequest.bind(readModelReader);
            it('throws an invalid parameter error when the version is not present in a request', async () => {
                const emptyReadmodelByIdRequest = {};
                await (0, expect_1.expect)(validateByIdRequest(emptyReadmodelByIdRequest)).to.be.eventually.rejectedWith('"version" was not present');
            });
            it("throws a not found error when it can't find the read model metadata", async () => {
                const readModelByIdRequest = { version: 1, class: { name: 'NonexistentReadModel' } };
                await (0, expect_1.expect)(validateByIdRequest(readModelByIdRequest)).to.be.eventually.rejectedWith(/Could not find read model/);
            });
            it('throws a non authorized error when the current user is not allowed to perform the request', async () => {
                const readModelByIdRequest = { version: 1, class: TestReadModel };
                await (0, expect_1.expect)(validateByIdRequest(readModelByIdRequest)).to.be.eventually.rejectedWith(/Access denied/);
            });
            it('throws an invalid parameter error when the request receives a sequence key but it cannot be found in the Booster metadata', async () => {
                const readModel = {
                    version: 1,
                    class: TestReadModel,
                    currentUser: { id: '666', username: 'root', roles: ['UserRole'], claims: {} },
                    key: {
                        id: 'π',
                        sequenceKey: { name: 'salmon', value: 'sammy' },
                    },
                };
                await (0, expect_1.expect)(validateByIdRequest(readModel)).to.be.eventually.rejectedWith(/Could not find a sort key/);
            });
            it('does not throw an error when there is no sequence key and everything else is ok', async () => {
                const readModel = {
                    version: 1,
                    class: TestReadModel,
                    currentUser: { id: '666', username: 'root', roles: ['UserRole'] },
                };
                await (0, expect_1.expect)(validateByIdRequest(readModel)).to.be.eventually.fulfilled;
            });
            it('does not throw an error when there is a valid sequence key and everything else is ok', async () => {
                const readModel = {
                    version: 1,
                    class: SequencedReadModel,
                    currentUser: { id: '666', username: 'root', roles: ['UserRole'] },
                    key: {
                        id: '§',
                        sequenceKey: { name: 'salmon', value: 'sammy' },
                    },
                };
                await (0, expect_1.expect)(validateByIdRequest(readModel)).to.be.eventually.fulfilled;
            });
        });
        describe('the `findById` method', () => {
            let migratorStub;
            beforeEach(() => {
                config.readModels['SomeReadModel'] = {
                    before: [],
                };
                migratorStub = (0, sinon_1.stub)(read_model_schema_migrator_1.ReadModelSchemaMigrator.prototype, 'migrate');
            });
            afterEach(() => {
                delete config.readModels['SomeReadModel'];
                migratorStub.restore();
            });
            it('validates and uses the searcher to find a read model by id', async () => {
                const fakeValidateByIdRequest = (0, sinon_1.fake)();
                (0, sinon_1.replace)(readModelReader, 'validateByIdRequest', fakeValidateByIdRequest);
                const fakeSearcher = { findById: (0, sinon_1.fake)() };
                (0, sinon_1.replace)(booster_1.Booster, 'readModel', sinon_1.fake.returns(fakeSearcher));
                const currentUser = {
                    id: 'a user',
                };
                const sequenceKey = {
                    name: 'salmon',
                    value: 'sammy',
                };
                const readModelRequestEnvelope = {
                    key: {
                        id: '42',
                        sequenceKey,
                    },
                    class: { name: 'SomeReadModel' },
                    className: 'SomeReadModel',
                    currentUser,
                    version: 1,
                    requestID: 'my request!',
                };
                await readModelReader.findById(readModelRequestEnvelope);
                (0, expect_1.expect)(fakeValidateByIdRequest).to.have.been.calledOnceWith(readModelRequestEnvelope);
                (0, expect_1.expect)(fakeSearcher.findById).to.have.been.calledOnceWith('42');
            });
            it('calls migrate after find a read model by id', async () => {
                const fakeValidateByIdRequest = (0, sinon_1.fake)();
                (0, sinon_1.replace)(readModelReader, 'validateByIdRequest', fakeValidateByIdRequest);
                const fakeSearcher = { findById: sinon_1.fake.returns(new TestReadModel()) };
                (0, sinon_1.replace)(booster_1.Booster, 'readModel', sinon_1.fake.returns(fakeSearcher));
                const readModelRequestEnvelope = {
                    key: {
                        id: '42',
                        sequenceKey: {
                            name: 'salmon',
                            value: 'sammy',
                        },
                    },
                    class: { name: 'TestReadModel' },
                    className: 'TestReadModel',
                    currentUser: {
                        id: 'a user',
                    },
                    version: 1,
                    requestID: 'my request!',
                };
                migratorStub.callsFake(async (readModel, readModelName) => readModel);
                await readModelReader.findById(readModelRequestEnvelope);
                (0, expect_1.expect)(fakeValidateByIdRequest).to.have.been.calledOnceWith(readModelRequestEnvelope);
                (0, expect_1.expect)(fakeSearcher.findById).to.have.been.calledOnceWith('42');
                (0, expect_1.expect)(migratorStub).to.have.been.calledOnce;
            });
            it('call migrate once the read model after find a read model by id and got an Array', async () => {
                const fakeValidateByIdRequest = (0, sinon_1.fake)();
                (0, sinon_1.replace)(readModelReader, 'validateByIdRequest', fakeValidateByIdRequest);
                const fakeSearcher = { findById: sinon_1.fake.returns([new TestReadModel(), new TestReadModel()]) };
                (0, sinon_1.replace)(booster_1.Booster, 'readModel', sinon_1.fake.returns(fakeSearcher));
                const readModelRequestEnvelope = {
                    key: {
                        id: '42',
                        sequenceKey: {
                            name: 'salmon',
                            value: 'sammy',
                        },
                    },
                    class: { name: 'TestReadModel' },
                    className: 'TestReadModel',
                    currentUser: {
                        id: 'a user',
                    },
                    version: 1,
                    requestID: 'my request!',
                };
                migratorStub.callsFake(async (readModel, readModelName) => readModel);
                await readModelReader.findById(readModelRequestEnvelope);
                (0, expect_1.expect)(fakeValidateByIdRequest).to.have.been.calledOnceWith(readModelRequestEnvelope);
                (0, expect_1.expect)(fakeSearcher.findById).to.have.been.calledOnceWith('42');
                (0, expect_1.expect)(migratorStub).to.have.been.calledOnce;
            });
        });
    });
    describe('the validation for methods `search` and `subscribe`', () => {
        beforeEach(() => {
            config.readModels[TestReadModel.name] = {
                class: TestReadModel,
                authorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
                properties: [],
                before: [],
            };
        });
        afterEach(() => {
            delete config.readModels[TestReadModel.name];
        });
        it('throws the right error when request is missing "version"', async () => {
            const envelope = {
                class: { name: 'anyReadModel' },
                requestID: faker_1.random.uuid(),
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
            }; // To avoid the compilation failure of "missing version field"
            await (0, expect_1.expect)(readModelReader.search(envelope)).to.eventually.be.rejectedWith(framework_types_1.InvalidParameterError);
            await (0, expect_1.expect)(readModelReader.subscribe(envelope.requestID, envelope, noopGraphQLOperation)).to.eventually.be.rejectedWith(framework_types_1.InvalidParameterError);
        });
        it('throws the right error when the read model does not exist', async () => {
            const envelope = {
                class: { name: 'nonExistentReadModel' },
                filters: {},
                requestID: faker_1.random.uuid(),
                version: 1,
            };
            await (0, expect_1.expect)(readModelReader.search(envelope)).to.eventually.be.rejectedWith(framework_types_1.NotFoundError);
            await (0, expect_1.expect)(readModelReader.subscribe(envelope.requestID.toString(), envelope, noopGraphQLOperation)).to.eventually.be.rejectedWith(framework_types_1.NotFoundError);
        });
        it('throws the right error when the user is not authorized', async () => {
            const envelope = {
                class: TestReadModel,
                className: TestReadModel.name,
                requestID: faker_1.random.uuid(),
                filters: {},
                version: 1,
                currentUser: {
                    username: faker_1.internet.email(),
                    roles: [''],
                    claims: {},
                },
            };
            await (0, expect_1.expect)(readModelReader.search(envelope)).to.eventually.be.rejectedWith(framework_types_1.NotAuthorizedError);
            await (0, expect_1.expect)(readModelReader.subscribe(envelope.requestID.toString(), envelope, noopGraphQLOperation)).to.eventually.be.rejectedWith(framework_types_1.NotAuthorizedError);
        });
    });
    context("The logic of 'search' and 'subscribe'  methods", () => {
        const filters = {
            id: {
                operation: 'eq',
                values: [faker_1.random.alphaNumeric(5)],
            },
            field: {
                operation: 'lt',
                values: [faker_1.random.number(10)],
            },
        };
        const currentUser = {
            username: faker_1.internet.email(),
            roles: [UserRole.name],
            claims: {},
        };
        const readModelRequestEnvelope = {
            class: TestReadModel,
            className: TestReadModel.name,
            requestID: faker_1.random.uuid(),
            version: 1,
            filters,
            currentUser,
        };
        const beforeFn = async (request) => {
            return Promise.resolve({ ...request, filters: { id: { eq: request.filters.id } } });
        };
        const beforeFnV2 = async (request) => {
            var _a;
            return Promise.resolve({ ...request, filters: { id: { eq: (_a = request.currentUser) === null || _a === void 0 ? void 0 : _a.username } } });
        };
        describe('the "search" method', () => {
            let migratorStub;
            beforeEach(() => {
                config.readModels[TestReadModel.name] = {
                    class: TestReadModel,
                    authorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
                    properties: [],
                    before: [],
                };
                migratorStub = (0, sinon_1.stub)(read_model_schema_migrator_1.ReadModelSchemaMigrator.prototype, 'migrate');
            });
            afterEach(() => {
                delete config.readModels[TestReadModel.name];
                migratorStub.restore();
            });
            it('calls the provider search function and returns its results', async () => {
                const expectedReadModels = [new TestReadModel(), new TestReadModel()];
                const providerSearcherFunctionFake = sinon_1.fake.returns(expectedReadModels);
                (0, sinon_1.replace)(config.provider.readModels, 'search', providerSearcherFunctionFake);
                (0, sinon_1.replace)(booster_1.Booster, 'config', config); // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`
                migratorStub.callsFake(async (readModel, readModelName) => readModel);
                const result = await readModelReader.search(readModelRequestEnvelope);
                (0, expect_1.expect)(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(sinon_1.match.any, TestReadModel.name, filters, {}, undefined, undefined, false, undefined);
                (0, expect_1.expect)(result).to.be.deep.equal(expectedReadModels);
            });
            it('calls migrates after search a read model with a simple array', async () => {
                const expectedReadModels = [new TestReadModel(), new TestReadModel()];
                const providerSearcherFunctionFake = sinon_1.fake.returns(expectedReadModels);
                (0, sinon_1.replace)(config.provider.readModels, 'search', providerSearcherFunctionFake);
                (0, sinon_1.replace)(booster_1.Booster, 'config', config); // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`
                migratorStub.callsFake(async (readModel, readModelName) => readModel);
                const result = await readModelReader.search(readModelRequestEnvelope);
                (0, expect_1.expect)(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(sinon_1.match.any, TestReadModel.name, filters, {}, undefined, undefined, false, undefined);
                (0, expect_1.expect)(result).to.be.deep.equal(expectedReadModels);
                (0, expect_1.expect)(migratorStub).to.have.been.calledTwice;
            });
            it('calls migrates once after paginated search a read model', async () => {
                const expectedReadModels = [new TestReadModel(), new TestReadModel()];
                const searchResult = {
                    items: expectedReadModels,
                    count: 2,
                    cursor: {},
                };
                const providerSearcherFunctionFake = sinon_1.fake.returns(searchResult);
                (0, sinon_1.replace)(config.provider.readModels, 'search', providerSearcherFunctionFake);
                (0, sinon_1.replace)(booster_1.Booster, 'config', config); // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`
                migratorStub.callsFake(async (readModel, readModelName) => readModel);
                readModelRequestEnvelope.paginatedVersion = true;
                const result = await readModelReader.search(readModelRequestEnvelope);
                (0, expect_1.expect)(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(sinon_1.match.any, TestReadModel.name, filters, {}, undefined, undefined, true, undefined);
                (0, expect_1.expect)(result).to.be.deep.equal(searchResult);
                (0, expect_1.expect)(migratorStub).to.have.been.calledTwice;
            });
            context('when there are projections fields', () => {
                it('calls the provider search function with the right parameters', async () => {
                    const readModelWithProjectionRequestEnvelope = {
                        class: TestReadModel,
                        className: TestReadModel.name,
                        requestID: faker_1.random.uuid(),
                        version: 1,
                        filters,
                        currentUser,
                        select: ['id'],
                    };
                    const expectedReadModels = [new TestReadModel(), new TestReadModel()];
                    const providerSearcherFunctionFake = sinon_1.fake.returns(expectedReadModels);
                    (0, sinon_1.replace)(config.provider.readModels, 'search', providerSearcherFunctionFake);
                    (0, sinon_1.replace)(booster_1.Booster, 'config', config); // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`
                    migratorStub.callsFake(async (readModel, readModelName) => readModel);
                    const result = await readModelReader.search(readModelWithProjectionRequestEnvelope);
                    (0, expect_1.expect)(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(sinon_1.match.any, TestReadModel.name, filters, {}, undefined, undefined, false, ['id']);
                    (0, expect_1.expect)(result).to.be.deep.equal(expectedReadModels);
                });
                it('do not  call migrates if select is set', async () => {
                    const readModelWithProjectionRequestEnvelope = {
                        class: TestReadModel,
                        className: TestReadModel.name,
                        requestID: faker_1.random.uuid(),
                        version: 1,
                        filters,
                        currentUser,
                        select: ['id'],
                    };
                    const expectedResult = [new TestReadModel(), new TestReadModel()];
                    const providerSearcherFunctionFake = sinon_1.fake.returns(expectedResult);
                    (0, sinon_1.replace)(config.provider.readModels, 'search', providerSearcherFunctionFake);
                    (0, sinon_1.replace)(booster_1.Booster, 'config', config); // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`
                    migratorStub.callsFake(async (readModel, readModelName) => readModel);
                    const result = await readModelReader.search(readModelWithProjectionRequestEnvelope);
                    (0, expect_1.expect)(providerSearcherFunctionFake).to.have.been.calledOnceWithExactly(sinon_1.match.any, TestReadModel.name, filters, {}, undefined, undefined, false, ['id']);
                    (0, expect_1.expect)(result).to.be.deep.equal(expectedResult);
                    (0, expect_1.expect)(migratorStub).to.have.not.been.called;
                });
            });
            context('when there is only one before hook function', () => {
                const fakeBeforeFn = (0, sinon_1.fake)(beforeFn);
                beforeEach(() => {
                    const providerSearcherFunctionFake = sinon_1.fake.returns([]);
                    config.readModels[TestReadModel.name] = {
                        class: TestReadModel,
                        authorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
                        properties: [],
                        before: [fakeBeforeFn],
                    };
                    (0, sinon_1.replace)(booster_1.Booster, 'config', config); // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`
                    (0, sinon_1.replace)(config.provider.readModels, 'search', providerSearcherFunctionFake);
                });
                afterEach(() => {
                    delete config.readModels[TestReadModel.name];
                });
                it('calls the before hook function', async () => {
                    await readModelReader.search(readModelRequestEnvelope);
                    const currentUser = readModelRequestEnvelope.currentUser;
                    (0, expect_1.expect)(fakeBeforeFn).to.have.been.calledOnceWithExactly(readModelRequestEnvelope, currentUser);
                    const expectedReturn = {
                        ...readModelRequestEnvelope,
                        filters: { id: { eq: readModelRequestEnvelope.filters.id } },
                    };
                    await (0, expect_1.expect)(fakeBeforeFn.getCall(0).returnValue).to.have.eventually.become(expectedReturn);
                });
            });
            context('when there are more than one before hook functions', () => {
                const beforeFnSpy = (0, sinon_1.fake)(beforeFn);
                const beforeFnV2Spy = (0, sinon_1.fake)(beforeFnV2);
                beforeEach(() => {
                    const providerSearcherFunctionFake = sinon_1.fake.returns([]);
                    config.readModels[TestReadModel.name] = {
                        class: TestReadModel,
                        authorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
                        properties: [],
                        before: [beforeFnSpy, beforeFnV2Spy],
                    };
                    (0, sinon_1.replace)(booster_1.Booster, 'config', config); // Needed because the function `Booster.readModel` references `this.config` from `searchFunction`
                    (0, sinon_1.replace)(config.provider.readModels, 'search', providerSearcherFunctionFake);
                });
                afterEach(() => {
                    delete config.readModels[TestReadModel.name];
                });
                it('chains the before hook functions when there is more than one', async () => {
                    var _a;
                    await readModelReader.search(readModelRequestEnvelope);
                    (0, expect_1.expect)(beforeFnSpy).to.have.been.calledOnceWithExactly(readModelRequestEnvelope, readModelRequestEnvelope.currentUser);
                    const expectedReturn = {
                        ...readModelRequestEnvelope,
                        filters: { id: { eq: readModelRequestEnvelope.filters.id } },
                    };
                    await (0, expect_1.expect)(beforeFnSpy.getCall(0).returnValue).to.have.eventually.become(expectedReturn);
                    const returnedEnvelope = await beforeFnSpy.returnValues[0];
                    (0, expect_1.expect)(beforeFnV2Spy).to.have.been.calledAfter(beforeFnSpy);
                    (0, expect_1.expect)(beforeFnV2Spy).to.have.been.calledOnceWithExactly(returnedEnvelope, returnedEnvelope.currentUser);
                    const expectedReturnEnvelope = {
                        ...returnedEnvelope,
                        filters: { id: { eq: (_a = returnedEnvelope.currentUser) === null || _a === void 0 ? void 0 : _a.username } },
                    };
                    await (0, expect_1.expect)(beforeFnV2Spy.getCall(0).returnValue).to.have.eventually.become(expectedReturnEnvelope);
                });
            });
        });
        describe('the "subscribe" method', () => {
            context('with no before hooks defined', () => {
                const providerSubscribeFunctionFake = (0, sinon_1.fake)();
                beforeEach(() => {
                    config.readModels[TestReadModel.name] = {
                        class: TestReadModel,
                        authorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
                        properties: [],
                        before: [],
                    };
                    (0, sinon_1.replace)(config.provider.readModels, 'subscribe', providerSubscribeFunctionFake);
                });
                it('calls the provider subscribe function and returns its results', async () => {
                    const connectionID = faker_1.random.uuid();
                    const expectedSubscriptionEnvelope = {
                        ...readModelRequestEnvelope,
                        connectionID,
                        operation: noopGraphQLOperation,
                        expirationTime: 1,
                    };
                    await readModelReader.subscribe(connectionID, readModelRequestEnvelope, noopGraphQLOperation);
                    (0, expect_1.expect)(providerSubscribeFunctionFake).to.have.been.calledOnce;
                    const gotSubscriptionEnvelope = providerSubscribeFunctionFake.getCall(0).lastArg;
                    (0, expect_1.expect)(gotSubscriptionEnvelope).to.include.keys('expirationTime');
                    gotSubscriptionEnvelope.expirationTime = expectedSubscriptionEnvelope.expirationTime; // We don't care now about the value
                    (0, expect_1.expect)(gotSubscriptionEnvelope).to.be.deep.equal(expectedSubscriptionEnvelope);
                });
            });
            context('with before hooks', () => {
                const providerSubscribeFunctionFake = (0, sinon_1.fake)();
                beforeEach(() => {
                    config.readModels[TestReadModel.name] = {
                        class: TestReadModel,
                        authorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, [UserRole]),
                        properties: [],
                        before: [beforeFn, beforeFnV2],
                    };
                    (0, sinon_1.replace)(config.provider.readModels, 'subscribe', providerSubscribeFunctionFake);
                });
                it('calls the provider subscribe function when setting before hooks and returns the new filter in the result', async () => {
                    const connectionID = faker_1.random.uuid();
                    readModelRequestEnvelope.filters = { id: { eq: currentUser === null || currentUser === void 0 ? void 0 : currentUser.username } };
                    const expectedSubscriptionEnvelope = {
                        ...readModelRequestEnvelope,
                        connectionID,
                        operation: noopGraphQLOperation,
                        expirationTime: 1,
                    };
                    await readModelReader.subscribe(connectionID, readModelRequestEnvelope, noopGraphQLOperation);
                    (0, expect_1.expect)(providerSubscribeFunctionFake).to.have.been.calledOnce;
                    const gotSubscriptionEnvelope = providerSubscribeFunctionFake.getCall(0).lastArg;
                    (0, expect_1.expect)(gotSubscriptionEnvelope).to.include.keys('expirationTime');
                    gotSubscriptionEnvelope.expirationTime = expectedSubscriptionEnvelope.expirationTime; // We don't care now about the value
                    (0, expect_1.expect)(gotSubscriptionEnvelope).to.be.deep.equal(expectedSubscriptionEnvelope);
                });
            });
        });
    });
    describe("The 'unsubscribe' method", () => {
        it('calls the provider "deleteSubscription" method with the right data', async () => {
            const deleteSubscriptionFake = (0, sinon_1.fake)();
            (0, sinon_1.replace)(config.provider.readModels, 'deleteSubscription', deleteSubscriptionFake);
            const connectionID = faker_1.random.uuid();
            const subscriptionID = faker_1.random.uuid();
            await readModelReader.unsubscribe(connectionID, subscriptionID);
            (0, expect_1.expect)(deleteSubscriptionFake).to.have.been.calledOnceWithExactly(sinon_1.match.any, connectionID, subscriptionID);
        });
    });
    describe("The 'unsubscribeAll' method", () => {
        it('calls the provider "deleteAllSubscription" method with the right data', async () => {
            const deleteAllSubscriptionsFake = (0, sinon_1.fake)();
            (0, sinon_1.replace)(config.provider.readModels, 'deleteAllSubscriptions', deleteAllSubscriptionsFake);
            const connectionID = faker_1.random.uuid();
            await readModelReader.unsubscribeAll(connectionID);
            (0, expect_1.expect)(deleteAllSubscriptionsFake).to.have.been.calledOnceWithExactly(sinon_1.match.any, connectionID);
        });
    });
});
