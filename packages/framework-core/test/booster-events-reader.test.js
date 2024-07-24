"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const framework_types_1 = require("@boostercloud/framework-types");
const sinon_1 = require("sinon");
const faker_1 = require("faker");
const booster_events_reader_1 = require("../src/booster-events-reader");
const expect_1 = require("./expect");
const src_1 = require("../src");
const booster_authorizer_1 = require("../src/booster-authorizer");
describe('BoosterEventsReader', () => {
    class TestEntity {
        constructor() {
            this.id = 'testID';
        }
    }
    class NonRegisteredTestEntity {
        constructor() {
            this.id = 'testID';
        }
    }
    class TestEvent {
        constructor() {
            this.id = 'testId';
        }
        entityID() {
            return this.id;
        }
        getPrefixedId(prefix) {
            return `${prefix}-${this.id}`;
        }
    }
    class TestEventReducedByNonRegisteredEntity {
    }
    class CanReadEventsRole {
    }
    let eventsReader;
    let providerEventsSearch;
    const searchResult = [
        {
            requestID: faker_1.random.uuid(),
            type: TestEvent.name,
            entity: faker_1.random.alpha(),
            entityID: faker_1.random.uuid(),
            createdAt: faker_1.random.alphaNumeric(),
            value: {
                entityID: () => framework_types_1.UUID.generate(),
            },
        },
    ];
    beforeEach(() => {
        const eventStreamAuthorizer = booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, [CanReadEventsRole]);
        src_1.Booster.configureCurrentEnv((config) => {
            providerEventsSearch = sinon_1.fake.returns(searchResult);
            config.provider = {
                events: {
                    search: providerEventsSearch,
                },
            };
            config.entities[TestEntity.name] = {
                class: TestEntity,
                eventStreamAuthorizer,
            };
            config.reducers[TestEvent.name] = {
                class: TestEntity,
                methodName: 'testReducerMethod',
            };
            config.reducers[TestEventReducedByNonRegisteredEntity.name] = {
                class: NonRegisteredTestEntity,
                methodName: 'testReducerMethod',
            };
            config.events[TestEvent.name] = { class: TestEvent };
            eventsReader = new booster_events_reader_1.BoosterEventsReader(config);
        });
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('the validation for the method `fetch` throws the right error when', () => {
        it('is a "byEntity" search and entity metadata is not found', async () => {
            const request = {
                requestID: faker_1.random.uuid(),
                parameters: {
                    entity: 'NonExistingEntity',
                },
            };
            await (0, expect_1.expect)(eventsReader.fetch(request)).to.be.rejectedWith(/Could not find entity metadata for "NonExistingEntity"/);
        });
        it('is a "byType" search and the associated entity is not found', async () => {
            const request = {
                requestID: faker_1.random.uuid(),
                parameters: {
                    type: 'NonExistingEventType',
                },
            };
            await (0, expect_1.expect)(eventsReader.fetch(request)).to.be.rejectedWith(/Could not find the entity associated to event type "NonExistingEventType"/);
        });
        it('is a "byEvent" search and the associated entity metadata is not found', async () => {
            const request = {
                requestID: faker_1.random.uuid(),
                parameters: {
                    type: TestEventReducedByNonRegisteredEntity.name,
                },
            };
            await (0, expect_1.expect)(eventsReader.fetch(request)).to.be.rejectedWith(/Could not find entity metadata for "NonRegisteredTestEntity"/);
        });
        it('is an invalid type of event search: it is not a "byEntity" or a "byType" search', async () => {
            const request = {
                requestID: faker_1.random.uuid(),
                parameters: {},
            };
            await (0, expect_1.expect)(eventsReader.fetch(request)).to.be.rejectedWith(/Invalid event search request/);
        });
        it('is an invalid type of event search: it is both a "byEntity" and a "byType" search', async () => {
            const request = {
                requestID: faker_1.random.uuid(),
                parameters: {
                    entity: TestEntity.name,
                    type: TestEvent.name,
                },
            };
            await (0, expect_1.expect)(eventsReader.fetch(request)).to.be.rejectedWith(/Invalid event search request/);
        });
        it('user has no permissions', async () => {
            const request = {
                currentUser: {
                    roles: ['NonValidRole'],
                    username: faker_1.internet.email(),
                    claims: {},
                },
                requestID: faker_1.random.uuid(),
                parameters: {
                    entity: TestEntity.name,
                },
            };
            await (0, expect_1.expect)(eventsReader.fetch(request)).to.be.rejectedWith(/Access denied/);
        });
    });
    describe("The logic of 'fetch' method", () => {
        context('for a "byEntity" search', () => {
            const request = {
                currentUser: {
                    roles: [CanReadEventsRole.name],
                    username: faker_1.internet.email(),
                    claims: {},
                },
                requestID: faker_1.random.uuid(),
                parameters: {
                    entity: TestEntity.name,
                    from: 'fromTime',
                    to: 'toTime',
                },
            };
            it('calls the provider search function with the right parameters and returns correctly', async () => {
                const result = await eventsReader.fetch(request);
                (0, expect_1.expect)(providerEventsSearch).to.have.been.calledWith(sinon_1.match.any, request.parameters);
                (0, expect_1.expect)(result).to.be.deep.equal(searchResult);
            });
        });
    });
});
