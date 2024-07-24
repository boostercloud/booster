"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const path = require("path");
const chai_1 = require("chai");
const file_helper_1 = require("../../helper/file-helper");
const child_process_promise_1 = require("child-process-promise");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../cli/src/common/sandbox");
const COMMAND_AUTH_PLACEHOLDER = "// Specify authorized roles here. Use 'all' to authorize anyone";
describe('Command', () => {
    let commandSandboxDir;
    before(async () => {
        commandSandboxDir = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('command'));
    });
    after(async () => {
        await (0, file_helper_1.removeFolders)([commandSandboxDir]);
    });
    const cliPath = path.join('..', '..', 'cli', 'bin', 'run');
    context('Valid command', () => {
        it('should create a new command', async () => {
            const changeCartCommandPath = `${commandSandboxDir}/src/commands/change-cart.ts`;
            const expectedOutputRegex = new RegExp(['boost new:command', 'Verifying project', 'Creating new command', 'Command generated'].join('(.|\n)*'));
            const { stdout } = await (0, child_process_promise_1.exec)(`${cliPath} new:command ChangeCart`, { cwd: commandSandboxDir });
            (0, chai_1.expect)(stdout).to.match(expectedOutputRegex);
            const expectedCommandContent = (0, file_helper_1.loadFixture)('commands/change-cart.ts');
            const commandContent = (0, file_helper_1.readFileContent)(changeCartCommandPath);
            (0, chai_1.expect)(commandContent).to.equal(expectedCommandContent);
            // Set command auth
            const updatedCommandContent = commandContent.replace(COMMAND_AUTH_PLACEHOLDER, "'all'");
            (0, file_helper_1.writeFileContent)(changeCartCommandPath, updatedCommandContent);
        });
        describe('with fields', () => {
            it('should create a new command with fields', async () => {
                const changeCartWithFieldsCommandPath = `${commandSandboxDir}/src/commands/change-cart-with-fields.ts`;
                await (0, child_process_promise_1.exec)(`${cliPath} new:command ChangeCartWithFields --fields cartId:UUID sku:string quantity:number`, {
                    cwd: commandSandboxDir,
                });
                const expectedCommandContent = (0, file_helper_1.loadFixture)('commands/change-cart-with-fields.ts');
                const commandContent = (0, file_helper_1.readFileContent)(changeCartWithFieldsCommandPath);
                (0, chai_1.expect)(commandContent).to.equal(expectedCommandContent);
                // Set command auth
                const updatedCommandContent = commandContent.replace(COMMAND_AUTH_PLACEHOLDER, "'all'");
                (0, file_helper_1.writeFileContent)(changeCartWithFieldsCommandPath, updatedCommandContent);
            });
        });
    });
    context('Invalid command', () => {
        describe('missing command name', () => {
            it('should fail', async () => {
                const { stderr } = await (0, child_process_promise_1.exec)(`${cliPath} new:command`, { cwd: commandSandboxDir });
                (0, chai_1.expect)(stderr).to.match(/You haven't provided a command name, but it is required, run with --help for usage/);
            });
        });
    });
});
