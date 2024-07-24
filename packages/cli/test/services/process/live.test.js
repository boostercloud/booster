"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const process = require("process");
const childProcessPromise = require("child-process-promise");
const sinon_1 = require("sinon");
const effect_1 = require("@boostercloud/framework-types/dist/effect");
const live_impl_1 = require("../../../src/services/process/live.impl");
const expect_1 = require("../../expect");
const errors_1 = require("../../../src/common/errors");
const process_1 = require("../../../src/services/process");
describe('Process - Live Implementation', () => {
    beforeEach(() => {
        (0, sinon_1.replace)(process, 'cwd', sinon_1.fake.returns(''));
        (0, sinon_1.replace)(childProcessPromise, 'exec', sinon_1.fake.resolves({}));
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    const mapEffError = (effect) => (0, effect_1.pipe)(effect, (0, effect_1.mapError)((e) => e.error));
    it('uses process.cwd', async () => {
        const effect = (0, effect_1.gen)(function* ($) {
            const { cwd } = yield* $(process_1.ProcessService);
            return yield* $(cwd());
        });
        await (0, effect_1.unsafeRunEffect)(mapEffError(effect), {
            layer: live_impl_1.LiveProcess,
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(process.cwd).to.have.been.called;
    });
    it('uses child-process-promise.exec', async () => {
        const command = 'command';
        const cwd = 'cwd';
        const effect = (0, effect_1.gen)(function* ($) {
            const { exec } = yield* $(process_1.ProcessService);
            return yield* $(exec(command, cwd));
        });
        await (0, effect_1.unsafeRunEffect)(mapEffError(effect), {
            layer: live_impl_1.LiveProcess,
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(childProcessPromise.exec).to.have.been.calledWith(command, { cwd });
    });
});
