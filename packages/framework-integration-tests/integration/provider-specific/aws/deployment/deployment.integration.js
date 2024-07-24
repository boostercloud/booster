"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const framework_provider_aws_infrastructure_1 = require("@boostercloud/framework-provider-aws-infrastructure");
const app_helper_1 = require("../../../helper/app-helper");
const expect_1 = require("../../../helper/expect");
describe('After deployment', () => {
    describe('the stack', () => {
        it('has been created successfully', async () => {
            // The project must have been deployed by the cliHelper hook in setup.ts
            // that scripts uses the cli to do the deployment, so we just check here
            // that the Cloudformation was run by AWS successfully. For that, we can just
            // build the AWSHelper. It will throw if the AWS stack is not ready.
            await (0, expect_1.expect)(framework_provider_aws_infrastructure_1.AWSTestHelper.build((0, app_helper_1.applicationName)())).to.be.eventually.fulfilled;
        });
    });
});
