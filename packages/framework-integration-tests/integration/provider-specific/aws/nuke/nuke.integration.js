"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const framework_provider_aws_infrastructure_1 = require("@boostercloud/framework-provider-aws-infrastructure");
const app_helper_1 = require("../../../helper/app-helper");
const expect_1 = require("../../../helper/expect");
describe('After nuke', () => {
    describe('the stack', () => {
        it('is deleted successfully', async () => {
            await (0, expect_1.expect)(framework_provider_aws_infrastructure_1.AWSTestHelper.build((0, app_helper_1.applicationName)())).to.be.eventually.rejectedWith(new RegExp(`Stack with id ${(0, app_helper_1.applicationName)()}[^\\s]+ does not exist`));
        });
    });
});
