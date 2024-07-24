"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const expect_1 = require("../expect");
const decorators_1 = require("../../src/decorators");
const src_1 = require("../../src");
describe('the `Notification` decorator', () => {
    afterEach(() => {
        src_1.Booster.configureCurrentEnv((config) => {
            config.notifications = {};
            config.topicToEvent = {};
        });
    });
    it('add the event class as an event', () => {
        let ANotification = class ANotification {
            constructor() { }
        };
        ANotification = tslib_1.__decorate([
            (0, decorators_1.Notification)()
        ], ANotification);
        (0, expect_1.expect)(src_1.Booster.config.notifications[ANotification.name]).to.deep.equal({
            class: ANotification,
        });
    });
    it('sets the topic in the config, if specified', () => {
        let ANotification = class ANotification {
            constructor() { }
        };
        ANotification = tslib_1.__decorate([
            (0, decorators_1.Notification)({ topic: 'my-topic' })
        ], ANotification);
        (0, expect_1.expect)(src_1.Booster.config.notifications[ANotification.name]).to.deep.equal({
            class: ANotification,
        });
        (0, expect_1.expect)(src_1.Booster.config.topicToEvent['my-topic']).to.deep.equal(ANotification.name);
    });
    it('sets the partitionKey in the config, if specified', () => {
        let ANotification = class ANotification {
            constructor(key) {
                this.key = key;
            }
        };
        ANotification = tslib_1.__decorate([
            (0, decorators_1.Notification)(),
            tslib_1.__param(0, decorators_1.partitionKey)
        ], ANotification);
        (0, expect_1.expect)(src_1.Booster.config.notifications[ANotification.name]).to.deep.equal({
            class: ANotification,
        });
        (0, expect_1.expect)(src_1.Booster.config.partitionKeys[ANotification.name]).to.equal('key');
    });
});
