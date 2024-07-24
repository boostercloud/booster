"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ChangeCart = void 0;
const tslib_1 = require("tslib");
const framework_core_1 = require("@boostercloud/framework-core");
let ChangeCart = exports.ChangeCart = class ChangeCart {
    constructor() { }
    static async handle(command, register) {
        register.events( /* YOUR EVENT HERE */);
    }
};
exports.ChangeCart = ChangeCart = tslib_1.__decorate([
    (0, framework_core_1.Command)({
        authorize:  // Specify authorized roles here. Use 'all' to authorize anyone
    })
], ChangeCart);
