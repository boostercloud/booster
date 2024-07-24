"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CartReadModel = void 0;
const tslib_1 = require("tslib");
const framework_core_1 = require("@boostercloud/framework-core");
let CartReadModel = exports.CartReadModel = class CartReadModel {
    constructor(id) {
        this.id = id;
    }
};
exports.CartReadModel = CartReadModel = tslib_1.__decorate([
    (0, framework_core_1.ReadModel)({
        authorize:  // Specify authorized roles here. Use 'all' to authorize anyone
    })
], CartReadModel);
