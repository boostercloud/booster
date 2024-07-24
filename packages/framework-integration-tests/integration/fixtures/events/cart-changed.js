"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CartChanged = void 0;
const tslib_1 = require("tslib");
const framework_core_1 = require("@boostercloud/framework-core");
let CartChanged = exports.CartChanged = class CartChanged {
    constructor() { }
    entityID() {
        return; /* the associated entity ID */
    }
};
exports.CartChanged = CartChanged = tslib_1.__decorate([
    framework_core_1.Event
], CartChanged);
