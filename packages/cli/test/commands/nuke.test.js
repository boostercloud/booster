"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
const fancy_test_1 = require("fancy-test");
const sinon_1 = require("sinon");
const user_prompt_1 = require("../../src/services/user-prompt");
const framework_types_1 = require("@boostercloud/framework-types");
const Nuke = require("../../src/commands/nuke");
const providerService = require("../../src/services/provider-service");
const logger_1 = require("../../src/services/logger");
const test_1 = require("@oclif/test");
const environment = require("../../src/services/environment");
const configService = require("../../src/services/config-service");
const projectChecker = require("../../src/services/project-checker");
const rewire = require('rewire');
const nuke = rewire('../../src/commands/nuke');
const runTasks = nuke.__get__('runTasks');
const loader = nuke.__get__('askToConfirmRemoval');
describe('nuke', () => {
    beforeEach(() => {
        delete process.env.BOOSTER_ENV;
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('runTasks function', () => {
        context('when an unexpected problem happens', () => {
            fancy_test_1.fancy.stdout().it('fails gracefully showing the error message', async () => {
                const msg = 'weird exception';
                const fakeLoader = Promise.reject(new Error(msg));
                const fakeNuke = (0, sinon_1.fake)();
                (0, sinon_1.replace)(environment, 'currentEnvironment', sinon_1.fake.returns('test-env'));
                await (0, expect_1.expect)(runTasks(fakeLoader, fakeNuke)).to.eventually.be.rejectedWith(msg);
                (0, expect_1.expect)(fakeNuke).not.to.have.been.called;
            });
        });
        context('when a wrong application name is provided', () => {
            fancy_test_1.fancy.stdout().it('fails gracefully showing the error message', async () => {
                const fakeProvider = {};
                const fakeConfig = Promise.resolve({
                    provider: fakeProvider,
                    appName: 'fake app',
                    region: 'tunte',
                    entities: {},
                });
                const prompter = new user_prompt_1.default();
                const fakePrompter = sinon_1.fake.resolves('fake app 2'); // The user entered wrong app name
                (0, sinon_1.replace)(prompter, 'defaultOrPrompt', fakePrompter);
                const fakeNuke = (0, sinon_1.fake)();
                const errorMsg = 'Wrong app name, stopping nuke!';
                (0, sinon_1.replace)(environment, 'currentEnvironment', sinon_1.fake.returns('test-env'));
                await (0, expect_1.expect)(runTasks(loader(prompter, false, fakeConfig), fakeNuke)).to.eventually.be.rejectedWith(errorMsg);
                (0, expect_1.expect)(fakeNuke).not.to.have.been.called;
            });
        });
        context('when the --force flag is provided', () => {
            fancy_test_1.fancy.stdout().it('continues without asking for the application name', async () => {
                const fakeProvider = {};
                const fakeConfig = Promise.resolve({
                    provider: fakeProvider,
                    appName: 'fake app',
                    region: 'tunte',
                    entities: {},
                });
                const prompter = new user_prompt_1.default();
                const fakePrompter = sinon_1.fake.resolves('fake app 2'); // The user entered wrong app name
                (0, sinon_1.replace)(prompter, 'defaultOrPrompt', fakePrompter);
                const fakeNuke = (0, sinon_1.fake)();
                (0, sinon_1.replace)(environment, 'currentEnvironment', sinon_1.fake.returns('test-env'));
                await (0, expect_1.expect)(runTasks(loader(prompter, true, fakeConfig), fakeNuke)).to.eventually.be.fulfilled;
                (0, expect_1.expect)(prompter.defaultOrPrompt).not.to.have.been.called;
                (0, expect_1.expect)(fakeNuke).to.have.been.calledOnce;
            });
        });
        context('when a valid application name is provided', () => {
            fancy_test_1.fancy.stdout().it('starts removal', async (ctx) => {
                const fakeProvider = {};
                const fakeConfig = Promise.resolve({
                    provider: fakeProvider,
                    appName: 'fake app',
                    region: 'tunte',
                    entities: {},
                });
                const prompter = new user_prompt_1.default();
                const fakePrompter = sinon_1.fake.resolves('fake app');
                (0, sinon_1.replace)(prompter, 'defaultOrPrompt', fakePrompter);
                const fakeNuke = (0, sinon_1.fake)((config) => {
                    var _a;
                    (_a = config.logger) === null || _a === void 0 ? void 0 : _a.info('this is a progress update');
                });
                (0, sinon_1.replace)(environment, 'currentEnvironment', sinon_1.fake.returns('test-env'));
                await runTasks(loader(prompter, false, fakeConfig), fakeNuke);
                (0, expect_1.expect)(ctx.stdout).to.include('Removal complete!');
                (0, expect_1.expect)(fakeNuke).to.have.been.calledOnce;
            });
        });
    });
    describe('run', () => {
        context('when no environment provided', async () => {
            test_1.test
                .loadConfig({ root: __dirname })
                .stdout()
                .command(['nuke'])
                .it('shows no environment provided error', (ctx) => {
                (0, expect_1.expect)(ctx.stdout).to.match(/No environment set/);
            });
        });
    });
    describe('command class', () => {
        beforeEach(() => {
            const config = new framework_types_1.BoosterConfig('fake_environment');
            (0, sinon_1.replace)(configService, 'compileProjectAndLoadConfig', sinon_1.fake.resolves(config));
            (0, sinon_1.replace)(providerService, 'nukeCloudProviderResources', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(projectChecker, 'checkCurrentDirBoosterVersion', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'fail', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'info', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'start', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'succeed', sinon_1.fake.resolves({}));
        });
        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new Nuke.default([], {}).init();
            (0, expect_1.expect)(projectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        it('without flags', async () => {
            await new Nuke.default([], {}).run();
            (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
            (0, expect_1.expect)(providerService.nukeCloudProviderResources).to.have.not.been.called;
            (0, expect_1.expect)(logger_1.oraLogger.fail).to.have.been.calledWithMatch(/No environment set/);
        });
        it('with -e flag incomplete', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await new Nuke.default(['-e'], {}).run();
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.to.contain('--environment expects a value');
            (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
            (0, expect_1.expect)(providerService.nukeCloudProviderResources).to.have.not.been.called;
        });
        it('with --environment flag incomplete', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await new Nuke.default(['--environment'], {}).run();
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.to.contain('--environment expects a value');
            (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
            (0, expect_1.expect)(providerService.nukeCloudProviderResources).to.have.not.been.called;
        });
        describe('inside a booster project', () => {
            it('entering correct environment and application name', async () => {
                (0, sinon_1.replace)(user_prompt_1.default.prototype, 'defaultOrPrompt', sinon_1.fake.resolves('new-booster-app'));
                await new Nuke.default(['-e', 'fake_environment'], {}).run();
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.been.called;
                (0, expect_1.expect)(providerService.nukeCloudProviderResources).to.have.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Removal complete!');
            });
            it('entering correct environment and --force flag', async () => {
                await new Nuke.default(['-e', 'fake_environment', '--force'], {}).run();
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.been.called;
                (0, expect_1.expect)(providerService.nukeCloudProviderResources).to.have.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Removal complete!');
            });
            it('entering correct environment and -f flag', async () => {
                await new Nuke.default(['-e', 'fake_environment', '-f'], {}).run();
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.been.called;
                (0, expect_1.expect)(providerService.nukeCloudProviderResources).to.have.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Removal complete!');
            });
            it('entering correct environment but a wrong application name', async () => {
                (0, sinon_1.replace)(user_prompt_1.default.prototype, 'defaultOrPrompt', sinon_1.fake.resolves('fake app 2'));
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new Nuke.default(['-e', 'fake_environment'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Wrong app name, stopping nuke!');
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.been.called;
                (0, expect_1.expect)(providerService.nukeCloudProviderResources).to.have.not.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.info).to.have.not.been.calledWithMatch('Removal complete!');
            });
            it('entering correct environment and nonexisting flag', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new Nuke.default(['-e', 'fake_environment', '--nonexistingoption'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Nonexistent flag: --nonexistingoption');
                (0, expect_1.expect)(providerService.nukeCloudProviderResources).to.have.not.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.info).to.have.not.been.calledWithMatch('Removal complete!');
            });
            it('without defining environment and --force', async () => {
                await new Nuke.default(['--force'], {}).run();
                (0, expect_1.expect)(providerService.nukeCloudProviderResources).to.have.not.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.fail).to.have.been.calledWithMatch(/No environment set/);
            });
        });
    });
});
