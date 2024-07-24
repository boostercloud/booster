"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/no-unused-vars */
const framework_types_1 = require("@boostercloud/framework-types");
const src_1 = require("../../../src");
const expect_1 = require("../../expect");
const sinon_1 = require("sinon");
describe('the `Trace` decorator', async () => {
    afterEach(() => {
        const booster = src_1.Booster;
        delete booster.config.traceConfiguration;
    });
    context('When a method is called', async () => {
        it('Injects the correct `this` to the traced method', async () => {
            src_1.Booster.config.traceConfiguration = {
                enableTraceNotification: true,
                includeInternal: false,
                onStart: CustomTracer.onStart,
                onEnd: CustomTracer.onEnd,
            };
            const testClass = new TestClass();
            await testClass.myCustomMethod('test');
            (0, expect_1.expect)(testClass.innerField).to.be.eq('test');
        });
        it('onStart and onEnd methods are called in the expected order', async () => {
            const executedMethods = [];
            (0, sinon_1.stub)(CustomTracer, 'onStart').callsFake(async (_config, _actionType, _traceInfo) => {
                executedMethods.push('onStart');
            });
            (0, sinon_1.stub)(CustomTracer, 'onEnd').callsFake(async (_config, _actionType, _traceInfo) => {
                executedMethods.push('onEnd');
            });
            src_1.Booster.config.traceConfiguration = {
                enableTraceNotification: true,
                includeInternal: false,
                onStart: CustomTracer.onStart,
                onEnd: CustomTracer.onEnd,
            };
            const testClass = new TestClass();
            await testClass.myCustomMethod('test');
            (0, expect_1.expect)(executedMethods).to.have.same.members(['onStart', 'onEnd']);
        });
    });
});
class TestClass {
    constructor() {
        this.innerField = '';
    }
    async myCustomMethod(param) {
        this.innerField = param;
    }
}
tslib_1.__decorate([
    (0, src_1.Trace)(framework_types_1.TraceActionTypes.CUSTOM)
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
], TestClass.prototype, "myCustomMethod", null);
class CustomTracer {
    static async onStart(_config, _actionType, _traceInfo) { }
    static async onEnd(_config, _actionType, _traceInfo) { }
}
