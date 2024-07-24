"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.makeTestProcess = void 0;
const process_1 = require("../../../src/services/process");
const sinon_1 = require("sinon");
const effect_1 = require("@boostercloud/application-tester/src/effect");
const makeTestProcess = (overrides) => (0, effect_1.fakeService)(process_1.ProcessService, {
    cwd: sinon_1.fake.returns(''),
    exec: sinon_1.fake.returns(''),
    ...overrides,
});
exports.makeTestProcess = makeTestProcess;
