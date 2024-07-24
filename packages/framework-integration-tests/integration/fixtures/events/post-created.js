"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PostCreated = void 0;
const tslib_1 = require("tslib");
const framework_core_1 = require("@boostercloud/framework-core");
let PostCreated = exports.PostCreated = class PostCreated {
    constructor(postId, title, body) {
        this.postId = postId;
        this.title = title;
        this.body = body;
    }
    entityID() {
        return; /* the associated entity ID */
    }
};
exports.PostCreated = PostCreated = tslib_1.__decorate([
    framework_core_1.Event
], PostCreated);
