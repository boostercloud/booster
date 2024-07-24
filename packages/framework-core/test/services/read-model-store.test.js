"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const mocha_1 = require("mocha");
const sinon_1 = require("sinon");
const read_model_store_1 = require("../../src/services/read-model-store");
const framework_common_helpers_1 = require("@boostercloud/framework-common-helpers");
const framework_types_1 = require("@boostercloud/framework-types");
const expect_1 = require("../expect");
const booster_authorizer_1 = require("../../src/booster-authorizer");
(0, mocha_1.describe)('ReadModelStore', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    const testConfig = new framework_types_1.BoosterConfig('Test');
    testConfig.logLevel = framework_types_1.Level.error;
    class AnImportantEntity {
        constructor(id, someKey, count) {
            this.id = id;
            this.someKey = someKey;
            this.count = count;
        }
        getPrefixedKey(prefix) {
            return `${prefix}-${this.someKey}`;
        }
    }
    class AnImportantEntityWithArray {
        constructor(id, someKey, count) {
            this.id = id;
            this.someKey = someKey;
            this.count = count;
        }
        getPrefixedKey(prefix) {
            return `${prefix}-${this.someKey.join('-')}`;
        }
    }
    class AnEntity {
        constructor(id, someKey, count) {
            this.id = id;
            this.someKey = someKey;
            this.count = count;
        }
    }
    class SomeReadModel {
        constructor(id) {
            this.id = id;
        }
        static someObserver(entity, obj) {
            const count = ((obj === null || obj === void 0 ? void 0 : obj.count) || 0) + entity.count;
            return { id: entity.someKey, kind: 'some', count: count };
        }
        static someObserverArray(entity, readModelID, obj) {
            const count = ((obj === null || obj === void 0 ? void 0 : obj.count) || 0) + entity.count;
            return { id: readModelID, kind: 'some', count: count };
        }
        getId() {
            return this.id;
        }
        static projectionThatCallsReadModelMethod(entity, currentReadModel) {
            currentReadModel.getId();
            return framework_types_1.ReadModelAction.Nothing;
        }
        static projectionThatCallsEntityMethod(entity, 
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        currentReadModel) {
            entity.getPrefixedKey('a prefix');
            return framework_types_1.ReadModelAction.Nothing;
        }
    }
    class AnotherReadModel {
        constructor(id) {
            this.id = id;
        }
        static anotherObserver(entity, obj) {
            const count = ((obj === null || obj === void 0 ? void 0 : obj.count) || 0) + entity.count;
            return { id: entity.someKey, kind: 'another', count: count };
        }
    }
    const config = new framework_types_1.BoosterConfig('test');
    config.provider = {
        readModels: {
            store: () => { },
            delete: () => { },
            fetch: () => { },
        },
    };
    config.entities[AnImportantEntity.name] = {
        class: AnImportantEntity,
        eventStreamAuthorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, []),
    };
    config.entities[AnEntity.name] = {
        class: AnEntity,
        eventStreamAuthorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, []),
    };
    config.entities[AnImportantEntityWithArray.name] = {
        class: AnImportantEntityWithArray,
        eventStreamAuthorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, []),
    };
    config.readModels[SomeReadModel.name] = {
        class: SomeReadModel,
        authorizer: booster_authorizer_1.BoosterAuthorizer.allowAccess,
        properties: [],
        before: [],
    };
    config.readModels[AnotherReadModel.name] = {
        class: AnotherReadModel,
        authorizer: booster_authorizer_1.BoosterAuthorizer.allowAccess,
        properties: [],
        before: [],
    };
    config.projections[AnImportantEntity.name] = [
        {
            class: SomeReadModel,
            methodName: 'someObserver',
            joinKey: 'someKey',
        },
        {
            class: SomeReadModel,
            methodName: 'projectionThatCallsEntityMethod',
            joinKey: 'someKey',
        },
        {
            class: AnotherReadModel,
            methodName: 'anotherObserver',
            joinKey: 'someKey',
        },
    ];
    config.projections[AnImportantEntityWithArray.name] = [
        {
            class: SomeReadModel,
            methodName: 'someObserverArray',
            joinKey: 'someKey',
        },
    ];
    config.projections['AnEntity'] = [
        {
            class: SomeReadModel,
            methodName: 'projectionThatCallsReadModelMethod',
            joinKey: 'someKey',
        },
    ];
    function entitySnapshotEnvelopeFor(entityName) {
        let someKeyValue = 'joinColumnID';
        if (AnImportantEntityWithArray.name == entityName) {
            someKeyValue = ['joinColumnID', 'anotherJoinColumnID'];
        }
        const snapshottedEventCreatedAtDate = new Date();
        const snapshottedEventCreatedAt = snapshottedEventCreatedAtDate.toISOString();
        return {
            version: 1,
            kind: 'snapshot',
            superKind: 'domain',
            entityID: '42',
            entityTypeName: entityName,
            value: {
                id: 'importantEntityID',
                someKey: someKeyValue,
                count: 123,
            },
            requestID: 'whatever',
            typeName: entityName,
            createdAt: snapshottedEventCreatedAt,
            persistedAt: new Date().toISOString(),
            snapshottedEventCreatedAt,
        };
    }
    (0, mocha_1.describe)('the `project` method', () => {
        context('when the entity class has no projections', () => {
            it('returns without errors and without performing any actions', async () => {
                const entitySnapshotWithNoProjections = {
                    version: 1,
                    kind: 'snapshot',
                    superKind: 'domain',
                    entityID: '42',
                    entityTypeName: 'AConceptWithoutProjections',
                    value: { entityID: () => '42' },
                    requestID: 'whatever',
                    typeName: AnImportantEntity.name,
                    createdAt: new Date().toISOString(),
                    persistedAt: new Date().toISOString(),
                    snapshottedEventCreatedAt: new Date().toISOString(),
                };
                (0, sinon_1.replace)(config.provider.readModels, 'store', (0, sinon_1.fake)());
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                (0, sinon_1.replace)(readModelStore, 'fetchReadModel', sinon_1.fake.returns(null));
                await (0, expect_1.expect)(readModelStore.project(entitySnapshotWithNoProjections)).to.eventually.be.fulfilled;
                (0, expect_1.expect)(config.provider.readModels.store).not.to.have.been.called;
                (0, expect_1.expect)(readModelStore.fetchReadModel).not.to.have.been.called;
            });
        });
        context('when the new read model returns ReadModelAction.Delete', () => {
            it('deletes the associated read model', async () => {
                (0, sinon_1.replace)(config.provider.readModels, 'store', (0, sinon_1.fake)());
                (0, sinon_1.replace)(config.provider.readModels, 'delete', (0, sinon_1.fake)());
                (0, sinon_1.replace)(read_model_store_1.ReadModelStore.prototype, 'projectionFunction', sinon_1.fake.returns(() => framework_types_1.ReadModelAction.Delete));
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntity.name));
                (0, expect_1.expect)(config.provider.readModels.store).not.to.have.been.called;
                (0, expect_1.expect)(config.provider.readModels.delete).to.have.been.calledThrice;
            });
        });
        context('when the new read model returns ReadModelAction.Nothing', () => {
            it('ignores the read model', async () => {
                (0, sinon_1.replace)(config.provider.readModels, 'store', (0, sinon_1.fake)());
                (0, sinon_1.replace)(config.provider.readModels, 'delete', (0, sinon_1.fake)());
                (0, sinon_1.replace)(read_model_store_1.ReadModelStore.prototype, 'projectionFunction', sinon_1.fake.returns(() => framework_types_1.ReadModelAction.Nothing));
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntity.name));
                (0, expect_1.expect)(config.provider.readModels.store).not.to.have.been.called;
                (0, expect_1.expect)(config.provider.readModels.delete).not.to.have.been.called;
            });
        });
        context("when the corresponding read models don't exist", () => {
            let clock;
            before(() => {
                clock = (0, sinon_1.useFakeTimers)(0);
            });
            after(() => {
                clock.restore();
            });
            it('creates new instances of the read models', async () => {
                (0, sinon_1.replace)(config.provider.readModels, 'store', (0, sinon_1.fake)());
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                (0, sinon_1.replace)(readModelStore, 'fetchReadModel', sinon_1.fake.returns(null));
                (0, sinon_1.spy)(SomeReadModel, 'someObserver');
                (0, sinon_1.spy)(AnotherReadModel, 'anotherObserver');
                const entityValue = entitySnapshotEnvelopeFor(AnImportantEntity.name).value;
                const anEntityInstance = new AnImportantEntity(entityValue.id, entityValue.someKey, entityValue.count);
                await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntity.name));
                (0, expect_1.expect)(readModelStore.fetchReadModel).to.have.been.calledThrice;
                (0, expect_1.expect)(readModelStore.fetchReadModel).to.have.been.calledWith(SomeReadModel.name, 'joinColumnID');
                (0, expect_1.expect)(readModelStore.fetchReadModel).to.have.been.calledWith(AnotherReadModel.name, 'joinColumnID');
                (0, expect_1.expect)(SomeReadModel.someObserver).to.have.been.calledOnceWith(anEntityInstance, null);
                (0, expect_1.expect)(SomeReadModel.someObserver).to.have.returned({
                    id: 'joinColumnID',
                    kind: 'some',
                    count: 123,
                    boosterMetadata: {
                        version: 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntity',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'SomeReadModel.someObserver',
                        },
                    },
                });
                (0, expect_1.expect)(AnotherReadModel.anotherObserver).to.have.been.calledOnceWith(anEntityInstance, null);
                (0, expect_1.expect)(AnotherReadModel.anotherObserver).to.have.returned({
                    id: 'joinColumnID',
                    kind: 'another',
                    count: 123,
                    boosterMetadata: {
                        version: 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntity',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'AnotherReadModel.anotherObserver',
                        },
                    },
                });
                (0, expect_1.expect)(config.provider.readModels.store).to.have.been.calledTwice;
                (0, expect_1.expect)(config.provider.readModels.store).to.have.been.calledWith(config, SomeReadModel.name, {
                    id: 'joinColumnID',
                    kind: 'some',
                    count: 123,
                    boosterMetadata: {
                        version: 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntity',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'SomeReadModel.someObserver',
                        },
                    },
                }, 0);
                (0, expect_1.expect)(config.provider.readModels.store).to.have.been.calledWith(config, AnotherReadModel.name, {
                    id: 'joinColumnID',
                    kind: 'another',
                    count: 123,
                    boosterMetadata: {
                        version: 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntity',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'AnotherReadModel.anotherObserver',
                        },
                    },
                }, 0);
            });
        });
        context('when the corresponding read model did exist', () => {
            let clock;
            before(() => {
                clock = (0, sinon_1.useFakeTimers)(0);
            });
            after(() => {
                clock.restore();
            });
            it('updates the read model', async () => {
                (0, sinon_1.replace)(config.provider.readModels, 'store', (0, sinon_1.fake)());
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                const someReadModelStoredVersion = 10;
                const anotherReadModelStoredVersion = 32;
                (0, sinon_1.replace)(readModelStore, 'fetchReadModel', (0, sinon_1.fake)((className, id) => {
                    if (className == SomeReadModel.name) {
                        return {
                            id: id,
                            kind: 'some',
                            count: 77,
                            boosterMetadata: {
                                version: someReadModelStoredVersion,
                                lastUpdateAt: '1970-01-01T00:00:00.000Z',
                                lastProjectionInfo: {
                                    entityId: 'importantEntityID',
                                    entityName: 'AnImportantEntity',
                                    entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                                    projectionMethod: 'SomeReadModel.someObserver',
                                },
                            },
                        };
                    }
                    else {
                        return {
                            id: id,
                            kind: 'another',
                            count: 177,
                            boosterMetadata: {
                                version: anotherReadModelStoredVersion,
                                lastUpdateAt: '1970-01-01T00:00:00.000Z',
                                lastProjectionInfo: {
                                    entityId: 'importantEntityID',
                                    entityName: 'AnImportantEntity',
                                    entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                                    projectionMethod: 'AnotherReadModel.anotherObserver',
                                },
                            },
                        };
                    }
                }));
                (0, sinon_1.spy)(SomeReadModel, 'someObserver');
                (0, sinon_1.spy)(AnotherReadModel, 'anotherObserver');
                const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name);
                const entityValue = anEntitySnapshot.value;
                const anEntityInstance = new AnImportantEntity(entityValue.id, entityValue.someKey, entityValue.count);
                await readModelStore.project(anEntitySnapshot);
                (0, expect_1.expect)(readModelStore.fetchReadModel).to.have.been.calledThrice;
                (0, expect_1.expect)(readModelStore.fetchReadModel).to.have.been.calledWith(SomeReadModel.name, 'joinColumnID');
                (0, expect_1.expect)(readModelStore.fetchReadModel).to.have.been.calledWith(AnotherReadModel.name, 'joinColumnID');
                (0, expect_1.expect)(SomeReadModel.someObserver).to.have.been.calledOnceWith(anEntityInstance, {
                    id: 'joinColumnID',
                    kind: 'some',
                    count: 77,
                    boosterMetadata: {
                        version: someReadModelStoredVersion,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntity',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'SomeReadModel.someObserver',
                        },
                    },
                });
                (0, expect_1.expect)(SomeReadModel.someObserver).to.have.returned({
                    id: 'joinColumnID',
                    kind: 'some',
                    count: 200,
                    boosterMetadata: {
                        version: someReadModelStoredVersion + 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntity',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'SomeReadModel.someObserver',
                        },
                    },
                });
                (0, expect_1.expect)(AnotherReadModel.anotherObserver).to.have.been.calledOnceWith(anEntityInstance, {
                    id: 'joinColumnID',
                    kind: 'another',
                    count: 177,
                    boosterMetadata: {
                        version: anotherReadModelStoredVersion,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntity',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'AnotherReadModel.anotherObserver',
                        },
                    },
                });
                (0, expect_1.expect)(AnotherReadModel.anotherObserver).to.have.returned({
                    id: 'joinColumnID',
                    kind: 'another',
                    count: 300,
                    boosterMetadata: {
                        version: anotherReadModelStoredVersion + 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntity',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'AnotherReadModel.anotherObserver',
                        },
                    },
                });
                (0, expect_1.expect)(config.provider.readModels.store).to.have.been.calledTwice;
                (0, expect_1.expect)(config.provider.readModels.store).to.have.been.calledWith(config, SomeReadModel.name, {
                    id: 'joinColumnID',
                    kind: 'some',
                    count: 200,
                    boosterMetadata: {
                        version: someReadModelStoredVersion + 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntity',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'SomeReadModel.someObserver',
                        },
                    },
                }, someReadModelStoredVersion);
                (0, expect_1.expect)(config.provider.readModels.store).to.have.been.calledWith(config, AnotherReadModel.name, {
                    id: 'joinColumnID',
                    kind: 'another',
                    count: 300,
                    boosterMetadata: {
                        version: anotherReadModelStoredVersion + 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntity',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'AnotherReadModel.anotherObserver',
                        },
                    },
                }, anotherReadModelStoredVersion);
            });
        });
        context('when the projection calls an instance method in the entity', () => {
            it('is executed without failing', async () => {
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                const getPrefixedKeyFake = (0, sinon_1.fake)();
                (0, sinon_1.replace)(AnImportantEntity.prototype, 'getPrefixedKey', getPrefixedKeyFake);
                await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntity.name));
                (0, expect_1.expect)(getPrefixedKeyFake).to.have.been.called;
            });
        });
        context('when the projection calls an instance method in the read model', () => {
            it('is executed without failing', async () => {
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                (0, sinon_1.replace)(config.provider.readModels, 'fetch', sinon_1.fake.returns([{ id: 'joinColumnID', count: 31415 }]));
                const getIdFake = (0, sinon_1.fake)();
                (0, sinon_1.replace)(SomeReadModel.prototype, 'getId', getIdFake);
                await readModelStore.project(entitySnapshotEnvelopeFor(AnEntity.name));
                (0, expect_1.expect)(getIdFake).to.have.been.called;
            });
        });
        context('when there is high contention and optimistic concurrency is needed', () => {
            let clock;
            before(() => {
                clock = (0, sinon_1.useFakeTimers)(0);
            });
            after(() => {
                clock.restore();
            });
            it('retries 5 times when the error OptimisticConcurrencyUnexpectedVersionError happens 4 times', async () => {
                let tryNumber = 1;
                const expectedTries = 5;
                const fakeStore = (0, sinon_1.fake)((config, readModelName) => {
                    if (readModelName === SomeReadModel.name && tryNumber < expectedTries) {
                        tryNumber++;
                        throw new framework_types_1.OptimisticConcurrencyUnexpectedVersionError('test error');
                    }
                    return Promise.resolve();
                });
                (0, sinon_1.replace)(config.provider.readModels, 'store', fakeStore);
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntity.name));
                const someReadModelStoreCalls = fakeStore.getCalls().filter((call) => call.args[1] === SomeReadModel.name);
                (0, expect_1.expect)(someReadModelStoreCalls).to.be.have.length(expectedTries);
                someReadModelStoreCalls.forEach((call) => {
                    (0, expect_1.expect)(call.args).to.be.deep.equal([
                        config,
                        SomeReadModel.name,
                        {
                            id: 'joinColumnID',
                            kind: 'some',
                            count: 123,
                            boosterMetadata: {
                                version: 1,
                                schemaVersion: 1,
                                lastUpdateAt: '1970-01-01T00:00:00.000Z',
                                lastProjectionInfo: {
                                    entityId: 'importantEntityID',
                                    entityName: 'AnImportantEntity',
                                    entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                                    projectionMethod: 'SomeReadModel.someObserver',
                                },
                            },
                        },
                        0,
                    ]);
                });
            });
        });
        context('when multiple read models are projected from Array joinKey', () => {
            let clock;
            before(() => {
                clock = (0, sinon_1.useFakeTimers)(0);
            });
            after(() => {
                clock.restore();
            });
            it('creates non-existent read models and updates existing read models', async () => {
                (0, sinon_1.replace)(config.provider.readModels, 'store', (0, sinon_1.fake)());
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                const someReadModelStoredVersion = 10;
                (0, sinon_1.replace)(readModelStore, 'fetchReadModel', (0, sinon_1.fake)((className, id) => {
                    if (className == SomeReadModel.name) {
                        if (id == 'anotherJoinColumnID') {
                            return null;
                        }
                        else {
                            return {
                                id: id,
                                kind: 'some',
                                count: 77,
                                boosterMetadata: {
                                    version: someReadModelStoredVersion,
                                    lastUpdateAt: '1970-01-01T00:00:00.000Z',
                                    lastProjectionInfo: {
                                        entityId: 'importantEntityID',
                                        entityName: 'AnImportantEntityWithArray',
                                        entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                                        projectionMethod: 'SomeReadModel.someObserverArray',
                                    },
                                },
                            };
                        }
                    }
                    return null;
                }));
                (0, sinon_1.spy)(SomeReadModel, 'someObserver');
                (0, sinon_1.spy)(SomeReadModel, 'someObserverArray');
                const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntityWithArray.name);
                const entityValue = anEntitySnapshot.value;
                const anEntityInstance = new AnImportantEntityWithArray(entityValue.id, entityValue.someKey, entityValue.count);
                await readModelStore.project(anEntitySnapshot);
                (0, expect_1.expect)(readModelStore.fetchReadModel).to.have.been.calledTwice;
                (0, expect_1.expect)(readModelStore.fetchReadModel).to.have.been.calledWith(SomeReadModel.name, 'joinColumnID');
                (0, expect_1.expect)(readModelStore.fetchReadModel).to.have.been.calledWith(SomeReadModel.name, 'anotherJoinColumnID');
                (0, expect_1.expect)(SomeReadModel.someObserverArray).to.have.been.calledWithMatch(anEntityInstance, 'joinColumnID', {
                    id: 'joinColumnID',
                    kind: 'some',
                    count: 77,
                    boosterMetadata: {
                        version: someReadModelStoredVersion,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntityWithArray',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'SomeReadModel.someObserverArray',
                        },
                    },
                });
                (0, expect_1.expect)(SomeReadModel.someObserverArray).to.have.returned({
                    id: 'joinColumnID',
                    kind: 'some',
                    count: 200,
                    boosterMetadata: {
                        version: someReadModelStoredVersion + 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntityWithArray',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'SomeReadModel.someObserverArray',
                        },
                    },
                });
                (0, expect_1.expect)(SomeReadModel.someObserverArray).to.have.been.calledWithMatch(anEntityInstance, 'anotherJoinColumnID', null);
                (0, expect_1.expect)(SomeReadModel.someObserverArray).to.have.returned({
                    id: 'anotherJoinColumnID',
                    kind: 'some',
                    count: 123,
                    boosterMetadata: {
                        version: 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntityWithArray',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'SomeReadModel.someObserverArray',
                        },
                    },
                });
                (0, expect_1.expect)(config.provider.readModels.store).to.have.been.calledTwice;
                (0, expect_1.expect)(config.provider.readModels.store).to.have.been.calledWith(config, SomeReadModel.name, {
                    id: 'joinColumnID',
                    kind: 'some',
                    count: 200,
                    boosterMetadata: {
                        version: someReadModelStoredVersion + 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntityWithArray',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'SomeReadModel.someObserverArray',
                        },
                    },
                }, someReadModelStoredVersion);
                (0, expect_1.expect)(config.provider.readModels.store).to.have.been.calledWith(config, SomeReadModel.name, {
                    id: 'anotherJoinColumnID',
                    kind: 'some',
                    count: 123,
                    boosterMetadata: {
                        version: 1,
                        schemaVersion: 1,
                        lastUpdateAt: '1970-01-01T00:00:00.000Z',
                        lastProjectionInfo: {
                            entityId: 'importantEntityID',
                            entityName: 'AnImportantEntityWithArray',
                            entityUpdatedAt: '1970-01-01T00:00:00.000Z',
                            projectionMethod: 'SomeReadModel.someObserverArray',
                        },
                    },
                }, 0);
            });
        });
        context('when there is high contention and optimistic concurrency is needed for Array joinKey projections', () => {
            it('The retries are independent for all Read Models in the array, retries 5 times when the error OptimisticConcurrencyUnexpectedVersionError happens 4 times', async () => {
                let tryNumber = 1;
                const expectedAnotherJoinColumnIDTries = 5;
                const expectedJoinColumnIDTries = 1;
                const fakeStore = (0, sinon_1.fake)((config, readModelName, readModel) => {
                    if (readModelName === SomeReadModel.name) {
                        if (readModel.id == 'anotherJoinColumnID' && tryNumber < expectedAnotherJoinColumnIDTries) {
                            tryNumber++;
                            throw new framework_types_1.OptimisticConcurrencyUnexpectedVersionError('test error');
                        }
                    }
                    return Promise.resolve();
                });
                (0, sinon_1.replace)(config.provider.readModels, 'store', fakeStore);
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                await readModelStore.project(entitySnapshotEnvelopeFor(AnImportantEntityWithArray.name));
                const someReadModelStoreCalls = fakeStore.getCalls().filter((call) => call.args[1] === SomeReadModel.name);
                (0, expect_1.expect)(someReadModelStoreCalls).to.be.have.length(expectedJoinColumnIDTries + expectedAnotherJoinColumnIDTries);
                someReadModelStoreCalls
                    .filter((call) => call.args[3].id == 'joinColumnID')
                    .forEach((call) => {
                    (0, expect_1.expect)(call.args).to.be.deep.equal([
                        config,
                        SomeReadModel.name,
                        {
                            id: 'joinColumnID',
                            kind: 'some',
                            count: 123,
                            boosterMetadata: { version: 1 },
                        },
                        0,
                    ]);
                });
                someReadModelStoreCalls
                    .filter((call) => call.args[3].id == 'anotherJoinColumnID')
                    .forEach((call) => {
                    (0, expect_1.expect)(call.args).to.be.deep.equal([
                        config,
                        SomeReadModel.name,
                        {
                            id: 'anotherJoinColumnID',
                            kind: 'some',
                            count: 123,
                            boosterMetadata: { version: 1 },
                        },
                        0,
                    ]);
                });
            });
        });
        context('for read models with defined sequenceKeys', () => {
            (0, mocha_1.beforeEach)(() => {
                config.readModelSequenceKeys['AnotherReadModel'] = 'count';
            });
            afterEach(() => {
                delete config.readModelSequenceKeys.AnotherReadModel;
            });
            it('applies the projections with the right sequenceMetadata', async () => {
                const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name);
                const anEntityInstance = (0, framework_common_helpers_1.createInstance)(AnImportantEntity, anEntitySnapshot.value);
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                const fakeApplyProjectionToReadModel = (0, sinon_1.fake)();
                (0, sinon_1.replace)(readModelStore, 'applyProjectionToReadModel', fakeApplyProjectionToReadModel);
                await readModelStore.project(anEntitySnapshot);
                (0, expect_1.expect)(fakeApplyProjectionToReadModel).to.have.been.calledThrice;
                for (const projectionMetadata of config.projections[AnImportantEntity.name]) {
                    const readModelClassName = projectionMetadata.class.name;
                    (0, expect_1.expect)(fakeApplyProjectionToReadModel).to.have.been.calledWith(anEntityInstance, projectionMetadata, readModelClassName, anEntityInstance[projectionMetadata.joinKey], readModelClassName === 'AnotherReadModel' ? { name: 'count', value: 123 } : undefined);
                }
            });
        });
    });
    (0, mocha_1.describe)('the `fetchReadModel` method', () => {
        context('with no sequenceMetadata', () => {
            it("returns `undefined` when the read model doesn't exist", async () => {
                (0, sinon_1.replace)(config.provider.readModels, 'fetch', sinon_1.fake.returns(undefined));
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                const result = await readModelStore.fetchReadModel(SomeReadModel.name, 'joinColumnID');
                (0, expect_1.expect)(config.provider.readModels.fetch).to.have.been.calledOnceWithExactly(config, SomeReadModel.name, 'joinColumnID', undefined);
                (0, expect_1.expect)(result).to.be.undefined;
            });
            it("returns `undefined` when the read model doesn't exist and provider returns [undefined]", async () => {
                (0, sinon_1.replace)(config.provider.readModels, 'fetch', sinon_1.fake.returns([undefined]));
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                const result = await readModelStore.fetchReadModel(SomeReadModel.name, 'joinColumnID');
                (0, expect_1.expect)(config.provider.readModels.fetch).to.have.been.calledOnceWithExactly(config, SomeReadModel.name, 'joinColumnID', undefined);
                (0, expect_1.expect)(result).to.be.undefined;
            });
            it('returns an instance of the current read model value when it exists', async () => {
                (0, sinon_1.replace)(config.provider.readModels, 'fetch', sinon_1.fake.returns([{ id: 'joinColumnID' }]));
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                const result = await readModelStore.fetchReadModel(SomeReadModel.name, 'joinColumnID');
                (0, expect_1.expect)(config.provider.readModels.fetch).to.have.been.calledOnceWithExactly(config, SomeReadModel.name, 'joinColumnID', undefined);
                (0, expect_1.expect)(result).to.be.deep.equal(new SomeReadModel('joinColumnID'));
            });
        });
        context('with sequenceMetadata', () => {
            it("calls the provider's fetch method passing the sequenceMetadata object", async () => {
                (0, sinon_1.replace)(config.provider.readModels, 'fetch', sinon_1.fake.returns({ id: 'joinColumnID' }));
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                await readModelStore.fetchReadModel(SomeReadModel.name, 'joinColumnID', {
                    name: 'time',
                    value: 'now!',
                });
                (0, expect_1.expect)(config.provider.readModels.fetch).to.have.been.calledOnceWithExactly(config, SomeReadModel.name, 'joinColumnID', { name: 'time', value: 'now!' });
            });
        });
    });
    (0, mocha_1.describe)('the `joinKeyForProjection` private method', () => {
        context('when the joinKey exists', () => {
            it('returns the joinKey value', () => {
                const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name);
                const anEntityInstance = (0, framework_common_helpers_1.createInstance)(AnImportantEntity, anEntitySnapshot.value);
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                (0, expect_1.expect)(readModelStore.joinKeyForProjection(anEntityInstance, { joinKey: 'someKey' })).to.be.deep.equal([
                    'joinColumnID',
                ]);
            });
        });
        context('when the joinkey does not exist', () => {
            it('should not throw and error an skip', () => {
                const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name);
                const anEntityInstance = (0, framework_common_helpers_1.createInstance)(AnImportantEntity, anEntitySnapshot.value);
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                (0, expect_1.expect)(readModelStore.joinKeyForProjection(anEntityInstance, { joinKey: 'whatever' })).to.be.undefined;
            });
        });
    });
    (0, mocha_1.describe)('the `sequenceKeyForProjection` private method', () => {
        context('when there is no sequence key for the read model in the config', () => {
            it('returns undefined', () => {
                const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name);
                const anEntityInstance = (0, framework_common_helpers_1.createInstance)(AnImportantEntity, anEntitySnapshot.value);
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                (0, expect_1.expect)(readModelStore.sequenceKeyForProjection(anEntityInstance, { class: SomeReadModel })).to.be.undefined;
            });
        });
        context('when there is a sequence key for the read model in the config', () => {
            (0, mocha_1.beforeEach)(() => {
                config.readModelSequenceKeys['AnotherReadModel'] = 'count';
            });
            afterEach(() => {
                delete config.readModelSequenceKeys.AnotherReadModel;
            });
            it('returns a `SequenceMetadata`object with the right sequenceKeyName and sequenceValue values', () => {
                const anEntitySnapshot = entitySnapshotEnvelopeFor(AnImportantEntity.name);
                const anEntityInstance = (0, framework_common_helpers_1.createInstance)(AnImportantEntity, anEntitySnapshot.value);
                const readModelStore = new read_model_store_1.ReadModelStore(config);
                (0, expect_1.expect)(readModelStore.sequenceKeyForProjection(anEntityInstance, { class: AnotherReadModel })).to.be.deep.equal({
                    name: 'count',
                    value: 123,
                });
            });
        });
    });
    // TODO: This method is tested indirectly in the `project` method tests, but it would be nice to have dedicated unit tests for it too
    (0, mocha_1.describe)('the `applyProjectionToReadModel` private method', () => {
        context('when `ReadModelAction.Delete` is returned', () => {
            it('deletes the read model'); // TODO
        });
        context('when `ReadModelAction.Nothing` is returned', () => {
            it('does not update the read model state'); // TODO
        });
        context('with no sequenceMetadata', () => {
            it('calls the `fetchReadodel` method with no sequenceMetadata object'); // TODO
        });
        context('with sequenceMetadata', () => {
            it('calls the `fetchReadModel` method passing the sequenceMetadata object'); // TODO
        });
    });
});
