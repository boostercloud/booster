"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("./expect");
const src_1 = require("../src/");
const sinon_1 = require("sinon");
const booster_event_dispatcher_1 = require("../src/booster-event-dispatcher");
const booster_graphql_dispatcher_1 = require("../src/booster-graphql-dispatcher");
const booster_scheduled_command_dispatcher_1 = require("../src/booster-scheduled-command-dispatcher");
const booster_subscribers_notifier_1 = require("../src/booster-subscribers-notifier");
const booster_rocket_dispatcher_1 = require("../src/booster-rocket-dispatcher");
const booster_event_stream_consumer_1 = require("../src/booster-event-stream-consumer");
const booster_event_stream_producer_1 = require("../src/booster-event-stream-producer");
const sensor_1 = require("../src/sensor");
describe('framework-core package', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    context('`boosterEventDispatcher` function', () => {
        it('calls the `dispatch` method of the `BoosterEventDispatcher` class', async () => {
            const fakeDispatch = sinon_1.fake.resolves(undefined);
            const fakeRawEvents = { some: 'events' };
            (0, sinon_1.replace)(booster_event_dispatcher_1.BoosterEventDispatcher, 'dispatch', fakeDispatch);
            await (0, src_1.boosterEventDispatcher)(fakeRawEvents);
            (0, expect_1.expect)(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawEvents, src_1.Booster.config);
        });
    });
    context('`boosterServeGraphQL` function', () => {
        it('calls the `dispatch` method of the `BoosterGraphQLDispatcher` class', async () => {
            const fakeDispatch = sinon_1.fake.resolves(undefined);
            const fakeRawRequest = { some: 'request' };
            (0, sinon_1.replace)(booster_graphql_dispatcher_1.BoosterGraphQLDispatcher.prototype, 'dispatch', fakeDispatch);
            await (0, src_1.boosterServeGraphQL)(fakeRawRequest);
            (0, expect_1.expect)(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawRequest);
        });
    });
    context('`boosterTriggerScheduledCommands` function', () => {
        it('calls the `dispatch` method of the `BoosterScheduledCommandDispatcher` class', async () => {
            const fakeDispatch = sinon_1.fake.resolves(undefined);
            const fakeRawRequest = { some: 'request' };
            (0, sinon_1.replace)(booster_scheduled_command_dispatcher_1.BoosterScheduledCommandDispatcher.prototype, 'dispatch', fakeDispatch);
            await (0, src_1.boosterTriggerScheduledCommands)(fakeRawRequest);
            (0, expect_1.expect)(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawRequest);
        });
    });
    context('`boosterNotifySubscribers` function', () => {
        it('calls the `dispatch` method of the `BoosterSubscribersNotifier` class', async () => {
            const fakeDispatch = sinon_1.fake.resolves(undefined);
            const fakeRawRequest = { some: 'request' };
            (0, sinon_1.replace)(booster_subscribers_notifier_1.BoosterSubscribersNotifier.prototype, 'dispatch', fakeDispatch);
            await (0, src_1.boosterNotifySubscribers)(fakeRawRequest);
            (0, expect_1.expect)(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawRequest);
        });
    });
    context('`boosterRocketDispatcher` function', () => {
        it('calls the `dispatch` method of the `BoosterRocketDispatcher` class', async () => {
            const fakeDispatch = sinon_1.fake.resolves(undefined);
            const fakeRawRequest = { some: 'request' };
            (0, sinon_1.replace)(booster_rocket_dispatcher_1.BoosterRocketDispatcher.prototype, 'dispatch', fakeDispatch);
            await (0, src_1.boosterRocketDispatcher)(fakeRawRequest);
            (0, expect_1.expect)(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawRequest);
        });
    });
    context('`boosterConsumeEventStream` function', () => {
        it('calls the `consume` method of the `BoosterEventStreamConsumer` class', async () => {
            const fakeConsume = sinon_1.fake.resolves(undefined);
            const fakeRawEvent = { some: 'event' };
            (0, sinon_1.replace)(booster_event_stream_consumer_1.BoosterEventStreamConsumer, 'consume', fakeConsume);
            await (0, src_1.boosterConsumeEventStream)(fakeRawEvent);
            (0, expect_1.expect)(fakeConsume).to.have.been.calledOnceWithExactly(fakeRawEvent, src_1.Booster.config);
        });
    });
    context('`boosterProduceEventStream` function', () => {
        it('calls the `produce` method of the `BoosterEventStreamProducer` class', async () => {
            const fakeProduce = sinon_1.fake.resolves(undefined);
            const fakeRawEvent = { some: 'event' };
            (0, sinon_1.replace)(booster_event_stream_producer_1.BoosterEventStreamProducer, 'produce', fakeProduce);
            await (0, src_1.boosterProduceEventStream)(fakeRawEvent);
            (0, expect_1.expect)(fakeProduce).to.have.been.calledOnceWithExactly(fakeRawEvent, src_1.Booster.config);
        });
    });
    context('`boosterHealth` function', () => {
        it('calls the `boosterHealth` method of the `BoosterHealthService` class', async () => {
            const fakeHealth = sinon_1.fake.resolves(undefined);
            const fakeRawRequest = { some: 'request' };
            (0, sinon_1.replace)(sensor_1.BoosterHealthService.prototype, 'boosterHealth', fakeHealth);
            await (0, src_1.boosterHealth)(fakeRawRequest);
            (0, expect_1.expect)(fakeHealth).to.have.been.calledOnceWithExactly(fakeRawRequest);
        });
    });
});
