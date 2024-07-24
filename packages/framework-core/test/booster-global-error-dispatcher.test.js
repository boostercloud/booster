"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const expect_1 = require("./expect");
const framework_types_1 = require("@boostercloud/framework-types");
const src_1 = require("../src");
const sinon_1 = require("sinon");
const booster_1 = require("../src/booster");
const booster_global_error_dispatcher_1 = require("../src/booster-global-error-dispatcher");
describe('BoosterGlobalErrorDispatcher', () => {
    let config;
    const baseError = new Error('test');
    beforeEach(() => {
        config = new framework_types_1.BoosterConfig('test');
    });
    afterEach(() => {
        booster_1.Booster.configure('test', (config) => {
            config.appName = '';
            config.globalErrorsHandler = undefined;
        });
        config.globalErrorsHandler = undefined;
        (0, sinon_1.restore)();
    });
    it('should dispatch original error if none is defined as a globalErrorsHandler', async () => {
        const globalError = new framework_types_1.GlobalErrorContainer(baseError);
        const errorDispatcher = new booster_global_error_dispatcher_1.BoosterGlobalErrorDispatcher(config);
        await (0, expect_1.expect)(errorDispatcher.dispatch(globalError)).to.eventually.eq(baseError);
    });
    it('should dispatch handleGenericError if globalErrorsHandler is defined for a GlobalErrorContainer', async () => {
        let ErrorHandler = class ErrorHandler {
            static async onScheduledCommandHandlerError(error) {
                return new Error(`${error}.onScheduledCommandHandlerError`);
            }
            static async onError(error) {
                return new Error(`${error}.updated error`);
            }
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        const globalError = new framework_types_1.GlobalErrorContainer(baseError);
        config.globalErrorsHandler = { class: ErrorHandler };
        const errorDispatcher = new booster_global_error_dispatcher_1.BoosterGlobalErrorDispatcher(config);
        const result = await errorDispatcher.dispatch(globalError);
        (0, expect_1.expect)(result).to.be.instanceof(Error);
        (0, expect_1.expect)(result === null || result === void 0 ? void 0 : result.toString()).to.be.eq(`Error: Error: ${baseError.message}.updated error`);
    });
    it('should dispatch original error if there is an error processing them', async () => {
        let ErrorHandler = class ErrorHandler {
            static async onScheduledCommandHandlerError(error) {
                throw new Error('failed');
            }
            static async onError(error) {
                return new Error(`${error}.onError`);
            }
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        const scheduleCommandGlobalError = new framework_types_1.ScheduleCommandGlobalError(baseError);
        config.globalErrorsHandler = { class: ErrorHandler };
        const errorDispatcher = new booster_global_error_dispatcher_1.BoosterGlobalErrorDispatcher(config);
        const result = await errorDispatcher.dispatch(scheduleCommandGlobalError);
        (0, expect_1.expect)(result === null || result === void 0 ? void 0 : result.toString()).to.be.eq('Error: failed');
    });
    it('should dispatch specific and generic handler if both are defined for a specific error', async () => {
        let ErrorHandler = class ErrorHandler {
            static async onScheduledCommandHandlerError(error) {
                return new Error(`${error}.onScheduledCommandHandlerError`);
            }
            static async onError(error) {
                return new Error(`${error}.onError`);
            }
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        const scheduleCommandGlobalError = new framework_types_1.ScheduleCommandGlobalError(baseError);
        config.globalErrorsHandler = { class: ErrorHandler };
        const errorDispatcher = new booster_global_error_dispatcher_1.BoosterGlobalErrorDispatcher(config);
        const result = await errorDispatcher.dispatch(scheduleCommandGlobalError);
        (0, expect_1.expect)(result === null || result === void 0 ? void 0 : result.toString()).to.be.eq(`Error: Error: Error: ${baseError.message}.onScheduledCommandHandlerError.onError`);
    });
    it('should dispatch CommandHandlerGlobalError', async () => {
        let ErrorHandler = class ErrorHandler {
            static async onCommandHandlerError(error) {
                return new Error(`${error}.onCommandHandlerError`);
            }
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        const mockCommand = {};
        const commandHandlerGlobalError = new framework_types_1.CommandHandlerGlobalError(mockCommand, baseError);
        config.globalErrorsHandler = { class: ErrorHandler };
        const errorDispatcher = new booster_global_error_dispatcher_1.BoosterGlobalErrorDispatcher(config);
        const result = await errorDispatcher.dispatch(commandHandlerGlobalError);
        (0, expect_1.expect)(result === null || result === void 0 ? void 0 : result.toString()).to.be.eq(`Error: Error: ${baseError.message}.onCommandHandlerError`);
    });
    it('should dispatch EventHandlerGlobalError', async () => {
        let ErrorHandler = class ErrorHandler {
            static async onDispatchEventHandlerError(error, eventInstance) {
                return new Error(`${error}.onDispatchEventHandlerError`);
            }
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        const mockEventInstance = {};
        const eventHandlerGlobalError = new framework_types_1.EventHandlerGlobalError(mockEventInstance, baseError);
        config.globalErrorsHandler = { class: ErrorHandler };
        const errorDispatcher = new booster_global_error_dispatcher_1.BoosterGlobalErrorDispatcher(config);
        const result = await errorDispatcher.dispatch(eventHandlerGlobalError);
        (0, expect_1.expect)(result === null || result === void 0 ? void 0 : result.toString()).to.be.eq(`Error: Error: ${baseError.message}.onDispatchEventHandlerError`);
    });
    it('should dispatch ReducerGlobalError', async () => {
        let ErrorHandler = class ErrorHandler {
            static async onReducerError(error, eventInstance, snapshotInstance) {
                return new Error(`${error}.onReducerError`);
            }
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        const mockEventInstance = {};
        const mockSnapshotInstance = {};
        const reducerGlobalError = new framework_types_1.ReducerGlobalError(mockEventInstance, mockSnapshotInstance, baseError);
        config.globalErrorsHandler = { class: ErrorHandler };
        const errorDispatcher = new booster_global_error_dispatcher_1.BoosterGlobalErrorDispatcher(config);
        const result = await errorDispatcher.dispatch(reducerGlobalError);
        (0, expect_1.expect)(result === null || result === void 0 ? void 0 : result.toString()).to.be.eq(`Error: Error: ${baseError.message}.onReducerError`);
    });
    it('should dispatch ProjectionGlobalError', async () => {
        let ErrorHandler = class ErrorHandler {
            static async onProjectionError(error, entity, readModel) {
                return new Error(`${error}.onProjectionError`);
            }
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        const mockEntity = {};
        const mockReadModel = {};
        const projectionGlobalError = new framework_types_1.ProjectionGlobalError(mockEntity, mockReadModel, baseError);
        config.globalErrorsHandler = { class: ErrorHandler };
        const errorDispatcher = new booster_global_error_dispatcher_1.BoosterGlobalErrorDispatcher(config);
        const result = await errorDispatcher.dispatch(projectionGlobalError);
        (0, expect_1.expect)(result === null || result === void 0 ? void 0 : result.toString()).to.be.eq(`Error: Error: ${baseError.message}.onProjectionError`);
    });
    it('should dispatch SnapshotPersistHandlerGlobalError', async () => {
        let ErrorHandler = class ErrorHandler {
            static async onSnapshotPersistError(error, snapshot) {
                return new Error(`${error}.onSnapshotPersistError`);
            }
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        const mockSnapshot = {};
        const snapshotPersistHandlerGlobalError = new framework_types_1.SnapshotPersistHandlerGlobalError(mockSnapshot, baseError);
        config.globalErrorsHandler = { class: ErrorHandler };
        const errorDispatcher = new booster_global_error_dispatcher_1.BoosterGlobalErrorDispatcher(config);
        const result = await errorDispatcher.dispatch(snapshotPersistHandlerGlobalError);
        (0, expect_1.expect)(result === null || result === void 0 ? void 0 : result.toString()).to.be.eq(`Error: Error: ${baseError.message}.onSnapshotPersistError`);
    });
    it('should ignore errors on ProjectionGlobalError if undefined is returned', async () => {
        let ErrorHandler = class ErrorHandler {
            static async onProjectionError(error, entity, readModel) {
                return undefined;
            }
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        const mockEntity = {};
        const mockReadModel = {};
        const projectionGlobalError = new framework_types_1.ProjectionGlobalError(mockEntity, mockReadModel, baseError);
        config.globalErrorsHandler = { class: ErrorHandler };
        const errorDispatcher = new booster_global_error_dispatcher_1.BoosterGlobalErrorDispatcher(config);
        const result = await errorDispatcher.dispatch(projectionGlobalError);
        (0, expect_1.expect)(result).to.be.undefined;
    });
    it('should ignore erros if generic handler returns an undefined', async () => {
        let ErrorHandler = class ErrorHandler {
            static async onScheduledCommandHandlerError(error) {
                return new Error(`${error}.onScheduledCommandHandlerError`);
            }
            static async onError(error) {
                return undefined;
            }
        };
        ErrorHandler = tslib_1.__decorate([
            (0, src_1.GlobalErrorHandler)()
        ], ErrorHandler);
        const scheduleCommandGlobalError = new framework_types_1.ScheduleCommandGlobalError(baseError);
        config.globalErrorsHandler = { class: ErrorHandler };
        const errorDispatcher = new booster_global_error_dispatcher_1.BoosterGlobalErrorDispatcher(config);
        const result = await errorDispatcher.dispatch(scheduleCommandGlobalError);
        (0, expect_1.expect)(result).to.be.undefined;
    });
});
