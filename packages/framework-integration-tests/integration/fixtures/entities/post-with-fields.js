"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PostWithFields = void 0;
const tslib_1 = require("tslib");
const framework_core_1 = require("@boostercloud/framework-core");
let PostWithFields = exports.PostWithFields = class PostWithFields {
    constructor(id, title, body) {
        this.id = id;
        this.title = title;
        this.body = body;
    }
};
exports.PostWithFields = PostWithFields = tslib_1.__decorate([
    framework_core_1.Entity
], PostWithFields);
