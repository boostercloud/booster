"use strict";
/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("./helpers/expect");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const logger_1 = require("../src/logger");
describe('the `getLogger method`', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    context('in `debug` level', () => {
        it('provides `debug`, `info`, `warn` and `error` functions', () => {
            const fakeConsoleDebug = (0, sinon_1.fake)();
            const fakeConsoleInfo = (0, sinon_1.fake)();
            const fakeConsoleWarn = (0, sinon_1.fake)();
            const fakeConsoleError = (0, sinon_1.fake)();
            (0, sinon_1.replace)(console, 'debug', fakeConsoleDebug);
            (0, sinon_1.replace)(console, 'info', fakeConsoleInfo);
            (0, sinon_1.replace)(console, 'warn', fakeConsoleWarn);
            (0, sinon_1.replace)(console, 'error', fakeConsoleError);
            const config = new framework_types_1.BoosterConfig('test');
            config.logLevel = framework_types_1.Level.debug;
            const logger = (0, logger_1.getLogger)(config);
            logger.debug('a');
            logger.info('b');
            logger.warn('c');
            logger.error('d');
            (0, expect_1.expect)(fakeConsoleDebug).to.have.been.calledOnce;
            (0, expect_1.expect)(fakeConsoleInfo).to.have.been.calledOnce;
            (0, expect_1.expect)(fakeConsoleWarn).to.have.been.calledOnce;
            (0, expect_1.expect)(fakeConsoleError).to.have.been.calledOnce;
        });
    });
    context('in `info` level', () => {
        it('provides only `info`, `warn` and `error` functions', () => {
            const fakeConsoleDebug = (0, sinon_1.fake)();
            const fakeConsoleInfo = (0, sinon_1.fake)();
            const fakeConsoleWarn = (0, sinon_1.fake)();
            const fakeConsoleError = (0, sinon_1.fake)();
            (0, sinon_1.replace)(console, 'debug', fakeConsoleDebug);
            (0, sinon_1.replace)(console, 'info', fakeConsoleInfo);
            (0, sinon_1.replace)(console, 'warn', fakeConsoleWarn);
            (0, sinon_1.replace)(console, 'error', fakeConsoleError);
            const config = new framework_types_1.BoosterConfig('test');
            config.logLevel = framework_types_1.Level.info;
            const logger = (0, logger_1.getLogger)(config);
            logger.debug('a');
            logger.info('b');
            logger.warn('c');
            logger.error('d');
            (0, expect_1.expect)(fakeConsoleDebug).not.to.have.been.called;
            (0, expect_1.expect)(fakeConsoleInfo).to.have.been.calledOnce;
            (0, expect_1.expect)(fakeConsoleWarn).to.have.been.calledOnce;
            (0, expect_1.expect)(fakeConsoleError).to.have.been.calledOnce;
        });
    });
    context('in `warn` level', () => {
        it('provides only `warn` and `error` functions', () => {
            const fakeConsoleDebug = (0, sinon_1.fake)();
            const fakeConsoleInfo = (0, sinon_1.fake)();
            const fakeConsoleWarn = (0, sinon_1.fake)();
            const fakeConsoleError = (0, sinon_1.fake)();
            (0, sinon_1.replace)(console, 'debug', fakeConsoleDebug);
            (0, sinon_1.replace)(console, 'info', fakeConsoleInfo);
            (0, sinon_1.replace)(console, 'warn', fakeConsoleWarn);
            (0, sinon_1.replace)(console, 'error', fakeConsoleError);
            const config = new framework_types_1.BoosterConfig('test');
            config.logLevel = framework_types_1.Level.warn;
            const logger = (0, logger_1.getLogger)(config);
            logger.debug('a');
            logger.info('b');
            logger.warn('c');
            logger.error('d');
            (0, expect_1.expect)(fakeConsoleDebug).not.to.have.been.called;
            (0, expect_1.expect)(fakeConsoleInfo).not.to.have.been.called;
            (0, expect_1.expect)(fakeConsoleWarn).to.have.been.calledOnce;
            (0, expect_1.expect)(fakeConsoleError).to.have.been.calledOnce;
        });
    });
    context('in `error` level', () => {
        it('provides only `error` functions', () => {
            const fakeConsoleDebug = (0, sinon_1.fake)();
            const fakeConsoleInfo = (0, sinon_1.fake)();
            const fakeConsoleWarn = (0, sinon_1.fake)();
            const fakeConsoleError = (0, sinon_1.fake)();
            (0, sinon_1.replace)(console, 'debug', fakeConsoleDebug);
            (0, sinon_1.replace)(console, 'info', fakeConsoleInfo);
            (0, sinon_1.replace)(console, 'warn', fakeConsoleWarn);
            (0, sinon_1.replace)(console, 'error', fakeConsoleError);
            const config = new framework_types_1.BoosterConfig('test');
            config.logLevel = framework_types_1.Level.error;
            const logger = (0, logger_1.getLogger)(config);
            logger.debug('a');
            logger.info('b');
            logger.warn('c');
            logger.error('d');
            (0, expect_1.expect)(fakeConsoleDebug).not.to.have.been.called;
            (0, expect_1.expect)(fakeConsoleInfo).not.to.have.been.called;
            (0, expect_1.expect)(fakeConsoleWarn).not.to.have.been.called;
            (0, expect_1.expect)(fakeConsoleError).to.have.been.calledOnce;
        });
    });
    context('when a logger instance is defined at config level', () => {
        context('when no prefix is configured', () => {
            it('uses the configured logger instance with the default prefix', () => {
                const fakeConsoleDebug = (0, sinon_1.fake)();
                const fakeConsoleInfo = (0, sinon_1.fake)();
                const fakeConsoleWarn = (0, sinon_1.fake)();
                const fakeConsoleError = (0, sinon_1.fake)();
                const config = new framework_types_1.BoosterConfig('test');
                config.logger = {
                    debug: fakeConsoleDebug,
                    info: fakeConsoleInfo,
                    warn: fakeConsoleWarn,
                    error: fakeConsoleError,
                };
                const logger = (0, logger_1.getLogger)(config);
                logger.debug('a');
                logger.info('b');
                logger.warn('c');
                logger.error('d');
                (0, expect_1.expect)(fakeConsoleDebug).to.have.been.calledOnceWith('[Booster]: ', 'a');
                (0, expect_1.expect)(fakeConsoleInfo).to.have.been.calledOnceWith('[Booster]: ', 'b');
                (0, expect_1.expect)(fakeConsoleWarn).to.have.been.calledOnceWith('[Booster]: ', 'c');
                (0, expect_1.expect)(fakeConsoleError).to.have.been.calledOnceWith('[Booster]: ', 'd');
            });
            context('and a location is set', () => {
                it('uses the configured logger instance with the default prefix and renders the location', () => {
                    const fakeConsoleDebug = (0, sinon_1.fake)();
                    const fakeConsoleInfo = (0, sinon_1.fake)();
                    const fakeConsoleWarn = (0, sinon_1.fake)();
                    const fakeConsoleError = (0, sinon_1.fake)();
                    const config = new framework_types_1.BoosterConfig('test');
                    config.logger = {
                        debug: fakeConsoleDebug,
                        info: fakeConsoleInfo,
                        warn: fakeConsoleWarn,
                        error: fakeConsoleError,
                    };
                    const logger = (0, logger_1.getLogger)(config, 'test-location');
                    logger.debug('a');
                    logger.info('b');
                    logger.warn('c');
                    logger.error('d');
                    (0, expect_1.expect)(fakeConsoleDebug).to.have.been.calledOnceWith('[Booster]|test-location: ', 'a');
                    (0, expect_1.expect)(fakeConsoleInfo).to.have.been.calledOnceWith('[Booster]|test-location: ', 'b');
                    (0, expect_1.expect)(fakeConsoleWarn).to.have.been.calledOnceWith('[Booster]|test-location: ', 'c');
                    (0, expect_1.expect)(fakeConsoleError).to.have.been.calledOnceWith('[Booster]|test-location: ', 'd');
                });
            });
        });
        context('when a prefix is configured', () => {
            it('uses the configured logger instance with the configured prefix', () => {
                const fakeConsoleDebug = (0, sinon_1.fake)();
                const fakeConsoleInfo = (0, sinon_1.fake)();
                const fakeConsoleWarn = (0, sinon_1.fake)();
                const fakeConsoleError = (0, sinon_1.fake)();
                const config = new framework_types_1.BoosterConfig('test');
                config.logger = {
                    debug: fakeConsoleDebug,
                    info: fakeConsoleInfo,
                    warn: fakeConsoleWarn,
                    error: fakeConsoleError,
                };
                config.logPrefix = 'MyLogger';
                const logger = (0, logger_1.getLogger)(config);
                logger.debug('a');
                logger.info('b');
                logger.warn('c');
                logger.error('d');
                (0, expect_1.expect)(fakeConsoleDebug).to.have.been.calledOnceWith('[MyLogger]: ', 'a');
                (0, expect_1.expect)(fakeConsoleInfo).to.have.been.calledOnceWith('[MyLogger]: ', 'b');
                (0, expect_1.expect)(fakeConsoleWarn).to.have.been.calledOnceWith('[MyLogger]: ', 'c');
                (0, expect_1.expect)(fakeConsoleError).to.have.been.calledOnceWith('[MyLogger]: ', 'd');
            });
        });
        context('when a `location` is provided via parameter', () => {
            it('uses the configured logger instance and sends the provided prefix', () => {
                const fakeConsoleDebug = (0, sinon_1.fake)();
                const fakeConsoleInfo = (0, sinon_1.fake)();
                const fakeConsoleWarn = (0, sinon_1.fake)();
                const fakeConsoleError = (0, sinon_1.fake)();
                const config = new framework_types_1.BoosterConfig('test');
                config.logger = {
                    debug: fakeConsoleDebug,
                    info: fakeConsoleInfo,
                    warn: fakeConsoleWarn,
                    error: fakeConsoleError,
                };
                const logger = (0, logger_1.getLogger)(config, 'ParameterLogger');
                logger.debug('a');
                logger.info('b');
                logger.warn('c');
                logger.error('d');
                (0, expect_1.expect)(fakeConsoleDebug).to.have.been.calledOnceWith('[Booster]|ParameterLogger: ', 'a');
                (0, expect_1.expect)(fakeConsoleInfo).to.have.been.calledOnceWith('[Booster]|ParameterLogger: ', 'b');
                (0, expect_1.expect)(fakeConsoleWarn).to.have.been.calledOnceWith('[Booster]|ParameterLogger: ', 'c');
                (0, expect_1.expect)(fakeConsoleError).to.have.been.calledOnceWith('[Booster]|ParameterLogger: ', 'd');
            });
        });
        context('when the `logPrefix` is overriden via parameter', () => {
            it('uses the configured logger instance and uses the `logPrefix` provided via parameter', () => {
                const fakeConsoleDebug = (0, sinon_1.fake)();
                const fakeConsoleInfo = (0, sinon_1.fake)();
                const fakeConsoleWarn = (0, sinon_1.fake)();
                const fakeConsoleError = (0, sinon_1.fake)();
                const config = new framework_types_1.BoosterConfig('test');
                config.logger = {
                    debug: fakeConsoleDebug,
                    info: fakeConsoleInfo,
                    warn: fakeConsoleWarn,
                    error: fakeConsoleError,
                };
                config.logPrefix = 'ConfigLogger';
                const logger = (0, logger_1.getLogger)(config, undefined, 'ParameterLogger');
                logger.debug('a');
                logger.info('b');
                logger.warn('c');
                logger.error('d');
                (0, expect_1.expect)(fakeConsoleDebug).to.have.been.calledOnceWith('[ParameterLogger]: ', 'a');
                (0, expect_1.expect)(fakeConsoleInfo).to.have.been.calledOnceWith('[ParameterLogger]: ', 'b');
                (0, expect_1.expect)(fakeConsoleWarn).to.have.been.calledOnceWith('[ParameterLogger]: ', 'c');
                (0, expect_1.expect)(fakeConsoleError).to.have.been.calledOnceWith('[ParameterLogger]: ', 'd');
            });
        });
        context('when the `logPrefix` is overriden via parameter and the `location` is provided via parameter', () => {
            it('uses the configured logger instance, uses the `logPrefix` provided via parameter and renders the location', () => {
                const fakeConsoleDebug = (0, sinon_1.fake)();
                const fakeConsoleInfo = (0, sinon_1.fake)();
                const fakeConsoleWarn = (0, sinon_1.fake)();
                const fakeConsoleError = (0, sinon_1.fake)();
                const config = new framework_types_1.BoosterConfig('test');
                config.logger = {
                    debug: fakeConsoleDebug,
                    info: fakeConsoleInfo,
                    warn: fakeConsoleWarn,
                    error: fakeConsoleError,
                };
                config.logPrefix = 'ConfigLogger';
                const logger = (0, logger_1.getLogger)(config, 'ParameterLocation', 'ParameterLogger');
                logger.debug('a');
                logger.info('b');
                logger.warn('c');
                logger.error('d');
                (0, expect_1.expect)(fakeConsoleDebug).to.have.been.calledOnceWith('[ParameterLogger]|ParameterLocation: ', 'a');
                (0, expect_1.expect)(fakeConsoleInfo).to.have.been.calledOnceWith('[ParameterLogger]|ParameterLocation: ', 'b');
                (0, expect_1.expect)(fakeConsoleWarn).to.have.been.calledOnceWith('[ParameterLogger]|ParameterLocation: ', 'c');
                (0, expect_1.expect)(fakeConsoleError).to.have.been.calledOnceWith('[ParameterLogger]|ParameterLocation: ', 'd');
            });
        });
    });
});
