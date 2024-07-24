"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ChangeCartWithFields = void 0;
const tslib_1 = require("tslib");
const framework_core_1 = require("@boostercloud/framework-core");
let ChangeCartWithFields = exports.ChangeCartWithFields = class ChangeCartWithFields {
    constructor(cartId, sku, quantity) {
        this.cartId = cartId;
        this.sku = sku;
        this.quantity = quantity;
    }
    static async handle(command, register) {
        register.events( /* YOUR EVENT HERE */);
    }
};
exports.ChangeCartWithFields = ChangeCartWithFields = tslib_1.__decorate([
    (0, framework_core_1.Command)({
        authorize:  // Specify authorized roles here. Use 'all' to authorize anyone
    })
], ChangeCartWithFields);
