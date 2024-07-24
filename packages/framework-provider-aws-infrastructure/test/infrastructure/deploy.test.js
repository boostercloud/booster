"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-magic-numbers */
const expect_1 = require("../expect");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const deploy_1 = require("../../src/infrastructure/deploy");
const cx_api_1 = require("@aws-cdk/cx-api");
const aws_cdk_1 = require("aws-cdk");
const StackTools = require("../../src/infrastructure/stack-tools");
const testEnvironment = {
    account: 'testAccount',
    region: 'testRegion',
};
const config = new framework_types_1.BoosterConfig('test');
config.logger = {
    info: (0, sinon_1.fake)(),
};
config.userProjectRootPath = '.';
describe('the deployment module', () => {
    beforeEach(() => {
        (0, sinon_1.replace)(aws_cdk_1.SdkProvider.prototype, 'forEnvironment', sinon_1.fake.returns((0, sinon_1.mock)()));
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('the `deploy` method', () => {
        it('logs progress through the passed logger', async () => {
            var _a;
            (0, sinon_1.replace)(StackTools, 'getStackServiceConfiguration', sinon_1.fake.returns({
                environment: testEnvironment,
                cdkToolkit: {
                    bootstrap: (0, sinon_1.fake)(),
                    deploy: (0, sinon_1.fake)(),
                },
            }));
            await (0, deploy_1.deploy)(config);
            (0, expect_1.expect)((_a = config.logger) === null || _a === void 0 ? void 0 : _a.info).to.have.been.called;
        });
        it('throws errors', async () => {
            const errorMessage = 'Testing error';
            (0, sinon_1.replace)(StackTools, 'getStackServiceConfiguration', sinon_1.fake.returns({
                environment: testEnvironment,
                cdkToolkit: {
                    bootstrap: (0, sinon_1.fake)(),
                    deploy: sinon_1.fake.throws(errorMessage),
                },
            }));
            await (0, expect_1.expect)((0, deploy_1.deploy)(config)).to.eventually.be.rejectedWith(errorMessage);
        });
        it('builds the AppStack calling to the `getStackServiceConfiguration`', async () => {
            (0, sinon_1.replace)(StackTools, 'getStackServiceConfiguration', sinon_1.fake.returns({
                environment: testEnvironment,
                cdkToolkit: {
                    bootstrap: (0, sinon_1.fake)(),
                    deploy: (0, sinon_1.fake)(),
                },
            }));
            await (0, deploy_1.deploy)(config);
            (0, expect_1.expect)(StackTools.getStackServiceConfiguration).to.have.been.calledOnceWith(config);
        });
        it('calls the CDK bootstrap with the default environment parameters', async () => {
            const fakeBootstrap = (0, sinon_1.fake)();
            (0, sinon_1.replace)(StackTools, 'getStackServiceConfiguration', sinon_1.fake.returns({
                environment: testEnvironment,
                cdkToolkit: {
                    bootstrap: fakeBootstrap,
                    deploy: (0, sinon_1.fake)(),
                },
            }));
            await (0, deploy_1.deploy)(config);
            (0, expect_1.expect)(fakeBootstrap).to.be.calledOnceWith((0, sinon_1.match)([cx_api_1.EnvironmentUtils.format(testEnvironment.account, testEnvironment.region)]));
        });
        it('calls the CDK bootstrap with the right config parameters', async () => {
            const testAppName = 'testing';
            config.appName = testAppName;
            const fakeBootstrap = (0, sinon_1.fake)();
            (0, sinon_1.replace)(StackTools, 'getStackServiceConfiguration', sinon_1.fake.returns({
                environment: testEnvironment,
                cdkToolkit: {
                    bootstrap: fakeBootstrap,
                    deploy: (0, sinon_1.fake)(),
                },
            }));
            await (0, deploy_1.deploy)(config);
            const appNamePrefixRegExp = new RegExp('^' + testAppName + '-');
            (0, expect_1.expect)(fakeBootstrap).to.have.been.calledOnce;
            (0, expect_1.expect)(fakeBootstrap).to.be.calledWith(sinon_1.match.any, sinon_1.match.any, (0, sinon_1.match)({
                toolkitStackName: (0, sinon_1.match)(appNamePrefixRegExp),
                parameters: {
                    bucketName: (0, sinon_1.match)(appNamePrefixRegExp),
                },
            }));
        });
        context('with rockets', () => {
            it('forwards the rockets to the `getStackServiceConfiguration` method for initialization', async () => {
                (0, sinon_1.replace)(StackTools, 'getStackServiceConfiguration', sinon_1.fake.returns({
                    environment: testEnvironment,
                    cdkToolkit: {
                        bootstrap: (0, sinon_1.fake)(),
                        deploy: (0, sinon_1.fake)(),
                    },
                }));
                const fakeRocket = {
                    mountStack: (0, sinon_1.fake)(),
                    unmountStack: (0, sinon_1.fake)(),
                };
                await (0, deploy_1.deploy)(config, [fakeRocket]);
                (0, expect_1.expect)(StackTools.getStackServiceConfiguration).to.have.been.calledOnceWith(config, [fakeRocket]);
            });
        });
    });
});
