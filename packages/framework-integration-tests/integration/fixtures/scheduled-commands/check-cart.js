"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CheckCart = void 0;
const tslib_1 = require("tslib");
const framework_core_1 = require("@boostercloud/framework-core");
let CheckCart = exports.CheckCart = class CheckCart {
    static async handle(register) {
        /* YOUR CODE HERE */
    }
};
exports.CheckCart = CheckCart = tslib_1.__decorate([
    (0, framework_core_1.ScheduledCommand)({
        // Specify schedule settings here. By default, it will be triggered every 30 minutes
        minute: '0/30',
    })
], CheckCart);
