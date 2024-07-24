"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_1 = require("sinon");
const effect_1 = require("@boostercloud/framework-types/dist/effect");
const expect_1 = require("../../expect");
const test_impl_1 = require("../file-system/test.impl");
const test_impl_2 = require("../process/test.impl");
const rush_impl_1 = require("../../../src/services/package-manager/rush.impl");
const errors_1 = require("../../../src/common/errors");
const package_manager_1 = require("../../../src/services/package-manager");
const TestFileSystem = (0, test_impl_1.makeTestFileSystem)();
const TestProcess = (0, test_impl_2.makeTestProcess)();
const mapEffError = (effect) => (0, effect_1.pipe)(effect, (0, effect_1.mapError)((e) => e.error));
describe('PackageManager - Rush Implementation', () => {
    beforeEach(() => {
        TestFileSystem.reset();
        TestProcess.reset();
    });
    it('run arbitrary scripts from package.json', async () => {
        const script = 'script';
        const args = ['arg1', 'arg2'];
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        const effect = (0, effect_1.gen)(function* ($) {
            const { runScript } = yield* $(package_manager_1.PackageManagerService);
            return yield* $(runScript(script, args));
        });
        await (0, effect_1.unsafeRunEffect)(mapEffError(effect), {
            layer: effect_1.Layer.using(testLayer)(rush_impl_1.RushPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith(`rushx ${script} ${args.join(' ')}`);
    });
    it('runs the `build` script', async () => {
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        const effect = (0, effect_1.gen)(function* ($) {
            const { build } = yield* $(package_manager_1.PackageManagerService);
            return yield* $(build([]));
        });
        await (0, effect_1.unsafeRunEffect)(mapEffError(effect), {
            layer: effect_1.Layer.using(testLayer)(rush_impl_1.RushPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith('rush build');
    });
    it('can set the project root properly', async () => {
        const projectRoot = 'projectRoot';
        const CwdTestProcess = (0, test_impl_2.makeTestProcess)({ cwd: sinon_1.fake.returns(projectRoot) });
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, CwdTestProcess.layer);
        const effect = (0, effect_1.gen)(function* ($) {
            const { setProjectRoot, runScript } = yield* $(package_manager_1.PackageManagerService);
            yield* $(setProjectRoot(projectRoot));
            yield* $(runScript('script', []));
        });
        await (0, effect_1.unsafeRunEffect)(mapEffError(effect), {
            layer: effect_1.Layer.using(testLayer)(rush_impl_1.RushPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(CwdTestProcess.fakes.exec).to.have.been.calledWith('rushx script', projectRoot);
    });
    it('cannot install production dependencies', async () => {
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        const effect = (0, effect_1.gen)(function* ($) {
            const { installProductionDependencies } = yield* $(package_manager_1.PackageManagerService);
            return yield* $(installProductionDependencies());
        });
        return (0, expect_1.expect)((0, effect_1.unsafeRunEffect)(mapEffError(effect), {
            layer: effect_1.Layer.using(testLayer)(rush_impl_1.RushPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        })).to.be.eventually.rejected;
    });
    it('can install all dependencies', async () => {
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        const effect = (0, effect_1.gen)(function* ($) {
            const { installAllDependencies } = yield* $(package_manager_1.PackageManagerService);
            return yield* $(installAllDependencies());
        });
        await (0, effect_1.unsafeRunEffect)(mapEffError(effect), {
            layer: effect_1.Layer.using(testLayer)(rush_impl_1.RushPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith('rush update');
    });
});
