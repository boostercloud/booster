"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const path = require("path");
const chai_1 = require("chai");
const file_helper_1 = require("../../helper/file-helper");
const child_process_promise_1 = require("child-process-promise");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../cli/src/common/sandbox");
describe('Scheduled Command', () => {
    let scheduledCommandSandboxDir;
    before(async () => {
        scheduledCommandSandboxDir = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('scheduled-command'));
    });
    after(async () => {
        await (0, file_helper_1.removeFolders)([scheduledCommandSandboxDir]);
    });
    const cliPath = path.join('..', '..', 'cli', 'bin', 'run');
    context('Valid scheduled command', () => {
        it('should create a new scheduled command', async () => {
            const expectedOutputRegex = new RegExp([
                'boost new:scheduled-command',
                'Verifying project',
                'Creating new scheduled command',
                'Scheduled command generated',
            ].join('(.|\n)*'));
            const { stdout } = await (0, child_process_promise_1.exec)(`${cliPath} new:scheduled-command CheckCart`, { cwd: scheduledCommandSandboxDir });
            (0, chai_1.expect)(stdout).to.match(expectedOutputRegex);
            const expectedCommandContent = (0, file_helper_1.loadFixture)('scheduled-commands/check-cart.ts');
            const commandContent = (0, file_helper_1.readFileContent)(path.join(scheduledCommandSandboxDir, 'src', 'scheduled-commands', 'check-cart.ts'));
            (0, chai_1.expect)(commandContent).to.equal(expectedCommandContent);
        });
    });
    context('Invalid scheduled command', () => {
        describe('missing scheduled command name', () => {
            it('should fail', async () => {
                const { stderr } = await (0, child_process_promise_1.exec)(`${cliPath} new:scheduled-command`, { cwd: scheduledCommandSandboxDir });
                (0, chai_1.expect)(stderr).to.match(/You haven't provided a scheduled command name, but it is required, run with --help for usage/);
            });
        });
    });
});
