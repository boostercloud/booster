"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.noopLogger = void 0;
/**
 * Logger that doesn't do anything
 */
exports.noopLogger = {
    debug: () => { },
    warn: () => { },
    info: () => { },
    error: () => { },
};
