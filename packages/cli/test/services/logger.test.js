"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const logger_1 = require("../../src/services/logger");
const fs = require("fs");
const path = require("path");
const sinon_1 = require("sinon");
const expect_1 = require("../expect");
describe('Booster logger', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('logger', () => {
        const message = 'this is a message for the logger';
        beforeEach(() => {
            (0, sinon_1.replace)(logger_1.oraLogger, 'warn', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'info', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(logger_1.oraLogger, 'fail', sinon_1.fake.resolves({}));
        });
        it('log a debug message', async () => {
            logger_1.logger.debug(message);
            (0, expect_1.expect)(logger_1.oraLogger.warn).to.have.been.calledWith(message);
        });
        it('log a info message', async () => {
            logger_1.logger.info(message);
            (0, expect_1.expect)(logger_1.oraLogger.info).to.have.been.calledWith(message);
        });
        it('log a error message', async () => {
            logger_1.logger.error(message);
            (0, expect_1.expect)(logger_1.oraLogger.fail).to.have.been.calledWith(message);
        });
    });
    describe('appendOnErrorsFile', () => {
        beforeEach(() => {
            (0, sinon_1.replace)(fs, 'appendFileSync', sinon_1.fake.returns(true));
        });
        it('writes in error.log file', async () => {
            const data = 'this is a message\nseparated with new line';
            const errorsFile = path.join(process.cwd(), 'errors.log');
            (0, logger_1.appendOnErrorsFile)(data);
            (0, expect_1.expect)(fs.appendFileSync).to.have.been.calledWithMatch(errorsFile, /this is a message/);
            (0, expect_1.expect)(fs.appendFileSync).to.have.been.calledWithMatch(errorsFile, /separated with new line/);
        });
    });
});
