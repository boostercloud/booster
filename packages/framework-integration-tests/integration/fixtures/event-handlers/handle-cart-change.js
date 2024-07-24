"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.HandleCartChange = void 0;
const tslib_1 = require("tslib");
const cart_item_changed_1 = require("../events/cart-item-changed");
const framework_core_1 = require("@boostercloud/framework-core");
let HandleCartChange = exports.HandleCartChange = class HandleCartChange {
    static async handle(event, register) { }
};
exports.HandleCartChange = HandleCartChange = tslib_1.__decorate([
    (0, framework_core_1.EventHandler)(cart_item_changed_1.CartItemChanged)
], HandleCartChange);
