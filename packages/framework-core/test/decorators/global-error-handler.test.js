"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const expect_1 = require("../expect");
const src_1 = require("../../src");
describe('the `GlobalErrorHandler` decorator', () => {
    afterEach(() => {
        src_1.Booster.configure('test', (config) => {
            config.appName = '';
            config.globalErrorsHandler = undefined;
        });
    });
    it('adds the error handler class as an error handler in the Booster configuration', () => {
        // Register command
        let ErrorHandler = class ErrorHandler {
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        // Make Booster be of any type to access private members
        const booster = src_1.Booster;
        (0, expect_1.expect)(booster.config.globalErrorsHandler.class).to.be.eq(ErrorHandler);
    });
    it('adds the error handler class as an error handler in the Booster configuration with expected methods', () => {
        // Register command
        let ErrorHandler = class ErrorHandler {
            static async onCommandHandlerError(error, command) {
                return new Error('');
            }
            static async onScheduledCommandHandlerError(error) {
                return new Error('');
            }
            static async onDispatchEventHandlerError(error, eventInstance) {
                return new Error('');
            }
            static async onProjectionError(error, entity, readModel) {
                return new Error('');
            }
            static async onReducerError(error, eventInstance, snapshotInstance) {
                return new Error('');
            }
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        // Make Booster be of any type to access private members
        const booster = src_1.Booster;
        (0, expect_1.expect)(booster.config.globalErrorsHandler.class).to.be.eq(ErrorHandler);
    });
});
