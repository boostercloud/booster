"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PostWithReducer = void 0;
const tslib_1 = require("tslib");
const framework_core_1 = require("@boostercloud/framework-core");
const post_created_1 = require("../events/post-created");
let PostWithReducer = exports.PostWithReducer = class PostWithReducer {
    constructor(id, title, body) {
        this.id = id;
        this.title = title;
        this.body = body;
    }
    static reducePostCreated(event, currentPostWithReducer) {
        return; /* NEW PostWithReducer HERE */
    }
};
tslib_1.__decorate([
    (0, framework_core_1.Reduces)(post_created_1.PostCreated)
], PostWithReducer, "reducePostCreated", null);
exports.PostWithReducer = PostWithReducer = tslib_1.__decorate([
    framework_core_1.Entity
], PostWithReducer);
