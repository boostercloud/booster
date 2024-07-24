"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_1 = require("sinon");
const projectChecker = require("../../src/services/project-checker");
const framework_types_1 = require("@boostercloud/framework-types");
const expect_1 = require("../expect");
const environment = require("../../src/services/environment");
const PackageManager = require("../../src/services/package-manager/live.impl");
const test_impl_1 = require("./package-manager/test.impl");
const rewire = require('rewire');
const configService = rewire('../../src/services/config-service');
const TestPackageManager = (0, test_impl_1.makeTestPackageManager)();
describe('configService', () => {
    const userProjectPath = 'path/to/project';
    afterEach(() => {
        (0, sinon_1.restore)();
        TestPackageManager.reset();
    });
    describe('compileProject', () => {
        it('runs the npm command', async () => {
            (0, sinon_1.replace)(PackageManager, 'LivePackageManager', TestPackageManager.layer);
            await configService.compileProject(userProjectPath);
            (0, expect_1.expect)(TestPackageManager.fakes.runScript).to.have.calledWith('clean');
            (0, expect_1.expect)(TestPackageManager.fakes.build).to.have.been.called;
        });
    });
    describe('cleanProject', () => {
        it('runs the npm command', async () => {
            (0, sinon_1.replace)(PackageManager, 'LivePackageManager', TestPackageManager.layer);
            await configService.cleanProject(userProjectPath);
            (0, expect_1.expect)(TestPackageManager.fakes.runScript).to.have.been.calledWith('clean');
        });
    });
    describe('compileProjectAndLoadConfig', () => {
        let checkItIsABoosterProject;
        beforeEach(() => {
            checkItIsABoosterProject = (0, sinon_1.stub)(projectChecker, 'checkItIsABoosterProject').resolves();
        });
        it('loads the config when the selected environment exists', async () => {
            const config = new framework_types_1.BoosterConfig('test');
            const rewires = [
                configService.__set__('compileProject', (0, sinon_1.fake)()),
                configService.__set__('loadUserProject', sinon_1.fake.returns({
                    Booster: {
                        config: config,
                        configuredEnvironments: new Set(['test']),
                        configureCurrentEnv: sinon_1.fake.yields(config),
                    },
                })),
            ];
            (0, sinon_1.replace)(environment, 'currentEnvironment', sinon_1.fake.returns('test'));
            await (0, expect_1.expect)(configService.compileProjectAndLoadConfig(userProjectPath)).to.eventually.become(config);
            (0, expect_1.expect)(checkItIsABoosterProject).to.have.been.calledOnceWithExactly(userProjectPath);
            rewires.forEach((fn) => fn());
        });
        it('throws the right error when there are not configured environments', async () => {
            const config = new framework_types_1.BoosterConfig('test');
            const rewires = [
                configService.__set__('compileProject', (0, sinon_1.fake)()),
                configService.__set__('loadUserProject', sinon_1.fake.returns({
                    Booster: {
                        config: config,
                        configuredEnvironments: new Set([]),
                        configureCurrentEnv: sinon_1.fake.yields(config),
                    },
                })),
            ];
            await (0, expect_1.expect)(configService.compileProjectAndLoadConfig(userProjectPath)).to.eventually.be.rejectedWith(/You haven't configured any environment/);
            (0, expect_1.expect)(checkItIsABoosterProject).to.have.been.calledOnceWithExactly(userProjectPath);
            rewires.forEach((fn) => fn());
        });
        it('throws the right error when the environment does not exist', async () => {
            const config = new framework_types_1.BoosterConfig('test');
            const rewires = [
                configService.__set__('compileProject', (0, sinon_1.fake)()),
                configService.__set__('loadUserProject', sinon_1.fake.returns({
                    Booster: {
                        config: config,
                        configuredEnvironments: new Set(['another']),
                        configureCurrentEnv: sinon_1.fake.yields(config),
                    },
                })),
            ];
            (0, sinon_1.replace)(environment, 'currentEnvironment', sinon_1.fake.returns('test'));
            await (0, expect_1.expect)(configService.compileProjectAndLoadConfig(userProjectPath)).to.eventually.be.rejectedWith(/The environment 'test' does not match any of the environments/);
            (0, expect_1.expect)(checkItIsABoosterProject).to.have.been.calledOnceWithExactly(userProjectPath);
            rewires.forEach((fn) => fn());
        });
    });
});
