"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const booster_event_dispatcher_1 = require("../src/booster-event-dispatcher");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const expect_1 = require("./expect");
const raw_events_parser_1 = require("../src/services/raw-events-parser");
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
describe('BoosterEventDispatcher', () => {
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
    const rawEvents = [{ some: 'raw event' }, { some: 'other raw event' }];
    const events = [{ some: 'raw event' }, { some: 'other raw event' }];
    const fakeRawToEnvelopes = sinon_1.fake.returns(events);
    config.provider = {
        events: {
            rawToEnvelopes: fakeRawToEnvelopes,
        },
    };
    context('with a configured provider', () => {
        describe('the `dispatch` method', () => {
            it('calls the raw events parser once and processes all messages', async () => {
                (0, sinon_1.replace)(raw_events_parser_1.RawEventsParser, 'streamPerEntityEvents', (0, sinon_1.fake)());
                await booster_event_dispatcher_1.BoosterEventDispatcher.dispatch(rawEvents, config);
                (0, expect_1.expect)(raw_events_parser_1.RawEventsParser.streamPerEntityEvents).to.have.been.calledWithMatch(config, events, booster_event_processor_1.BoosterEventProcessor.eventProcessor);
            });
            it('logs and ignores errors thrown by `streamPerEntityEvents`', async () => {
                var _a;
                const error = new Error('some error');
                (0, sinon_1.replace)(raw_events_parser_1.RawEventsParser, 'streamPerEntityEvents', sinon_1.fake.rejects(error));
                const rawEvents = [{ some: 'raw event' }, { some: 'other raw event' }];
                await (0, expect_1.expect)(booster_event_dispatcher_1.BoosterEventDispatcher.dispatch(rawEvents, config)).not.to.be.rejected;
                (0, expect_1.expect)((_a = config.logger) === null || _a === void 0 ? void 0 : _a.error).to.have.been.calledWith('[Booster]|BoosterEventDispatcher#dispatch: ', 'Unhandled error while dispatching event: ', error);
            });
        });
    });
});
