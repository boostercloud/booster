"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const framework_core_1 = require("@boostercloud/framework-core");
const framework_provider_aws_1 = require("@boostercloud/framework-provider-aws");
framework_core_1.Booster.configure('production', (config) => {
    config.appName = 'boosted-blog';
    config.provider = framework_provider_aws_1.Provider;
});
framework_core_1.Booster.configure('fake_environment', (config) => {
    config.appName = 'boosted-blog-fake';
    config.provider = framework_provider_aws_1.Provider;
});
