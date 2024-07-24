"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const cli_helper_1 = require("../../helper/cli-helper");
const sleep_1 = require("../../helper/sleep");
const file_helper_1 = require("../../helper/file-helper");
const deps_helper_1 = require("../../helper/deps-helper");
const constants_1 = require("./constants");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../cli/src/common/sandbox");
const framework_common_helpers_1 = require("@boostercloud/framework-common-helpers");
let serverProcess;
let sandboxPath;
before(async () => {
    console.log('preparing sandboxed project...');
    const configuredAssets = ['assets', 'assetFile.txt'];
    sandboxPath = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)(constants_1.sandboxName), configuredAssets);
    console.log('overriding booster dependencies...');
    await (0, deps_helper_1.overrideWithBoosterLocalDependencies)(sandboxPath);
    console.log('installing dependencies...');
    await (0, framework_common_helpers_1.runCommand)(sandboxPath, 'npm install');
    console.log(`starting local server in ${sandboxPath}...`);
    serverProcess = (0, cli_helper_1.start)(sandboxPath, 'local');
    await (0, sleep_1.sleep)(10000); // TODO: We need some time for the server to start, but maybe we could do this faster using the `waitForIt` method
});
after(async () => {
    console.log('stopping local server...');
    serverProcess.kill('SIGINT');
    console.log('removing sandbox project...');
    await (0, file_helper_1.removeFolders)([sandboxPath]);
});
