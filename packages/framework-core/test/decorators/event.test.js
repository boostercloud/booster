"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const expect_1 = require("../expect");
const decorators_1 = require("../../src/decorators");
const src_1 = require("../../src");
describe('the `Event` decorator', () => {
    it('add the event class as an event', () => {
        let AnEvent = class AnEvent {
            constructor(foo) {
                this.foo = foo;
            }
            entityID() {
                return '123';
            }
        };
        AnEvent = tslib_1.__decorate([
            decorators_1.Event
        ], AnEvent);
        (0, expect_1.expect)(src_1.Booster.config.events['AnEvent']).to.deep.equal({
            class: AnEvent,
        });
    });
});
