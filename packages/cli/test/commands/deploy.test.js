"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
const fancy_test_1 = require("fancy-test");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const test_1 = require("@oclif/test");
const Deploy = require("../../src/commands/deploy");
const providerService = require("../../src/services/provider-service");
const logger_1 = require("../../src/services/logger");
const environment = require("../../src/services/environment");
const packageManagerImpl = require("../../src/services/package-manager/live.impl");
const configService = require("../../src/services/config-service");
const projectChecker = require("../../src/services/project-checker");
const test_impl_1 = require("../services/package-manager/test.impl");
// With this trick we can test non exported symbols
const rewire = require('rewire');
const deploy = rewire('../../src/commands/deploy');
const runTasks = deploy.__get__('runTasks');
const TestPackageManager = (0, test_impl_1.makeTestPackageManager)();
describe('deploy', () => {
    beforeEach(() => {
        delete process.env.BOOSTER_ENV;
    });
    afterEach(() => {
        TestPackageManager.reset();
        // Restore the default sinon sandbox here
        (0, sinon_1.restore)();
    });
    // TODO: Check if I can test that `runTasks` is called from the Command `run` method using `sinon.replace(...)`
    describe('runTasks function', () => {
        context('when an unexpected problem happens', () => {
            fancy_test_1.fancy.stdout().it('fails gracefully showing the error message', async () => {
                const msg = 'weird exception';
                const fakeLoader = Promise.reject(new Error(msg));
                const fakeDeployer = (0, sinon_1.fake)();
                (0, sinon_1.replace)(environment, 'currentEnvironment', sinon_1.fake.returns('test-env'));
                await (0, expect_1.expect)(runTasks(fakeLoader, fakeDeployer)).to.eventually.be.rejectedWith(msg);
                (0, expect_1.expect)(fakeDeployer).not.to.have.been.called;
            });
        });
        context('when index.ts structure is not correct', () => {
            fancy_test_1.fancy.stdout().it('fails gracefully', async () => {
                const msg = 'An error when loading project';
                const fakeLoader = Promise.reject(new Error(msg));
                const fakeDeployer = (0, sinon_1.fake)();
                (0, sinon_1.replace)(environment, 'currentEnvironment', sinon_1.fake.returns('test-env'));
                await (0, expect_1.expect)(runTasks(fakeLoader, fakeDeployer)).to.eventually.be.rejectedWith(msg);
                (0, expect_1.expect)(fakeDeployer).not.to.have.been.called;
            });
        });
        context('when there is a valid index.ts', () => {
            fancy_test_1.fancy.stdout().it('Starts deployment', async (ctx) => {
                // TODO: Once we migrate all services to the new way, we can remove this and just use the Test Layer for each of them
                (0, sinon_1.replace)(packageManagerImpl, 'LivePackageManager', TestPackageManager.layer);
                const fakeProvider = {};
                const fakeLoader = sinon_1.fake.resolves({
                    provider: fakeProvider,
                    appName: 'fake app',
                    region: 'tunte',
                    entities: {},
                });
                const fakeDeployer = (0, sinon_1.fake)((config) => {
                    var _a;
                    (_a = config.logger) === null || _a === void 0 ? void 0 : _a.info('this is a progress update');
                });
                (0, sinon_1.replace)(environment, 'currentEnvironment', sinon_1.fake.returns('test-env'));
                await runTasks(fakeLoader, fakeDeployer);
                (0, expect_1.expect)(ctx.stdout).to.include('Deployment complete');
                (0, expect_1.expect)(fakeDeployer).to.have.been.calledOnce;
            });
        });
    });
    describe('run', () => {
        context('when no environment provided', async () => {
            test_1.test
                .loadConfig({ root: __dirname })
                .stdout()
                .command(['deploy'])
                .it('shows no environment provided error', (ctx) => {
                (0, expect_1.expect)(ctx.stdout).to.match(/No environment set/);
            });
        });
    });
    describe('deploy class', () => {
        beforeEach(() => {
            const config = new framework_types_1.BoosterConfig('fake_environment');
            (0, sinon_1.replace)(configService, 'compileProjectAndLoadConfig', sinon_1.fake.resolves(config));
            (0, sinon_1.replace)(providerService, 'deployToCloudProvider', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(configService, 'createDeploymentSandbox', sinon_1.fake.returns('fake/path'));
            (0, sinon_1.replace)(configService, 'cleanDeploymentSandbox', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(projectChecker, 'checkCurrentDirBoosterVersion', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'fail', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'info', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'start', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'succeed', sinon_1.fake.resolves({}));
        });
        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new Deploy.default([], {}).init();
            (0, expect_1.expect)(projectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        it('without flags', async () => {
            await new Deploy.default([], {}).run();
            (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
            (0, expect_1.expect)(providerService.deployToCloudProvider).to.have.not.been.called;
            (0, expect_1.expect)(logger_1.oraLogger.fail).to.have.been.calledWithMatch(/No environment set/);
        });
        it('with -e flag incomplete', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await new Deploy.default(['-e'], {}).run();
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.contain('--environment expects a value');
            (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
            (0, expect_1.expect)(providerService.deployToCloudProvider).to.have.not.been.called;
        });
        it('with --environment flag incomplete', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await new Deploy.default(['--environment'], {}).run();
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.to.contain('--environment expects a value');
            (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
            (0, expect_1.expect)(providerService.deployToCloudProvider).to.have.not.been.called;
        });
        describe('inside a booster project', () => {
            it('entering correct environment', async () => {
                await new Deploy.default(['-e', 'fake_environment'], {}).run();
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.been.called;
                (0, expect_1.expect)(providerService.deployToCloudProvider).to.have.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Deployment complete!');
            });
            it('entering correct environment and nonexisting flag', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new Deploy.default(['-e', 'fake_environment', '--nonexistingoption'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Nonexistent flag: --nonexistingoption');
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
                (0, expect_1.expect)(providerService.deployToCloudProvider).to.have.not.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.info).to.have.not.been.calledWithMatch('Deployment complete!');
            });
        });
    });
});
