"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.start = exports.nuke = exports.deploy = void 0;
const path = require("path");
const framework_common_helpers_1 = require("@boostercloud/framework-common-helpers");
// Path to the CLI binary compiled for this project
const cliBinaryPath = path.join(__dirname, '..', '..', 'node_modules', '.bin', 'boost');
async function deploy(projectPath, environmentName = 'production') {
    // Production dependencies are installed by the cliHelper command
    await (0, framework_common_helpers_1.runCommand)(projectPath, `${cliBinaryPath} deploy --verbose -e ${environmentName}`);
}
exports.deploy = deploy;
async function nuke(projectPath, environmentName = 'production') {
    // Dependencies should be installed before running the nuke command
    await (0, framework_common_helpers_1.runCommand)(projectPath, 'npm install --omit=dev --omit=optional --no-bin-links');
    await (0, framework_common_helpers_1.runCommand)(projectPath, `${cliBinaryPath} nuke --verbose -e ${environmentName} --force`);
}
exports.nuke = nuke;
function start(path, environmentName = 'local') {
    return (0, framework_common_helpers_1.runCommandAsync)(path, `${cliBinaryPath} start --verbose -e ${environmentName}`);
}
exports.start = start;
