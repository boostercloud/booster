"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const framework_provider_azure_infrastructure_1 = require("@boostercloud/framework-provider-azure-infrastructure");
const app_helper_1 = require("../../../helper/app-helper");
const expect_1 = require("../../../helper/expect");
describe('After nuke', () => {
    describe('the resource group', () => {
        it('is deleted successfully', async () => {
            const environmentName = (0, app_helper_1.checkAndGetCurrentEnv)();
            await (0, expect_1.expect)(framework_provider_azure_infrastructure_1.AzureTestHelper.checkResourceGroup((0, app_helper_1.applicationName)(), environmentName)).to.be.eventually.rejectedWith('ResourceGroupNotFound');
        });
    });
});
