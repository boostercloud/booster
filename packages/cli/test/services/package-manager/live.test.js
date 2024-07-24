"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_1 = require("sinon");
const effect_1 = require("@boostercloud/framework-types/dist/effect");
const expect_1 = require("../../expect");
const test_impl_1 = require("../file-system/test.impl");
const test_impl_2 = require("../process/test.impl");
const package_manager_1 = require("../../../src/services/package-manager");
const live_impl_1 = require("../../../src/services/package-manager/live.impl");
const errors_1 = require("../../../src/common/errors");
describe('PackageManager - Live Implementation (with inference)', () => {
    const TestProcess = (0, test_impl_2.makeTestProcess)();
    afterEach(() => {
        TestProcess.reset();
    });
    const effect = (0, effect_1.gen)(function* ($) {
        const { runScript } = yield* $(package_manager_1.PackageManagerService);
        return yield* $(runScript('script', []));
    });
    const runScript = (0, effect_1.pipe)(effect, (0, effect_1.mapError)((e) => e.error));
    it('infers Rush when a `.rush` folder is present', async () => {
        const TestFileSystem = (0, test_impl_1.makeTestFileSystem)({ readDirectoryContents: sinon_1.fake.returns(['.rush']) });
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        await (0, effect_1.unsafeRunEffect)(runScript, {
            layer: effect_1.Layer.using(testLayer)(live_impl_1.InferredPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith('rushx script');
    });
    it('infers pnpm when a `pnpm-lock.yaml` file is present', async () => {
        const TestFileSystem = (0, test_impl_1.makeTestFileSystem)({ readDirectoryContents: sinon_1.fake.returns(['pnpm-lock.yaml']) });
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        await (0, effect_1.unsafeRunEffect)(runScript, {
            layer: effect_1.Layer.using(testLayer)(live_impl_1.InferredPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith('pnpm run script');
    });
    it('infers npm when a `package-lock.json` file is present', async () => {
        const TestFileSystem = (0, test_impl_1.makeTestFileSystem)({ readDirectoryContents: sinon_1.fake.returns(['package-lock.json']) });
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        await (0, effect_1.unsafeRunEffect)(runScript, {
            layer: effect_1.Layer.using(testLayer)(live_impl_1.InferredPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith('npm run script');
    });
    it('infers yarn when a `yarn.lock` file is present', async () => {
        const TestFileSystem = (0, test_impl_1.makeTestFileSystem)({ readDirectoryContents: sinon_1.fake.returns(['yarn.lock']) });
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        await (0, effect_1.unsafeRunEffect)(runScript, {
            layer: effect_1.Layer.using(testLayer)(live_impl_1.InferredPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith('yarn run script');
    });
    it('infers npm when no lock file is present', async () => {
        const TestFileSystem = (0, test_impl_1.makeTestFileSystem)({ readDirectoryContents: sinon_1.fake.returns([]) });
        const testLayer = effect_1.Layer.all(TestFileSystem.layer, TestProcess.layer);
        await (0, effect_1.unsafeRunEffect)(runScript, {
            layer: effect_1.Layer.using(testLayer)(live_impl_1.InferredPackageManager),
            onError: (0, errors_1.guardError)('An error ocurred'),
        });
        (0, expect_1.expect)(TestProcess.fakes.exec).to.have.been.calledWith('npm run script');
    });
});
