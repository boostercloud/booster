"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const path = require("path");
const chai_1 = require("chai");
const file_helper_1 = require("../../helper/file-helper");
const child_process_promise_1 = require("child-process-promise");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../cli/src/common/sandbox");
describe('Clean', () => {
    let cleanSandboxDir;
    before(async () => {
        cleanSandboxDir = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('clean'));
    });
    after(async () => {
        await (0, file_helper_1.removeFolders)([cleanSandboxDir]);
    });
    const cliPath = path.join('..', '..', 'cli', 'bin', 'run');
    context('Valid clean', () => {
        it('should clean the project after build', async () => {
            await (0, child_process_promise_1.exec)(`${cliPath} build`, { cwd: cleanSandboxDir });
            (0, chai_1.expect)((0, file_helper_1.fileExists)(path.join(cleanSandboxDir, 'dist'))).to.be.true;
            const expectedCleanOutputRegex = new RegExp(['boost clean', 'Checking project structure', 'Cleaning project', 'Clean complete'].join('(.|\n)*'));
            const { stdout } = await (0, child_process_promise_1.exec)(`${cliPath} clean`, { cwd: cleanSandboxDir });
            (0, chai_1.expect)(stdout).to.match(expectedCleanOutputRegex);
            (0, chai_1.expect)((0, file_helper_1.fileExists)(path.join(cleanSandboxDir, 'dist'))).to.be.false;
        });
    });
});
