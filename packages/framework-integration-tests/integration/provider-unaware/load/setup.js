"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.applicationUnderTest = exports.scriptExecutor = void 0;
const app_helper_1 = require("../../helper/app-helper");
const application_tester_1 = require("@boostercloud/application-tester");
const path = require("path");
const artillery_executor_1 = require("./artillery-executor");
const loadTestsFolder = path.join(__dirname, 'scripts');
before(async () => {
    await (0, app_helper_1.setEnv)();
    const providerTestHelper = await (0, app_helper_1.getProviderTestHelper)();
    exports.scriptExecutor = new artillery_executor_1.ArtilleryExecutor(loadTestsFolder, providerTestHelper);
    await exports.scriptExecutor.ensureDeployed();
    exports.applicationUnderTest = new application_tester_1.ApplicationTester(providerTestHelper);
});
