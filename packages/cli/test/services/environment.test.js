"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const environment_1 = require("../../src/services/environment");
const logger_1 = require("../../src/services/logger");
const sinon_1 = require("sinon");
const expect_1 = require("../expect");
describe('environment service', () => {
    let formerValue;
    beforeEach(() => {
        if (process.env.BOOSTER_ENV !== undefined) {
            formerValue = process.env.BOOSTER_ENV;
        }
        delete process.env.BOOSTER_ENV;
    });
    afterEach(() => {
        process.env.BOOSTER_ENV = formerValue;
        (0, sinon_1.restore)();
    });
    describe('currentEnvironment', () => {
        it('get current environment: testing', async () => {
            process.env.BOOSTER_ENV = 'testing';
            (0, expect_1.expect)((0, environment_1.currentEnvironment)()).to.be.equal('testing');
        });
        it('get current environment: undefined', async () => {
            (0, expect_1.expect)((0, environment_1.currentEnvironment)()).to.be.undefined;
        });
    });
    describe('initializeEnvironment', () => {
        beforeEach(() => {
            (0, sinon_1.replace)(logger_1.logger, 'error', sinon_1.fake.resolves({}));
        });
        const logMessage = /No environment set/;
        describe('process.env.BOOSTER_ENV set', () => {
            beforeEach(() => {
                process.env.BOOSTER_ENV = 'testing';
            });
            it('set environment in param: no log message', async () => {
                (0, environment_1.initializeEnvironment)(logger_1.logger, 'production');
                (0, expect_1.expect)(logger_1.logger.error).to.not.have.been.calledWithMatch(logMessage);
                (0, expect_1.expect)(process.env.BOOSTER_ENV).to.be.equal('production');
            });
            it('do not pass environment as param: no log message', async () => {
                (0, environment_1.initializeEnvironment)(logger_1.logger);
                (0, expect_1.expect)(logger_1.logger.error).to.not.have.been.calledWithMatch(logMessage);
                (0, expect_1.expect)(process.env.BOOSTER_ENV).to.be.equal('testing');
            });
        });
        describe('process.env.BOOSTER_ENV not set', () => {
            it('set environment only in param: no log message', async () => {
                (0, environment_1.initializeEnvironment)(logger_1.logger, 'production');
                (0, expect_1.expect)(logger_1.logger.error).to.not.have.been.calledWithMatch(logMessage);
                (0, expect_1.expect)(process.env.BOOSTER_ENV).to.be.equal('production');
            });
            it('environment not set as param: log message', async () => {
                (0, environment_1.initializeEnvironment)(logger_1.logger);
                (0, expect_1.expect)(logger_1.logger.error).to.have.been.calledWithMatch(logMessage);
                (0, expect_1.expect)(process.env.BOOSTER_ENV).to.be.undefined;
            });
        });
    });
});
