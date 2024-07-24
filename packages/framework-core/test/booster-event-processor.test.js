"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const expect_1 = require("./expect");
const read_model_store_1 = require("../src/services/read-model-store");
const event_store_1 = require("../src/services/event-store");
const booster_register_handler_1 = require("../src/booster-register-handler");
const faker_1 = require("faker");
const booster_event_processor_1 = require("../src/booster-event-processor");
class SomeEvent {
    constructor(id) {
        this.id = id;
    }
    entityID() {
        return this.id;
    }
    getPrefixedId(prefix) {
        return `${prefix}-${this.id}`;
    }
}
class SomeNotification {
    constructor() { }
}
class AnEventHandler {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    static async handle(event, register) {
        event.getPrefixedId('prefix');
    }
}
const someEvent = {
    version: 1,
    kind: 'event',
    superKind: 'domain',
    entityID: '42',
    entityTypeName: 'SomeEntity',
    value: {
        entityID: () => {
            return '42';
        },
        id: '42',
    },
    requestID: '123',
    typeName: SomeEvent.name,
};
const someNotification = {
    version: 1,
    kind: 'event',
    superKind: 'domain',
    entityID: 'default',
    entityTypeName: 'defaultTopic',
    value: {},
    requestID: '123',
    typeName: SomeNotification.name,
};
const someEntity = {
    id: '42',
};
const someEntitySnapshot = {
    version: 1,
    kind: 'snapshot',
    superKind: 'domain',
    entityID: '42',
    entityTypeName: 'SomeEntity',
    value: someEntity,
    requestID: '234',
    typeName: 'SomeEntity',
    createdAt: 'an uncertain future',
    persistedAt: 'a few nanoseconds later',
    snapshottedEventCreatedAt: 'an uncertain future',
};
describe('BoosterEventProcessor', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    const config = new framework_types_1.BoosterConfig('test');
    config.provider = {};
    config.events[SomeEvent.name] = { class: SomeEvent };
    config.notifications[SomeNotification.name] = { class: SomeNotification };
    config.logger = {
        info: (0, sinon_1.fake)(),
        error: (0, sinon_1.fake)(),
        debug: (0, sinon_1.fake)(),
        warn: (0, sinon_1.fake)(),
    };
    context('with a configured provider', () => {
        describe('the `eventProcessor` method', () => {
            it('waits for the snapshot generation process and read model update process to complete', async () => {
                const stubEventStore = (0, sinon_1.createStubInstance)(event_store_1.EventStore);
                const stubReadModelStore = (0, sinon_1.createStubInstance)(read_model_store_1.ReadModelStore);
                const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                (0, sinon_1.replace)(boosterEventProcessor, 'snapshotAndUpdateReadModels', (0, sinon_1.fake)());
                (0, sinon_1.replace)(boosterEventProcessor, 'dispatchEntityEventsToEventHandlers', (0, sinon_1.fake)());
                const callback = boosterEventProcessor.eventProcessor(stubEventStore, stubReadModelStore);
                await callback(someEvent.entityTypeName, someEvent.entityID, [someEvent], config);
                (0, expect_1.expect)(boosterEventProcessor.snapshotAndUpdateReadModels).to.have.been.calledOnceWith(config, someEvent.entityTypeName, someEvent.entityID, stubEventStore, stubReadModelStore);
            });
            it('waits for the event to be handled by the event handlers', async () => {
                const stubEventStore = (0, sinon_1.createStubInstance)(event_store_1.EventStore);
                const stubReadModelStore = (0, sinon_1.createStubInstance)(read_model_store_1.ReadModelStore);
                const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                const fakeFilterDispatched = sinon_1.fake.returns([someEvent]);
                (0, sinon_1.replace)(boosterEventProcessor, 'filterDispatched', fakeFilterDispatched);
                (0, sinon_1.replace)(boosterEventProcessor, 'snapshotAndUpdateReadModels', (0, sinon_1.fake)());
                (0, sinon_1.replace)(boosterEventProcessor, 'dispatchEntityEventsToEventHandlers', (0, sinon_1.fake)());
                const callback = boosterEventProcessor.eventProcessor(stubEventStore, stubReadModelStore);
                await callback(someEvent.entityTypeName, someEvent.entityID, [someEvent], config);
                (0, expect_1.expect)(boosterEventProcessor.dispatchEntityEventsToEventHandlers).to.have.been.calledOnceWith([someEvent], config);
            });
            it("doesn't call snapshotAndUpdateReadModels if the entity name is in config.topicToEvent", async () => {
                const stubEventStore = (0, sinon_1.createStubInstance)(event_store_1.EventStore);
                const stubReadModelStore = (0, sinon_1.createStubInstance)(read_model_store_1.ReadModelStore);
                const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                (0, sinon_1.replace)(boosterEventProcessor, 'snapshotAndUpdateReadModels', (0, sinon_1.fake)());
                (0, sinon_1.replace)(boosterEventProcessor, 'dispatchEntityEventsToEventHandlers', (0, sinon_1.fake)());
                const overriddenConfig = { ...config };
                overriddenConfig.topicToEvent = { [someEvent.entityTypeName]: 'SomeEvent' };
                const callback = boosterEventProcessor.eventProcessor(stubEventStore, stubReadModelStore);
                await callback(someEvent.entityTypeName, someEvent.entityID, [someEvent], overriddenConfig);
                overriddenConfig.topicToEvent = {};
                (0, expect_1.expect)(boosterEventProcessor.snapshotAndUpdateReadModels).not.to.have.been.called;
            });
        });
        describe('the `snapshotAndUpdateReadModels` method', () => {
            it('gets the updated state for the event entity', async () => {
                const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                const eventStore = (0, sinon_1.createStubInstance)(event_store_1.EventStore);
                const readModelStore = (0, sinon_1.createStubInstance)(read_model_store_1.ReadModelStore);
                eventStore.fetchEntitySnapshot = sinon_1.fake.resolves({});
                await boosterEventProcessor.snapshotAndUpdateReadModels(config, someEvent.entityTypeName, someEvent.entityID, eventStore, readModelStore);
                (0, expect_1.expect)(eventStore.fetchEntitySnapshot).to.have.been.called;
                (0, expect_1.expect)(eventStore.fetchEntitySnapshot).to.have.been.calledOnceWith(someEvent.entityTypeName, someEvent.entityID);
            });
            it('projects the entity state to the corresponding read models', async () => {
                const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                const eventStore = (0, sinon_1.createStubInstance)(event_store_1.EventStore);
                eventStore.fetchEntitySnapshot = sinon_1.fake.resolves(someEntitySnapshot);
                const readModelStore = (0, sinon_1.createStubInstance)(read_model_store_1.ReadModelStore);
                await boosterEventProcessor.snapshotAndUpdateReadModels(config, someEvent.entityTypeName, someEvent.entityID, eventStore, readModelStore);
                (0, expect_1.expect)(readModelStore.project).to.have.been.calledOnce;
                (0, expect_1.expect)(readModelStore.project).to.have.been.calledWith(someEntitySnapshot);
            });
            context('when the entity reduction fails', () => {
                it('logs the error, does not throw it, and the projects method is not called', async () => {
                    var _a;
                    const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                    const eventStore = (0, sinon_1.createStubInstance)(event_store_1.EventStore);
                    const readModelStore = (0, sinon_1.createStubInstance)(read_model_store_1.ReadModelStore);
                    const error = new Error('some error');
                    eventStore.fetchEntitySnapshot = sinon_1.fake.rejects(error);
                    await (0, expect_1.expect)(boosterEventProcessor.snapshotAndUpdateReadModels(config, someEvent.entityTypeName, someEvent.entityID, eventStore, readModelStore)).to.be.eventually.fulfilled;
                    (0, expect_1.expect)(readModelStore.project).not.to.have.been.called;
                    (0, expect_1.expect)((_a = config.logger) === null || _a === void 0 ? void 0 : _a.error).to.have.been.calledWith('[Booster]|BoosterEventDispatcher#snapshotAndUpdateReadModels: ', 'Error while fetching or reducing entity snapshot:', error);
                });
            });
        });
        describe('the `dispatchEntityEventsToEventHandlers` method', () => {
            afterEach(() => {
                config.eventHandlers[SomeEvent.name] = [];
            });
            it('does nothing and does not throw if there are no event handlers', async () => {
                (0, sinon_1.replace)(booster_register_handler_1.RegisterHandler, 'handle', (0, sinon_1.fake)());
                const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                // We try first with null array of event handlers
                config.eventHandlers[SomeEvent.name] = null;
                await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config);
                // And now with an empty array
                config.eventHandlers[SomeEvent.name] = [];
                await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config);
                // It should not throw any errors
            });
            it('calls all the handlers for the current event', async () => {
                const fakeHandler1 = (0, sinon_1.fake)();
                const fakeHandler2 = (0, sinon_1.fake)();
                config.eventHandlers[SomeEvent.name] = [{ handle: fakeHandler1 }, { handle: fakeHandler2 }];
                (0, sinon_1.replace)(booster_register_handler_1.RegisterHandler, 'handle', (0, sinon_1.fake)());
                const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config);
                const eventValue = someEvent.value;
                const anEventInstance = new SomeEvent(eventValue.id);
                anEventInstance.entityID = eventValue.entityID;
                (0, expect_1.expect)(fakeHandler1).to.have.been.calledOnceWith(anEventInstance);
                (0, expect_1.expect)(fakeHandler2).to.have.been.calledOnceWith(anEventInstance);
            });
            it('calls all the handlers, even if the event is stored in the notifications field instead of the events one', async () => {
                const fakeHandler1 = (0, sinon_1.fake)();
                const fakeHandler2 = (0, sinon_1.fake)();
                config.eventHandlers[SomeNotification.name] = [{ handle: fakeHandler1 }, { handle: fakeHandler2 }];
                (0, sinon_1.replace)(booster_register_handler_1.RegisterHandler, 'handle', (0, sinon_1.fake)());
                const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someNotification], config);
                const aNotificationInstance = new SomeNotification();
                (0, expect_1.expect)(fakeHandler1).to.have.been.calledOnceWith(aNotificationInstance);
                (0, expect_1.expect)(fakeHandler2).to.have.been.calledOnceWith(aNotificationInstance);
            });
            it('calls the register handler for all the published events', async () => {
                let capturedRegister1 = {};
                let capturedRegister2 = {};
                const fakeHandler1 = (0, sinon_1.fake)((event, register) => {
                    capturedRegister1 = register;
                });
                const fakeHandler2 = (0, sinon_1.fake)((event, register) => {
                    capturedRegister2 = register;
                });
                config.eventHandlers[SomeEvent.name] = [{ handle: fakeHandler1 }, { handle: fakeHandler2 }];
                (0, sinon_1.replace)(booster_register_handler_1.RegisterHandler, 'handle', (0, sinon_1.fake)());
                const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config);
                (0, expect_1.expect)(booster_register_handler_1.RegisterHandler.handle).to.have.been.calledTwice;
                (0, expect_1.expect)(booster_register_handler_1.RegisterHandler.handle).to.have.been.calledWith(config, capturedRegister1);
                (0, expect_1.expect)(booster_register_handler_1.RegisterHandler.handle).to.have.been.calledWith(config, capturedRegister2);
            });
            it('waits for async event handlers to finish', async () => {
                let capturedRegister = new framework_types_1.Register(faker_1.random.uuid(), {}, booster_register_handler_1.RegisterHandler.flush);
                const fakeHandler = (0, sinon_1.fake)(async (event, register) => {
                    await new Promise((resolve) => setTimeout(resolve, 100));
                    register.events(someEvent.value);
                    capturedRegister = register;
                });
                config.eventHandlers[SomeEvent.name] = [{ handle: fakeHandler }];
                (0, sinon_1.replace)(booster_register_handler_1.RegisterHandler, 'handle', (0, sinon_1.fake)());
                const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config);
                (0, expect_1.expect)(booster_register_handler_1.RegisterHandler.handle).to.have.been.calledWith(config, capturedRegister);
                (0, expect_1.expect)(capturedRegister.eventList[0]).to.be.deep.equal(someEvent.value);
            });
        });
        describe('the `filterDispatched` method', () => {
            it("removes events if they've been already dispatched", async () => {
                var _a;
                const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
                const eventStore = (0, sinon_1.createStubInstance)(event_store_1.EventStore);
                const someEventEnvelope = { ...someEvent, id: 'event-id' };
                eventStore.storeDispatchedEvent = sinon_1.fake.returns(false);
                const eventsNotDispatched = await boosterEventProcessor.filterDispatched(config, [someEventEnvelope], eventStore);
                (0, expect_1.expect)(eventStore.storeDispatchedEvent).to.have.been.called;
                (0, expect_1.expect)(eventStore.storeDispatchedEvent).to.have.been.calledOnceWith(someEventEnvelope);
                (0, expect_1.expect)(eventsNotDispatched).to.deep.equal([]);
                (0, expect_1.expect)((_a = config.logger) === null || _a === void 0 ? void 0 : _a.warn).to.have.been.calledWith('[Booster]|BoosterEventDispatcher#filterDispatched: ', 'Event has already been dispatched. Skipping.', sinon_1.match.any);
            });
        });
        it('calls an instance method in the event and it is executed without failing', async () => {
            config.eventHandlers[SomeEvent.name] = [{ handle: AnEventHandler.handle }];
            const boosterEventProcessor = booster_event_processor_1.BoosterEventProcessor;
            const getPrefixedIdFake = (0, sinon_1.fake)();
            (0, sinon_1.replace)(SomeEvent.prototype, 'getPrefixedId', getPrefixedIdFake);
            await boosterEventProcessor.dispatchEntityEventsToEventHandlers([someEvent], config);
            (0, expect_1.expect)(getPrefixedIdFake).to.have.been.called;
        });
    });
});
