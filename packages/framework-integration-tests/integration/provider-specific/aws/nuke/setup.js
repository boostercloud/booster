"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../../cli/src/common/sandbox");
const file_helper_1 = require("../../../helper/file-helper");
const app_helper_1 = require("../../../helper/app-helper");
const framework_provider_aws_infrastructure_1 = require("@boostercloud/framework-provider-aws-infrastructure");
const cli_helper_1 = require("../../../helper/cli-helper");
const deps_helper_1 = require("../../../helper/deps-helper");
before(async () => {
    await (0, app_helper_1.setEnv)();
    const sandboxPath = (0, file_helper_1.sandboxPathFor)('nuke');
    const configuredAssets = ['assets', 'assetFile.txt'];
    const sandboxedProject = (0, sandbox_1.createSandboxProject)(sandboxPath, configuredAssets);
    await (0, deps_helper_1.overrideWithBoosterLocalDependencies)(sandboxedProject);
    framework_provider_aws_infrastructure_1.AWSTestHelper.ensureAWSConfiguration();
    await (0, cli_helper_1.nuke)(sandboxedProject);
    (0, sandbox_1.removeSandboxProject)(sandboxPath);
});
