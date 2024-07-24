"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.checkAndGetCurrentEnv = exports.setEnv = exports.getProviderTestHelper = exports.applicationName = void 0;
const framework_provider_aws_infrastructure_1 = require("@boostercloud/framework-provider-aws-infrastructure");
const framework_provider_azure_infrastructure_1 = require("@boostercloud/framework-provider-azure-infrastructure");
const framework_provider_local_infrastructure_1 = require("@boostercloud/framework-provider-local-infrastructure");
const util = require("util");
const exec = util.promisify(require('child_process').exec);
function applicationName() {
    return `my-store-${process.env.BOOSTER_APP_SUFFIX}`;
}
exports.applicationName = applicationName;
async function getProviderTestHelper() {
    const provider = process.env.TESTED_PROVIDER;
    const environmentName = checkAndGetCurrentEnv();
    const providerHelpers = {
        AWS: () => framework_provider_aws_infrastructure_1.AWSTestHelper.build(applicationName()),
        AZURE: () => framework_provider_azure_infrastructure_1.AzureTestHelper.build(applicationName(), environmentName),
        LOCAL: () => framework_provider_local_infrastructure_1.LocalTestHelper.build(applicationName()),
    };
    const supportedProviders = Object.keys(providerHelpers);
    if (!provider || !supportedProviders.includes(provider)) {
        throw new Error(`Invalid provider to run tests. Environment variable TESTED_PROVIDER is ${provider} and the supported ones are [${supportedProviders}]`);
    }
    return providerHelpers[provider]();
}
exports.getProviderTestHelper = getProviderTestHelper;
async function setEnv() {
    if (!process.env.BOOSTER_APP_SUFFIX) {
        // If the user doesn't set an app name suffix, use the current git commit hash
        // to build a unique suffix for the application name to avoid collisions
        // between tests from different branches.
        const { stdout } = await exec('git rev-parse HEAD');
        process.env['BOOSTER_APP_SUFFIX'] = stdout.trim().substring(0, 7);
        console.log('setting BOOSTER_APP_SUFFIX=' + process.env.BOOSTER_APP_SUFFIX);
    }
}
exports.setEnv = setEnv;
function checkAndGetCurrentEnv() {
    const env = process.env.BOOSTER_ENV;
    if (!env || env.trim().length == 0) {
        throw new Error('Booster environment is missing. You need to provide an environment to configure your Booster project');
    }
    return env;
}
exports.checkAndGetCurrentEnv = checkAndGetCurrentEnv;
