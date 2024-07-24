"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.makeTestFileSystem = void 0;
const file_system_1 = require("../../../src/services/file-system");
const sinon_1 = require("sinon");
const effect_1 = require("@boostercloud/application-tester/src/effect");
const makeTestFileSystem = (overrides) => (0, effect_1.fakeService)(file_system_1.FileSystemService, {
    readDirectoryContents: sinon_1.fake.returns([]),
    readFileContents: sinon_1.fake.returns('{}'),
    ...overrides,
});
exports.makeTestFileSystem = makeTestFileSystem;
