"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const cli_helper_1 = require("../../../helper/cli-helper");
const sleep_1 = require("../../../helper/sleep");
const file_helper_1 = require("../../../helper/file-helper");
const deps_helper_1 = require("../../../helper/deps-helper");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../../cli/src/common/sandbox");
const app_helper_1 = require("../../../helper/app-helper");
const framework_provider_aws_infrastructure_1 = require("@boostercloud/framework-provider-aws-infrastructure");
before(async () => {
    await (0, app_helper_1.setEnv)();
    const configuredAssets = ['assets', 'assetFile.txt'];
    const sandboxedProject = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('deploy'), configuredAssets);
    await (0, deps_helper_1.overrideWithBoosterLocalDependencies)(sandboxedProject);
    framework_provider_aws_infrastructure_1.AWSTestHelper.ensureAWSConfiguration();
    await (0, cli_helper_1.deploy)(sandboxedProject);
    console.log('Waiting 30 seconds after deployment to let the stack finish its initialization...');
    await (0, sleep_1.sleep)(30000);
    console.log('...sleep finished. Let the tests begin 🔥!');
});
