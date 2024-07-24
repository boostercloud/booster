"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
const framework_types_1 = require("@boostercloud/framework-types");
const cdk_toolkit_1 = require("aws-cdk/lib/cdk-toolkit");
const sinon_1 = require("sinon");
const StackServiceConfiguration = require("../../src/infrastructure/stack-tools");
const S3Tools = require("../../src/infrastructure/s3utils");
const rocketUtils = require("../../src/rockets/rocket-utils");
const rewire = require('rewire');
const nukeModule = rewire('../../src/infrastructure/nuke');
describe('the nuke module', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('the `nuke` method', () => {
        it('calls to `getStackServiceConfiguration` to get the stack configuration', async () => {
            const revertNukeToolkit = nukeModule.__set__('nukeToolkit', (0, sinon_1.fake)());
            const revertNukeApp = nukeModule.__set__('nukeApplication', (0, sinon_1.fake)());
            (0, sinon_1.replace)(StackServiceConfiguration, 'getStackServiceConfiguration', sinon_1.fake.resolves({ aws: '', appStacks: '', cdkToolkit: '' }));
            (0, sinon_1.replace)(cdk_toolkit_1.CdkToolkit.prototype, 'destroy', (0, sinon_1.fake)());
            const config = new framework_types_1.BoosterConfig('test');
            await nukeModule.nuke(config);
            (0, expect_1.expect)(StackServiceConfiguration.getStackServiceConfiguration).to.have.been.calledWithMatch(config);
            revertNukeToolkit();
            revertNukeApp();
        });
        it('nukes the toolkit stack', async () => {
            const fakeNukeToolkit = (0, sinon_1.fake)();
            const revertNukeToolkit = nukeModule.__set__('nukeToolkit', fakeNukeToolkit);
            const revertNukeApp = nukeModule.__set__('nukeApplication', (0, sinon_1.fake)());
            const fakeStackServiceConfiguration = { sdk: 'here goes the SDK', appStacks: '', cdkToolkit: '' };
            (0, sinon_1.replace)(StackServiceConfiguration, 'getStackServiceConfiguration', sinon_1.fake.resolves(fakeStackServiceConfiguration));
            const config = new framework_types_1.BoosterConfig('test');
            await nukeModule.nuke(config);
            (0, expect_1.expect)(fakeNukeToolkit).to.have.been.calledWithMatch(config, fakeStackServiceConfiguration.sdk);
            revertNukeToolkit();
            revertNukeApp();
        });
        it('nukes the application stack', async () => {
            const fakeNukeApplication = (0, sinon_1.fake)();
            const revertNukeToolkit = nukeModule.__set__('nukeToolkit', (0, sinon_1.fake)());
            const revertNukeApp = nukeModule.__set__('nukeApplication', fakeNukeApplication);
            const fakeStackServiceConfiguration = {
                cdkToolkit: 'and here the cdkToolkit',
            };
            (0, sinon_1.replace)(StackServiceConfiguration, 'getStackServiceConfiguration', sinon_1.fake.resolves(fakeStackServiceConfiguration));
            const config = new framework_types_1.BoosterConfig('test');
            await nukeModule.nuke(config);
            (0, expect_1.expect)(fakeNukeApplication).to.have.been.calledWithMatch(config, fakeStackServiceConfiguration.cdkToolkit);
            revertNukeToolkit();
            revertNukeApp();
        });
        it('logs progress calling to the passed `logger`', async () => {
            var _a;
            const revertNukeToolkit = nukeModule.__set__('nukeToolkit', (0, sinon_1.fake)());
            const revertNukeApp = nukeModule.__set__('nukeApplication', (0, sinon_1.fake)());
            const fakeStackServiceConfiguration = {
                aws: 'here goes the SDK',
                appStacks: 'and here the appStacks',
                cdkToolkit: 'and here the cdkToolkit',
            };
            (0, sinon_1.replace)(StackServiceConfiguration, 'getStackServiceConfiguration', sinon_1.fake.resolves(fakeStackServiceConfiguration));
            const config = new framework_types_1.BoosterConfig('test');
            config.logger = {
                debug: (0, sinon_1.fake)(),
                info: (0, sinon_1.fake)(),
                warn: (0, sinon_1.fake)(),
                error: (0, sinon_1.fake)(),
            };
            await nukeModule.nuke(config);
            (0, expect_1.expect)((_a = config.logger) === null || _a === void 0 ? void 0 : _a.info).to.have.been.calledWithMatch(/.*/, /Destroying application/);
            revertNukeToolkit();
            revertNukeApp();
        });
        it('logs errors thrown by `getStackServiceConfiguration`', async () => {
            const revertNukeToolkit = nukeModule.__set__('nukeToolkit', (0, sinon_1.fake)());
            const revertNukeApp = nukeModule.__set__('nukeApplication', (0, sinon_1.fake)());
            const error = new Error('things gone bad');
            (0, sinon_1.replace)(StackServiceConfiguration, 'getStackServiceConfiguration', sinon_1.fake.rejects(error));
            (0, sinon_1.replace)(cdk_toolkit_1.CdkToolkit.prototype, 'destroy', (0, sinon_1.fake)());
            const config = new framework_types_1.BoosterConfig('test');
            await (0, expect_1.expect)(nukeModule.nuke(config)).to.be.eventually.rejectedWith(error);
            revertNukeToolkit();
            revertNukeApp();
        });
        it('logs errors thrown by the toolkit nuke process', async () => {
            const error = new Error('things gone bad');
            const revertNukeToolkit = nukeModule.__set__('nukeToolkit', sinon_1.fake.rejects(error));
            const revertNukeApp = nukeModule.__set__('nukeApplication', (0, sinon_1.fake)());
            const fakeStackServiceConfiguration = {
                aws: 'here goes the SDK',
                appStacks: 'and here the appStacks',
                cdkToolkit: 'and here the cdkToolkit',
            };
            (0, sinon_1.replace)(StackServiceConfiguration, 'getStackServiceConfiguration', sinon_1.fake.resolves(fakeStackServiceConfiguration));
            (0, sinon_1.replace)(cdk_toolkit_1.CdkToolkit.prototype, 'destroy', (0, sinon_1.fake)());
            const config = new framework_types_1.BoosterConfig('test');
            await (0, expect_1.expect)(nukeModule.nuke(config)).to.be.eventually.rejectedWith(error);
            revertNukeToolkit();
            revertNukeApp();
        });
        it('logs errors thrown by the application nuke process', async () => {
            const error = new Error('things gone bad');
            const revertNukeToolkit = nukeModule.__set__('nukeToolkit', (0, sinon_1.fake)());
            const revertNukeApp = nukeModule.__set__('nukeApplication', sinon_1.fake.rejects(error));
            const fakeStackServiceConfiguration = {
                aws: 'here goes the SDK',
                appStacks: 'and here the appStacks',
                cdkToolkit: 'and here the cdkToolkit',
            };
            (0, sinon_1.replace)(StackServiceConfiguration, 'getStackServiceConfiguration', sinon_1.fake.resolves(fakeStackServiceConfiguration));
            (0, sinon_1.replace)(cdk_toolkit_1.CdkToolkit.prototype, 'destroy', (0, sinon_1.fake)());
            const config = new framework_types_1.BoosterConfig('test');
            await (0, expect_1.expect)(nukeModule.nuke(config)).to.be.eventually.rejectedWith(error);
            revertNukeToolkit();
            revertNukeApp();
        });
        context('with rockets', () => {
            it('cleans rocket-initialized resources', async () => {
                const fakeNukeRockets = (0, sinon_1.fake)();
                const revertNukeToolkit = nukeModule.__set__('nukeToolkit', (0, sinon_1.fake)());
                const revertNukeApp = nukeModule.__set__('nukeApplication', (0, sinon_1.fake)());
                const revertNukeRockets = nukeModule.__set__('nukeRockets', fakeNukeRockets);
                const fakeStackServiceConfiguration = {
                    sdk: 'here goes the SDK',
                    appStacks: 'and here the appStacks',
                    cdkToolkit: 'and here the cdkToolkit',
                };
                (0, sinon_1.replace)(StackServiceConfiguration, 'getStackServiceConfiguration', sinon_1.fake.resolves(fakeStackServiceConfiguration));
                const config = new framework_types_1.BoosterConfig('test');
                const fakeRockets = [
                    {
                        mountStack: (0, sinon_1.fake)(),
                        unmountStack: (0, sinon_1.fake)(),
                    },
                ];
                await nukeModule.nuke(config, fakeRockets);
                (0, expect_1.expect)(fakeNukeRockets).to.have.been.calledWithMatch(config, fakeStackServiceConfiguration.sdk, fakeRockets);
                revertNukeToolkit();
                revertNukeApp();
                revertNukeRockets();
            });
        });
    });
    describe('the `nukeToolkit` method', () => {
        it('empties the toolkit bucket', async () => {
            const nukeToolkit = nukeModule.__get__('nukeToolkit');
            (0, sinon_1.replace)(S3Tools, 'emptyS3Bucket', (0, sinon_1.fake)());
            const config = { appName: 'test-app' };
            const fakeCloudformation = { deleteStack: sinon_1.fake.returns({ promise: sinon_1.fake.resolves(true) }) };
            const fakeSDK = {
                cloudFormation: sinon_1.fake.returns(fakeCloudformation),
            };
            await nukeToolkit(config, fakeSDK);
            (0, expect_1.expect)(S3Tools.emptyS3Bucket).to.have.been.calledWithMatch(config, fakeSDK, 'test-app-toolkit-bucket');
        });
        it('deletes the toolkit stack', async () => {
            const nukeToolkit = nukeModule.__get__('nukeToolkit');
            (0, sinon_1.replace)(S3Tools, 'emptyS3Bucket', (0, sinon_1.fake)());
            const config = { appName: 'test-app' };
            const fakeCloudformation = { deleteStack: sinon_1.fake.returns({ promise: sinon_1.fake.resolves(true) }) };
            const fakeSDK = {
                cloudFormation: sinon_1.fake.returns(fakeCloudformation),
            };
            await nukeToolkit(config, fakeSDK);
            (0, expect_1.expect)(fakeSDK.cloudFormation).to.have.been.called;
            (0, expect_1.expect)(fakeCloudformation.deleteStack).to.have.been.calledWithMatch({
                StackName: 'test-app-toolkit',
            });
        });
    });
    describe('the `nukeRockets` method', () => {
        it('builds a rocketUtils object and passes it to all the rockets', async () => {
            const nukeRockets = nukeModule.__get__('nukeRockets');
            const fakeRocketUtils = { rocket: 'utils' };
            const fakeBuildRocketUtils = sinon_1.fake.returns(fakeRocketUtils);
            (0, sinon_1.replace)(rocketUtils, 'buildRocketUtils', fakeBuildRocketUtils);
            const config = new framework_types_1.BoosterConfig('test');
            const fakeSDK = { fake: 'aws' };
            const fakeUnmountStack = (0, sinon_1.fake)();
            const fakeRockets = [
                {
                    unmountStack: fakeUnmountStack,
                },
            ];
            await nukeRockets(config, fakeSDK, fakeRockets);
            (0, expect_1.expect)(fakeBuildRocketUtils).to.have.been.calledWithMatch(config, fakeSDK);
            (0, expect_1.expect)(fakeUnmountStack).to.have.been.calledWithMatch(fakeRocketUtils);
        });
    });
    describe('the `nukeApplication` method', () => {
        it('destroys the application stack', async () => {
            const nukeApplication = nukeModule.__get__('nukeApplication');
            const config = {
                appName: 'test-app',
                resourceNames: {
                    applicationStack: 'stack-name',
                },
            };
            const cdkToolkit = {
                destroy: (0, sinon_1.fake)(),
            };
            await nukeApplication(config, cdkToolkit);
            (0, expect_1.expect)(cdkToolkit.destroy).to.have.been.calledWithMatch({
                selector: { patterns: ['stack-name'] },
                exclusively: false,
                force: true,
            });
        });
    });
});
