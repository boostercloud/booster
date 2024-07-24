"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CartChangedWithFields = void 0;
const tslib_1 = require("tslib");
const framework_core_1 = require("@boostercloud/framework-core");
let CartChangedWithFields = exports.CartChangedWithFields = class CartChangedWithFields {
    constructor(cartId, sku, quantity) {
        this.cartId = cartId;
        this.sku = sku;
        this.quantity = quantity;
    }
    entityID() {
        return; /* the associated entity ID */
    }
};
exports.CartChangedWithFields = CartChangedWithFields = tslib_1.__decorate([
    framework_core_1.Event
], CartChangedWithFields);
