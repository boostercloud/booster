"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const fs = require("fs");
const sinon_1 = require("sinon");
const effect_1 = require("@boostercloud/framework-types/dist/effect");
const expect_1 = require("../../expect");
const file_system_1 = require("../../../src/services/file-system");
const live_impl_1 = require("../../../src/services/file-system/live.impl");
const errors_1 = require("../../../src/common/errors");
describe('FileSystem - Live Implementation', () => {
    beforeEach(() => {
        (0, sinon_1.replace)(fs.promises, 'readdir', sinon_1.fake.resolves(''));
    });
    it('uses fs.promises.readdir', async () => {
        const directoryPath = 'directoryPath';
        const effect = (0, effect_1.gen)(function* ($) {
            const { readDirectoryContents } = yield* $(file_system_1.FileSystemService);
            return yield* $(readDirectoryContents(directoryPath));
        });
        await (0, effect_1.unsafeRunEffect)((0, effect_1.pipe)(effect, (0, effect_1.mapError)((e) => e.error)), {
            layer: live_impl_1.LiveFileSystem,
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(fs.promises.readdir).to.have.been.calledWith(directoryPath);
    });
});
