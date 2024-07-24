"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const mocha_1 = require("mocha");
const sinon_1 = require("sinon");
const script_1 = require("../../src/common/script");
const expect_1 = require("../expect");
const brand_1 = require("../../src/common/brand");
const testContext = {
    ctxParam: 'value',
};
function replaceLogger() {
    const fakeLogger = {
        info: (0, sinon_1.fake)(),
        fail: (0, sinon_1.fake)(),
        start: (0, sinon_1.fake)(),
        succeed: (0, sinon_1.fake)(),
    };
    (0, sinon_1.replace)(script_1.Script, 'logger', fakeLogger);
    return fakeLogger;
}
(0, mocha_1.describe)('The Script class', () => {
    (0, mocha_1.afterEach)(() => {
        (0, sinon_1.restore)();
    });
    (0, mocha_1.describe)('init', () => {
        (0, mocha_1.it)('runs a sequence of successful actions chaining context and return values', async () => {
            replaceLogger();
            const fakeAction1 = (0, sinon_1.stub)().resolves();
            const fakeAction2 = (0, sinon_1.stub)().resolves();
            const fakeAction3 = (0, sinon_1.stub)().resolves();
            await script_1.Script.init('initializing test', Promise.resolve(testContext))
                .step('step 1', fakeAction1)
                .step('step 2', fakeAction2)
                .step('step 3', fakeAction3)
                .done();
            (0, expect_1.expect)(fakeAction1).to.have.been.calledOnce;
            // @ts-ignore
            (0, expect_1.expect)(fakeAction1).to.have.been.calledBefore(fakeAction2);
            (0, expect_1.expect)(fakeAction1).to.have.been.calledOnceWith({ ctxParam: 'value' });
            // @ts-ignore
            (0, expect_1.expect)(fakeAction2).to.have.been.calledBefore(fakeAction3);
            (0, expect_1.expect)(fakeAction2).to.have.been.calledOnceWith({ ctxParam: 'value' });
            // @ts-ignore
            (0, expect_1.expect)(fakeAction2).to.have.been.calledBefore(fakeAction3);
        });
        (0, mocha_1.it)('runs a sequence of successful actions and stops on failure', async () => {
            replaceLogger();
            const errorMsg = 'some error';
            const fakeAction1 = (0, sinon_1.stub)().resolves();
            const fakeAction2 = (0, sinon_1.stub)().rejects(errorMsg);
            const fakeAction3 = (0, sinon_1.stub)().resolves();
            await (0, expect_1.expect)(script_1.Script.init('initializing test', Promise.resolve(testContext))
                .step('step', fakeAction1)
                .step('step', fakeAction2)
                .step('step', fakeAction3)
                .done()).to.eventually.be.rejectedWith(errorMsg);
            (0, expect_1.expect)(fakeAction1).to.have.been.calledOnce;
            // @ts-ignore
            (0, expect_1.expect)(fakeAction1).to.have.been.calledBefore(fakeAction2);
            (0, expect_1.expect)(fakeAction1).to.have.been.calledOnceWith({ ctxParam: 'value' });
            // @ts-ignore
            (0, expect_1.expect)(fakeAction1).to.have.been.calledBefore(fakeAction2);
            (0, expect_1.expect)(fakeAction2).to.have.been.calledOnceWith({ ctxParam: 'value' });
            (0, expect_1.expect)(fakeAction3).not.to.have.been.called;
            (0, expect_1.expect)(fakeAction2).to.have.been.calledOnceWith({ ctxParam: 'value' });
        });
        (0, mocha_1.it)('prints the provided message', async () => {
            const msg = 'initializing';
            const logger = replaceLogger();
            await script_1.Script.init(msg, Promise.resolve(testContext)).done();
            (0, expect_1.expect)(logger.info).to.have.been.calledOnceWith(msg);
        });
        (0, mocha_1.it)('fails gracefully in case of initializer failure', async () => {
            replaceLogger();
            const msg = 'initializing';
            const errorMessage = 'some error';
            const err = new Error(errorMessage);
            const initializer = (0, sinon_1.stub)().rejects(err);
            await (0, expect_1.expect)(script_1.Script.init(msg, initializer()).done()).to.eventually.be.rejectedWith(errorMessage);
        });
    });
    (0, mocha_1.describe)('info', () => {
        (0, mocha_1.it)('prints the provided message', async () => {
            const initMsg = 'initializing test';
            const msg = 'yo!';
            const logger = replaceLogger();
            await script_1.Script.init(initMsg, Promise.resolve(testContext)).info(msg).done();
            (0, expect_1.expect)(logger.info).to.have.been.calledWith(msg);
        });
    });
    (0, mocha_1.describe)('step', () => {
        (0, mocha_1.it)('prints the passed message, initializes and runs the function', async () => {
            const msg = 'That is one small step for a man';
            const val = 'one giant leap for mankind';
            const stepFn = (0, sinon_1.stub)().resolves(val);
            const logger = replaceLogger();
            await script_1.Script.init('initializing', Promise.resolve(testContext)).step(msg, stepFn).done();
            (0, expect_1.expect)(logger.start).to.have.been.calledOnceWith(msg);
            (0, expect_1.expect)(logger.succeed).to.have.been.called;
            (0, expect_1.expect)(stepFn).to.have.been.calledOnceWith(testContext);
        });
        (0, mocha_1.it)('fails gracefully on step function failure', async () => {
            const errorMsg = 'some error';
            const err = new Error(errorMsg);
            const msg = 'That is no step for anyone';
            const stepFn = (0, sinon_1.stub)().rejects(err);
            const logger = replaceLogger();
            await (0, expect_1.expect)(script_1.Script.init('initializing', Promise.resolve(testContext)).step(msg, stepFn).done()).to.eventually.be.rejectedWith(errorMsg);
            (0, expect_1.expect)(logger.start).to.have.been.calledOnceWith(msg);
            (0, expect_1.expect)(logger.succeed).not.to.have.been.called;
            (0, expect_1.expect)(stepFn).to.have.been.calledOnceWith(testContext);
        });
    });
    (0, mocha_1.describe)('catch', () => {
        (0, mocha_1.it)('prints a custom error message for specified error types', async () => {
            replaceLogger();
            const err = new SyntaxError('other message');
            const msg = 'much nicer message';
            await (0, expect_1.expect)(script_1.Script.init('initializing', Promise.reject(err))
                .catch('SyntaxError', () => msg)
                .done()).to.eventually.be.rejectedWith(msg);
        });
        (0, mocha_1.it)('prints the error message for non specified error types', async () => {
            replaceLogger();
            const msg = 'some message';
            const err = new Error(msg);
            await (0, expect_1.expect)(script_1.Script.init('initializing', Promise.reject(err))
                .catch('SyntaxError', () => msg)
                .done()).to.eventually.be.rejectedWith(msg);
        });
    });
    (0, mocha_1.describe)('optionalStep', () => {
        (0, mocha_1.it)('should skip optional step and log info', async () => {
            const initMessage = 'Initializing test';
            const message = 'Hello World!';
            const stepFn = (0, sinon_1.stub)().resolves(message);
            const logger = replaceLogger();
            const willSkip = true;
            await script_1.Script.init(initMessage, Promise.resolve(testContext)).optionalStep(willSkip, message, stepFn).done();
            (0, expect_1.expect)(logger.start).to.not.have.been.calledOnce;
            (0, expect_1.expect)(logger.succeed).to.not.have.been.calledOnce;
            (0, expect_1.expect)(logger.info).to.have.been.calledWith(brand_1.default.mellancholize(`Skipping: ${message}`));
        });
        (0, mocha_1.it)('should not skip optional step and call passed action', async () => {
            const initMessage = 'Initializing test';
            const message = 'Hello World!';
            const stepFn = (0, sinon_1.stub)().resolves(message);
            const logger = replaceLogger();
            const willSkip = false;
            await script_1.Script.init(initMessage, Promise.resolve(testContext)).optionalStep(willSkip, message, stepFn).done();
            (0, expect_1.expect)(logger.start).to.have.been.calledOnceWith(message);
            (0, expect_1.expect)(logger.succeed).to.have.been.called;
            (0, expect_1.expect)(logger.info).to.not.have.been.calledWith(brand_1.default.mellancholize(`Skipping: ${message}`));
        });
    });
});
