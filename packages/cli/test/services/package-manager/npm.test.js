"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_1 = require("sinon");
const effect_1 = require("@boostercloud/framework-types/dist/effect");
const expect_1 = require("../../expect");
const test_impl_1 = require("../file-system/test.impl");
const test_impl_2 = require("../process/test.impl");
const npm_impl_1 = require("../../../src/services/package-manager/npm.impl");
const errors_1 = require("../../../src/common/errors");
const package_manager_1 = require("../../../src/services/package-manager");
const TestFileSystem = (0, test_impl_1.makeTestFileSystem)();
const TestProcess = (0, test_impl_2.makeTestProcess)();
const mapEffError = (effect) => (0, effect_1.pipe)(effect, (0, effect_1.mapError)((e) => e.error));
describe('PackageManager - npm Implementation', () => {
    beforeEach(() => { });
    it('run arbitrary scripts from package.json', async () => {
        const script = 'script';
        const args = ['arg1', 'arg2'];
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        const effect = (0, effect_1.gen)(function* ($) {
            const { runScript } = yield* $(package_manager_1.PackageManagerService);
            return yield* $(runScript(script, args));
        });
        await (0, effect_1.unsafeRunEffect)(mapEffError(effect), {
            layer: effect_1.Layer.using(testLayer)(npm_impl_1.NpmPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith(`npm run ${script} ${args.join(' ')}`);
    });
    describe('when the `compile` script exists', () => {
        const TestFileSystemWithCompileScript = (0, test_impl_1.makeTestFileSystem)({
            readFileContents: sinon_1.fake.returns('{"scripts": {"compile": "tsc"}}'),
        });
        it('run the `compile` script', async () => {
            const compileLayer = effect_1.Layer.all(TestFileSystemWithCompileScript.layer, TestProcess.layer);
            const effect = (0, effect_1.gen)(function* ($) {
                const { build } = yield* $(package_manager_1.PackageManagerService);
                return yield* $(build([]));
            });
            await (0, effect_1.unsafeRunEffect)(mapEffError(effect), {
                layer: effect_1.Layer.using(compileLayer)(npm_impl_1.NpmPackageManager),
                onError: (0, errors_1.guardError)('An error ocurred'),
            });
            (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith('npm run compile');
        });
    });
    describe('when the `compile` script does not exist', () => {
        it('run the `build` script', async () => {
            const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
            const effect = (0, effect_1.gen)(function* ($) {
                const { build } = yield* $(package_manager_1.PackageManagerService);
                return yield* $(build([]));
            });
            await (0, effect_1.unsafeRunEffect)(mapEffError(effect), {
                layer: effect_1.Layer.using(testLayer)(npm_impl_1.NpmPackageManager),
                onError: (0, errors_1.guardError)('An error ocurred'),
            });
            (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith('npm run build');
        });
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
            layer: effect_1.Layer.using(testLayer)(npm_impl_1.NpmPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(CwdTestProcess.fakes.exec).to.have.been.calledWith('npm run script', projectRoot);
    });
    it('can install production dependencies', async () => {
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        const effect = (0, effect_1.gen)(function* ($) {
            const { installProductionDependencies } = yield* $(package_manager_1.PackageManagerService);
            return yield* $(installProductionDependencies());
        });
        await (0, effect_1.unsafeRunEffect)(mapEffError(effect), {
            layer: effect_1.Layer.using(testLayer)(npm_impl_1.NpmPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith('npm install --omit=dev --omit=optional --no-bin-links');
    });
    it('can install all dependencies', async () => {
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        const effect = (0, effect_1.gen)(function* ($) {
            const { installAllDependencies } = yield* $(package_manager_1.PackageManagerService);
            return yield* $(installAllDependencies());
        });
        await (0, effect_1.unsafeRunEffect)(mapEffError(effect), {
            layer: effect_1.Layer.using(testLayer)(npm_impl_1.NpmPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith('npm install');
    });
});
