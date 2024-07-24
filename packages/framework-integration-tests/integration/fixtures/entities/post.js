"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Post = void 0;
const tslib_1 = require("tslib");
const framework_core_1 = require("@boostercloud/framework-core");
let Post = exports.Post = class Post {
    constructor(id) {
        this.id = id;
    }
};
exports.Post = Post = tslib_1.__decorate([
    framework_core_1.Entity
], Post);
