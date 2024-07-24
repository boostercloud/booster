"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.makeTestPackageManager = void 0;
const effect_1 = require("@boostercloud/application-tester/src/effect");
const sinon_1 = require("sinon");
const package_manager_1 = require("../../../src/services/package-manager");
const makeTestPackageManager = (overrides) => (0, effect_1.fakeService)(package_manager_1.PackageManagerService, {
    setProjectRoot: (0, sinon_1.fake)(),
    runScript: sinon_1.fake.returns(''),
    build: sinon_1.fake.returns(''),
    installAllDependencies: (0, sinon_1.fake)(),
    installProductionDependencies: (0, sinon_1.fake)(),
    ...overrides,
});
exports.makeTestPackageManager = makeTestPackageManager;
