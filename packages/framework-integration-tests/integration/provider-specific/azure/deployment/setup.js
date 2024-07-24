"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const cli_helper_1 = require("../../../helper/cli-helper");
const file_helper_1 = require("../../../helper/file-helper");
const deps_helper_1 = require("../../../helper/deps-helper");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../../cli/src/common/sandbox");
const app_helper_1 = require("../../../helper/app-helper");
const framework_provider_azure_infrastructure_1 = require("@boostercloud/framework-provider-azure-infrastructure");
before(async () => {
    await (0, app_helper_1.setEnv)();
    const configuredAssets = ['assets', 'assetFile.txt', 'host.json'];
    const sandboxedProject = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('deploy'), configuredAssets);
    await (0, deps_helper_1.overrideWithBoosterLocalDependencies)(sandboxedProject);
    framework_provider_azure_infrastructure_1.AzureTestHelper.ensureAzureConfiguration();
    console.log('Deploying sandbox project...');
    await (0, cli_helper_1.deploy)(sandboxedProject, 'azure');
});
