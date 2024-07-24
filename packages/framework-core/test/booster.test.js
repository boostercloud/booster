"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const expect_1 = require("./expect");
const src_1 = require("../src/");
const sinon_1 = require("sinon");
const importer_1 = require("../src/importer");
const framework_types_1 = require("@boostercloud/framework-types");
const event_store_1 = require("../src/services/event-store");
const faker_1 = require("faker");
const token_verifiers_1 = require("../src/services/token-verifiers");
const mocha_1 = require("mocha");
describe('the `Booster` class', () => {
    (0, mocha_1.afterEach)(() => {
        (0, sinon_1.restore)();
        src_1.Booster.configure('test', (config) => {
            config.appName = '';
            for (const propName in config.commandHandlers) {
                delete config.commandHandlers[propName];
            }
        });
    });
    describe('the `configure` method', () => {
        it('can be used to configure the app', () => {
            const booster = src_1.Booster;
            src_1.Booster.configure('test', (config) => {
                config.appName = 'test-app-name';
                config.providerPackage = 'some-provider';
            });
            src_1.Booster.configure('another-environment', (config) => {
                config.appName = 'this-shouldnt-be-set';
            });
            (0, expect_1.expect)(booster.configuredEnvironments).to.have.lengthOf(2);
            (0, expect_1.expect)(booster.configuredEnvironments).to.include.keys(['test', 'another-environment']);
            (0, expect_1.expect)(booster.config.appName).to.equal('test-app-name');
            (0, expect_1.expect)(booster.config.providerPackage).to.equal('some-provider');
        });
    });
    describe('the `start` method', () => {
        it('imports all the user files', () => {
            const fakeImporter = (0, sinon_1.fake)();
            (0, sinon_1.replace)(importer_1.Importer, 'importUserProjectFiles', fakeImporter);
            src_1.Booster.start('path/to/code');
            (0, expect_1.expect)(fakeImporter).to.have.been.calledOnce;
        });
    });
    describe('the `readModel` method', () => {
        class TestReadModel {
            constructor(id) {
                this.id = id;
            }
            getId() {
                return this.id;
            }
        }
        it('returns a properly configured Searcher', async () => {
            const searcherFunctionFake = sinon_1.fake.resolves([]);
            src_1.Booster.configureCurrentEnv((config) => {
                (0, sinon_1.replaceGetter)(config, 'provider', () => {
                    return {
                        readModels: {
                            search: searcherFunctionFake,
                        },
                    };
                });
            });
            await src_1.Booster.readModel(TestReadModel).search();
            (0, expect_1.expect)(searcherFunctionFake).to.have.been.calledOnceWithExactly(sinon_1.match.any, TestReadModel.name, sinon_1.match.any, {}, undefined, undefined, false, undefined);
        });
        it('has an instance method', async () => {
            const searcherFunctionFake = sinon_1.fake.returns([{ id: '42' }]);
            src_1.Booster.configureCurrentEnv((config) => {
                (0, sinon_1.replaceGetter)(config, 'provider', () => {
                    return {
                        readModels: {
                            search: searcherFunctionFake,
                        },
                    };
                });
            });
            const readModels = (await src_1.Booster.readModel(TestReadModel).search());
            for (const readModel of readModels) {
                (0, expect_1.expect)(readModel.getId()).to.not.throw;
            }
            (0, expect_1.expect)(searcherFunctionFake).to.have.been.calledOnce;
        });
    });
    describe('the `entitiesIDs` method', () => {
        it('has an instance method', async () => {
            const providerSearchEntitiesIds = sinon_1.fake.returns([]);
            src_1.Booster.configureCurrentEnv((config) => {
                config.provider = {
                    events: {
                        searchEntitiesIDs: providerSearchEntitiesIds,
                    },
                };
            });
            await src_1.Booster.entitiesIDs('TestEvent', 1, undefined);
            (0, expect_1.expect)(providerSearchEntitiesIds).to.have.been.calledOnce;
        });
    });
    describe('the `event` method', () => {
        class TestEvent {
            constructor(id) {
                this.id = id;
            }
            entityID() {
                return this.id;
            }
            getId() {
                return this.id;
            }
        }
        class BestEvent {
            constructor(id) {
                this.id = id;
            }
            entityID() {
                return this.id;
            }
            getId() {
                return this.id;
            }
        }
        (0, mocha_1.afterEach)(() => {
            (0, sinon_1.restore)();
            src_1.Booster.configureCurrentEnv((config) => {
                config.appName = '';
                for (const propName in config.events) {
                    delete config.events[propName];
                }
                for (const propName in config.notifications) {
                    delete config.notifications[propName];
                }
            });
        });
        it('has an instance method', async () => {
            const searchResult = [
                {
                    requestID: faker_1.random.uuid(),
                    type: TestEvent.name,
                    entity: faker_1.random.alpha(),
                    entityID: faker_1.random.uuid(),
                    createdAt: faker_1.random.alphaNumeric(),
                    value: {
                        id: '1',
                        entityID: () => framework_types_1.UUID.generate(),
                    },
                },
                {
                    requestID: faker_1.random.uuid(),
                    type: BestEvent.name,
                    entity: faker_1.random.alpha(),
                    entityID: faker_1.random.uuid(),
                    createdAt: faker_1.random.alphaNumeric(),
                    value: {
                        id: '1',
                        entityID: () => framework_types_1.UUID.generate(),
                    },
                },
            ];
            const providerEventsSearch = sinon_1.fake.returns(searchResult);
            src_1.Booster.configureCurrentEnv((config) => {
                config.provider = {
                    events: {
                        search: providerEventsSearch,
                    },
                };
                config.events[TestEvent.name] = { class: TestEvent };
                config.events[BestEvent.name] = { class: BestEvent };
            });
            const eventFilterByType = {
                type: TestEvent.name,
            };
            const events = await src_1.Booster.events(eventFilterByType);
            for (const event of events) {
                let eventValue;
                switch (event.type) {
                    case TestEvent.name:
                        eventValue = event.value;
                        (0, expect_1.expect)(eventValue.getId()).to.not.throw;
                        break;
                    case BestEvent.name:
                        eventValue = event.value;
                        (0, expect_1.expect)(eventValue.getId()).to.not.throw;
                        break;
                    default:
                        break;
                }
            }
            (0, expect_1.expect)(providerEventsSearch).to.have.been.calledOnce;
        });
        it('has a plain object if event class does not exist', async () => {
            const searchResult = [
                {
                    requestID: faker_1.random.uuid(),
                    type: TestEvent.name,
                    entity: faker_1.random.alpha(),
                    entityID: faker_1.random.uuid(),
                    createdAt: faker_1.random.alphaNumeric(),
                    value: {
                        id: '1',
                        entityID: () => framework_types_1.UUID.generate(),
                    },
                },
                {
                    requestID: faker_1.random.uuid(),
                    type: BestEvent.name,
                    entity: faker_1.random.alpha(),
                    entityID: faker_1.random.uuid(),
                    createdAt: faker_1.random.alphaNumeric(),
                    value: {
                        id: '1',
                        entityID: () => framework_types_1.UUID.generate(),
                    },
                },
            ];
            const providerEventsSearch = sinon_1.fake.returns(searchResult);
            src_1.Booster.configureCurrentEnv((config) => {
                config.provider = {
                    events: {
                        search: providerEventsSearch,
                    },
                };
                config.events[TestEvent.name] = { class: TestEvent };
            });
            const eventFilterByType = {
                type: TestEvent.name,
            };
            const events = await src_1.Booster.events(eventFilterByType);
            for (const event of events) {
                let eventValue;
                switch (event.type) {
                    case TestEvent.name:
                        eventValue = event.value;
                        (0, expect_1.expect)(eventValue.getId()).to.not.throw;
                        break;
                    case BestEvent.name:
                        eventValue = event.value;
                        (0, expect_1.expect)(eventValue.getId).to.be.undefined;
                        break;
                    default:
                        break;
                }
            }
            (0, expect_1.expect)(providerEventsSearch).to.have.been.calledOnce;
        });
        it('has a plain object if notification class does not exist', async () => {
            const searchResult = [
                {
                    requestID: faker_1.random.uuid(),
                    type: TestEvent.name,
                    entity: faker_1.random.alpha(),
                    entityID: faker_1.random.uuid(),
                    createdAt: faker_1.random.alphaNumeric(),
                    value: {
                        id: '1',
                        entityID: () => framework_types_1.UUID.generate(),
                    },
                },
            ];
            const providerEventsSearch = sinon_1.fake.returns(searchResult);
            src_1.Booster.configureCurrentEnv((config) => {
                config.provider = {
                    events: {
                        search: providerEventsSearch,
                    },
                };
                config.notifications[TestEvent.name] = { class: TestEvent };
            });
            const eventFilterByType = {
                type: TestEvent.name,
            };
            const events = await src_1.Booster.events(eventFilterByType);
            for (const event of events) {
                let eventValue;
                switch (event.type) {
                    case TestEvent.name:
                        eventValue = event.value;
                        (0, expect_1.expect)(eventValue.getId()).to.not.throw;
                        break;
                    default:
                        break;
                }
            }
            (0, expect_1.expect)(providerEventsSearch).to.have.been.calledOnce;
        });
    });
    describe('The `entity` method', () => {
        context('given a BoosterConfig', () => {
            const config = new framework_types_1.BoosterConfig('test');
            config.provider = {};
            it('the `entity` function calls to the `fetchEntitySnapshot` method in the EventStore', async () => {
                (0, sinon_1.replace)(event_store_1.EventStore.prototype, 'fetchEntitySnapshot', sinon_1.fake.returns({ value: { id: '42' } }));
                class SomeEntity {
                    constructor(id) {
                        this.id = id;
                    }
                }
                const snapshot = await src_1.Booster.entity(SomeEntity, '42');
                (0, expect_1.expect)(snapshot).to.be.deep.equal({ id: '42' });
                (0, expect_1.expect)(event_store_1.EventStore.prototype.fetchEntitySnapshot).to.have.been.calledOnceWith('SomeEntity', '42');
            });
            it('the entity function has an instance method', async () => {
                (0, sinon_1.replace)(event_store_1.EventStore.prototype, 'fetchEntitySnapshot', sinon_1.fake.returns({ id: '42' }));
                class SomeEntity {
                    constructor(id) {
                        this.id = id;
                    }
                    getId() {
                        return this.id;
                    }
                }
                const snapshot = await src_1.Booster.entity(SomeEntity, '42');
                snapshot === null || snapshot === void 0 ? void 0 : snapshot.getId();
                if (snapshot) {
                    (0, expect_1.expect)(snapshot === null || snapshot === void 0 ? void 0 : snapshot.getId()).to.not.throw;
                }
            });
        });
    });
    describe('The `loadTokenVerifierFromEnv` function', () => {
        context('when the JWT_ENV_VARS are set', () => {
            beforeEach(() => {
                process.env.BOOSTER_JWT_ISSUER = 'BOOSTER_JWT_ISSUER_VALUE';
                process.env.BOOSTER_JWKS_URI = 'BOOSTER_JWKS_URI_VALUE';
                process.env.BOOSTER_ROLES_CLAIM = 'BOOSTER_ROLES_CLAIM_VALUE';
            });
            (0, mocha_1.afterEach)(() => {
                delete process.env.BOOSTER_JWT_ISSUER;
                delete process.env.BOOSTER_JWKS_URI;
                delete process.env.BOOSTER_ROLES_CLAIM;
                src_1.Booster.config.tokenVerifiers = [];
            });
            it('does alter the token verifiers config', () => {
                (0, expect_1.expect)(src_1.Booster.config.tokenVerifiers).to.be.empty;
                const booster = src_1.Booster;
                booster.loadTokenVerifierFromEnv();
                const tokenVerifierConfig = src_1.Booster.config.tokenVerifiers;
                (0, expect_1.expect)(tokenVerifierConfig.length).to.be.equal(1);
                (0, expect_1.expect)(tokenVerifierConfig[0]).to.be.an.instanceOf(token_verifiers_1.JwksUriTokenVerifier);
                (0, expect_1.expect)(tokenVerifierConfig[0].issuer).to.be.equal('BOOSTER_JWT_ISSUER_VALUE');
                (0, expect_1.expect)(tokenVerifierConfig[0].jwksUri).to.be.equal('BOOSTER_JWKS_URI_VALUE');
                (0, expect_1.expect)(tokenVerifierConfig[0].rolesClaim).to.be.equal('BOOSTER_ROLES_CLAIM_VALUE');
            });
        });
        context('when the JWT_ENV_VARS are not set', () => {
            it('does not alter the token verifiers config', () => {
                (0, expect_1.expect)(src_1.Booster.config.tokenVerifiers).to.be.empty;
                const booster = src_1.Booster;
                booster.loadTokenVerifierFromEnv();
                (0, expect_1.expect)(src_1.Booster.config.tokenVerifiers).to.be.empty;
            });
        });
    });
});
