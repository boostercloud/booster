"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const path = require("path");
const chai_1 = require("chai");
const file_helper_1 = require("../../helper/file-helper");
const child_process_promise_1 = require("child-process-promise");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../cli/src/common/sandbox");
describe('Event handler', () => {
    let eventHandlerSandboxDir;
    before(async () => {
        eventHandlerSandboxDir = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('event-handler'));
    });
    after(async () => {
        await (0, file_helper_1.removeFolders)([eventHandlerSandboxDir]);
    });
    const cliPath = path.join('..', '..', 'cli', 'bin', 'run');
    describe('Valid event handler', () => {
        it('should create new event handler', async () => {
            const expectedOutputRegex = new RegExp(['boost new:event-handler', 'Verifying project', 'Creating new event handler', 'Event handler generated'].join('(.|\n)*'));
            const { stdout } = await (0, child_process_promise_1.exec)(`${cliPath} new:event-handler HandleCartChange -e CartItemChanged`, {
                cwd: eventHandlerSandboxDir,
            });
            (0, chai_1.expect)(stdout).to.match(expectedOutputRegex);
            const expectedEventContent = (0, file_helper_1.loadFixture)('event-handlers/handle-cart-change.ts');
            const eventContent = (0, file_helper_1.readFileContent)(`${eventHandlerSandboxDir}/src/event-handlers/handle-cart-change.ts`);
            (0, chai_1.expect)(eventContent).to.equal(expectedEventContent);
        });
    });
    describe('Invalid event handler', () => {
        context('without name and event', () => {
            it('should fail', async () => {
                const { stderr } = await (0, child_process_promise_1.exec)(`${cliPath} new:event-handler`, { cwd: eventHandlerSandboxDir });
                (0, chai_1.expect)(stderr).to.match(/You haven't provided an event handler name, but it is required, run with --help for usage/);
            });
        });
        context('Without name', () => {
            it('should fail', async () => {
                const { stderr } = await (0, child_process_promise_1.exec)(`${cliPath} new:event-handler -e CartPaid`, { cwd: eventHandlerSandboxDir });
                (0, chai_1.expect)(stderr).to.match(/You haven't provided an event handler name, but it is required, run with --help for usage/);
            });
        });
        context('Without event', () => {
            it('should fail', async () => {
                const { stderr } = await (0, child_process_promise_1.exec)(`${cliPath} new:event-handler CartPaid`, { cwd: eventHandlerSandboxDir });
                (0, chai_1.expect)(stderr).to.match(/You haven't provided an event, but it is required, run with --help for usage/);
            });
        });
    });
});
