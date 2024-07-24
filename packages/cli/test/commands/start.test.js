"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const Start = require("../../src/commands/start");
const providerService = require("../../src/services/provider-service");
const logger_1 = require("../../src/services/logger");
const test_1 = require("@oclif/test");
const environment = require("../../src/services/environment");
const configService = require("../../src/services/config-service");
const projectChecker = require("../../src/services/project-checker");
const rewire = require("rewire");
const start = rewire('../../src/commands/start');
const runTasks = start.__get__('runTasks');
describe('start', () => {
    beforeEach(() => {
        delete process.env.BOOSTER_ENV;
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('runTasks function', () => {
        it('calls the runner for the local server', async () => {
            const fakeProvider = {};
            const fakeConfig = {
                provider: fakeProvider,
                appName: 'fake-app',
            };
            const fakeLoader = sinon_1.fake.resolves(fakeConfig);
            const fakeRunner = (0, sinon_1.fake)();
            (0, sinon_1.replace)(environment, 'currentEnvironment', sinon_1.fake.returns('test-env'));
            await runTasks(3000, fakeLoader, fakeRunner);
            (0, expect_1.expect)(fakeRunner).to.have.been.calledOnce;
        });
    });
    describe('run', () => {
        context('when no environment provided', async () => {
            test_1.test
                .loadConfig({ root: __dirname })
                .stdout()
                .command(['start'])
                .it('shows no environment provided error', (ctx) => {
                (0, expect_1.expect)(ctx.stdout).to.match(/No environment set/);
            });
        });
    });
    describe('start class', () => {
        beforeEach(() => {
            const config = new framework_types_1.BoosterConfig('fake_environment');
            (0, sinon_1.replace)(configService, 'compileProjectAndLoadConfig', sinon_1.fake.resolves(config));
            (0, sinon_1.replace)(providerService, 'startProvider', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(projectChecker, 'checkCurrentDirBoosterVersion', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'fail', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'info', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'start', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'succeed', sinon_1.fake.resolves({}));
        });
        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new Start.default([], {}).init();
            (0, expect_1.expect)(projectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        it('without flags', async () => {
            await new Start.default([], {}).run();
            (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
            (0, expect_1.expect)(providerService.startProvider).to.have.not.been.called;
            (0, expect_1.expect)(logger_1.oraLogger.fail).to.have.been.calledWithMatch(/No environment set/);
        });
        it('with -e flag incomplete', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await new Start.default(['-e'], {}).run();
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.to.contain('--environment expects a value');
            (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
            (0, expect_1.expect)(providerService.startProvider).to.have.not.been.called;
        });
        it('with --environment flag incomplete', async () => {
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                await new Start.default(['--environment'], {}).run();
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
            (0, expect_1.expect)(exceptionMessage).to.to.contain('--environment expects a value');
            (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
            (0, expect_1.expect)(providerService.startProvider).to.have.not.been.called;
        });
        describe('inside a booster project', () => {
            it('entering correct environment', async () => {
                await new Start.default(['-e', 'fake_environment'], {}).run();
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.been.called;
                (0, expect_1.expect)(providerService.startProvider).to.have.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.start).to.have.been.calledWithMatch(/Starting debug server on port/);
            });
            it('entering correct environment and --port flag', async () => {
                await new Start.default(['-e', 'fake_environment', '--port', '5000'], {}).run();
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.been.called;
                (0, expect_1.expect)(providerService.startProvider).to.have.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.start).to.have.been.calledWithMatch(/Starting debug server on port 5000/);
            });
            it('entering correct environment and -p flag', async () => {
                await new Start.default(['-e', 'fake_environment', '-p', '5000'], {}).run();
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.been.called;
                (0, expect_1.expect)(providerService.startProvider).to.have.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.start).to.have.been.calledWithMatch(/Starting debug server on port 5000/);
            });
            it('entering correct environment and nonexisting flag', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new Start.default(['-e', 'fake_environment', '--nonexistingoption'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Nonexistent flag: --nonexistingoption');
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
                (0, expect_1.expect)(providerService.startProvider).to.have.not.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.start).to.have.not.been.calledWithMatch(/Starting debug server on port/);
            });
            it('entering correct environment and --port with incomplete port number', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new Start.default(['-e', 'fake_environment', '--port'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('--port expects a value');
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
                (0, expect_1.expect)(providerService.startProvider).to.have.not.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.start).to.have.not.been.calledWithMatch(/Starting debug server on port/);
            });
            it('entering correct environment and -p with incomplete port number', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new Start.default(['-e', 'fake_environment', '-p'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('--port expects a value');
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
                (0, expect_1.expect)(providerService.startProvider).to.have.not.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.start).to.have.not.been.calledWithMatch(/Starting debug server on port/);
            });
            it('without defining environment and -p', async () => {
                await new Start.default(['-p', '5000'], {}).run();
                (0, expect_1.expect)(configService.compileProjectAndLoadConfig).to.have.not.been.called;
                (0, expect_1.expect)(providerService.startProvider).to.have.not.been.called;
                (0, expect_1.expect)(logger_1.oraLogger.fail).to.have.been.calledWithMatch(/No environment set/);
            });
        });
    });
});
