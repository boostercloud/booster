"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const path = require("path");
const chai_1 = require("chai");
const file_helper_1 = require("../../helper/file-helper");
const child_process_promise_1 = require("child-process-promise");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../cli/src/common/sandbox");
describe('Type', () => {
    let typeSandboxDir;
    before(async () => {
        typeSandboxDir = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('type'));
    });
    after(async () => {
        await (0, file_helper_1.removeFolders)([typeSandboxDir]);
    });
    const cliPath = path.join('..', '..', 'cli', 'bin', 'run');
    context('Valid type', () => {
        it('should create a new type', async () => {
            const expectedOutputRegex = new RegExp(['boost new:type', 'Verifying project', 'Creating new type', 'Type generated'].join('(.|\n)*'));
            const { stdout } = await (0, child_process_promise_1.exec)(`${cliPath} new:type Item`, { cwd: typeSandboxDir });
            (0, chai_1.expect)(stdout).to.match(expectedOutputRegex);
            const expectedTypeContent = (0, file_helper_1.loadFixture)('common/item.ts');
            const typeContent = (0, file_helper_1.readFileContent)(`${typeSandboxDir}/src/common/item.ts`);
            (0, chai_1.expect)(typeContent).to.equal(expectedTypeContent);
        });
        describe('with fields', () => {
            it('should create a new type with fields', async () => {
                await (0, child_process_promise_1.exec)(`${cliPath} new:type ItemWithFields --fields sku:string quantity:number`, {
                    cwd: typeSandboxDir,
                });
                const expectedTypeContent = (0, file_helper_1.loadFixture)('common/item-with-fields.ts');
                const typeContent = (0, file_helper_1.readFileContent)(`${typeSandboxDir}/src/common/item-with-fields.ts`);
                (0, chai_1.expect)(typeContent).to.equal(expectedTypeContent);
            });
        });
    });
    context('Invalid type', () => {
        describe('missing type name', () => {
            it('should fail', async () => {
                const { stderr } = await (0, child_process_promise_1.exec)(`${cliPath} new:type`, { cwd: typeSandboxDir });
                (0, chai_1.expect)(stderr).to.match(/You haven't provided a type name, but it is required, run with --help for usage/);
            });
        });
    });
});
