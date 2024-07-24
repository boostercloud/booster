"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-unused-vars */
const expect_1 = require("../expect");
const decorators_1 = require("../../src/decorators");
const src_1 = require("../../src");
const decorators_2 = require("../../src/decorators");
describe('the `EventHandler` decorator', () => {
    afterEach(() => {
        src_1.Booster.configureCurrentEnv((config) => {
            for (const propName in config.eventHandlers) {
                delete config.eventHandlers[propName];
            }
        });
    });
    it('registers the event handler class as an event handler in Booster configuration', () => {
        let SomeEvent = class SomeEvent {
            entityID() {
                return '123';
            }
        };
        SomeEvent = tslib_1.__decorate([
            decorators_2.Event
        ], SomeEvent);
        let SomeEventHandler = class SomeEventHandler {
            static handle(_event, _register) {
                return Promise.resolve();
            }
        };
        SomeEventHandler = tslib_1.__decorate([
            (0, decorators_1.EventHandler)(SomeEvent)
        ], SomeEventHandler);
        const booster = src_1.Booster;
        const someEventHandlers = booster.config.eventHandlers['SomeEvent'];
        (0, expect_1.expect)(someEventHandlers).to.be.an('Array');
        (0, expect_1.expect)(someEventHandlers).to.contain(SomeEventHandler);
    });
});
