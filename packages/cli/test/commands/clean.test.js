"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
const sinon_1 = require("sinon");
const Clean = require("../../src/commands/clean");
const configService = require("../../src/services/config-service");
const projectChecker = require("../../src/services/project-checker");
const logger_1 = require("../../src/services/logger");
describe('clean', () => {
    describe('Clean class', () => {
        beforeEach(() => {
            (0, sinon_1.replace)(configService, 'cleanProject', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(projectChecker, 'checkCurrentDirIsABoosterProject', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(projectChecker, 'checkCurrentDirBoosterVersion', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'info', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'start', sinon_1.fake.resolves({}));
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new Clean.default([], {}).init();
            (0, expect_1.expect)(projectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        it('runs the command', async () => {
            await new Clean.default([], {}).run();
            (0, expect_1.expect)(projectChecker.checkCurrentDirIsABoosterProject).to.have.been.called;
            (0, expect_1.expect)(configService.cleanProject).to.have.been.called;
            (0, expect_1.expect)(logger_1.oraLogger.start).to.have.been.calledWithMatch('Checking project structure');
            (0, expect_1.expect)(logger_1.oraLogger.start).to.have.been.calledWithMatch('Cleaning project');
            (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWithMatch('Clean complete!');
        });
    });
});
