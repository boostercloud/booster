"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.applicationUnderTest = void 0;
const application_tester_1 = require("@boostercloud/application-tester");
const app_helper_1 = require("../../helper/app-helper");
before(async () => {
    await (0, app_helper_1.setEnv)();
    exports.applicationUnderTest = new application_tester_1.ApplicationTester(await (0, app_helper_1.getProviderTestHelper)());
});
