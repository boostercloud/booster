"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const chai_1 = require("chai");
const path = require("path");
const file_helper_1 = require("../../helper/file-helper");
const child_process_promise_1 = require("child-process-promise");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../cli/src/common/sandbox");
const READ_MODEL_AUTH_PLACEHOLDER = "// Specify authorized roles here. Use 'all' to authorize anyone";
const READ_MODEL_PROJECTION_PLACEHOLDER = '/* NEW CartWithProjectionReadModel HERE */';
describe('Read model', () => {
    let readModelSandboxDir;
    before(async () => {
        readModelSandboxDir = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('read-model'));
    });
    after(async () => {
        await (0, file_helper_1.removeFolders)([readModelSandboxDir]);
    });
    const cliPath = path.join('..', '..', 'cli', 'bin', 'run');
    const EXPECTED_OUTPUT_REGEX = new RegExp(['boost new:read-model', 'Verifying project', 'Creating new read model', 'Read model generated'].join('(.|\n)*'));
    context('valid read model', () => {
        describe('without fields', () => {
            it('should create new read model', async () => {
                const FILE_CART_READ_MODEL = `${readModelSandboxDir}/src/read-models/cart-read-model.ts`;
                (0, file_helper_1.removeFiles)([FILE_CART_READ_MODEL]);
                const { stdout } = await (0, child_process_promise_1.exec)(`${cliPath} new:read-model CartReadModel`, { cwd: readModelSandboxDir });
                (0, chai_1.expect)(stdout).to.match(EXPECTED_OUTPUT_REGEX);
                const expectedEntityContent = (0, file_helper_1.loadFixture)('read-models/cart-read-model.ts');
                const entityContent = (0, file_helper_1.readFileContent)(FILE_CART_READ_MODEL);
                (0, chai_1.expect)(entityContent).to.equal(expectedEntityContent);
                // set Auth
                const updatedReadModelContent = entityContent.replace(READ_MODEL_AUTH_PLACEHOLDER, "'all'");
                (0, file_helper_1.writeFileContent)(FILE_CART_READ_MODEL, updatedReadModelContent);
            });
        });
        describe('with fields', () => {
            it('should create new read model', async () => {
                const FILE_CART_WITH_FIELDS_READ_MODEL = `${readModelSandboxDir}/src/read-models/cart-with-fields-read-model.ts`;
                const { stdout } = await (0, child_process_promise_1.exec)(cliPath + " new:read-model CartWithFieldsReadModel --fields 'items:Array<Item>'", { cwd: readModelSandboxDir });
                (0, chai_1.expect)(stdout).to.match(EXPECTED_OUTPUT_REGEX);
                const expectedEntityContent = (0, file_helper_1.loadFixture)('read-models/cart-with-fields-read-model.ts');
                const entityContent = (0, file_helper_1.readFileContent)(FILE_CART_WITH_FIELDS_READ_MODEL);
                (0, chai_1.expect)(entityContent).to.equal(expectedEntityContent);
                // set Auth
                let updatedReadModelContent = entityContent.replace(READ_MODEL_AUTH_PLACEHOLDER, "'all'");
                // Add Item import
                updatedReadModelContent = `import { Item } from '../common/item'\n${updatedReadModelContent}`;
                (0, file_helper_1.writeFileContent)(FILE_CART_WITH_FIELDS_READ_MODEL, updatedReadModelContent);
            });
        });
        describe('with projection', () => {
            it('should create new read model', async () => {
                const FILE_CART_WITH_PROJECTION_READ_MODEL = `${readModelSandboxDir}/src/read-models/cart-with-projection-read-model.ts`;
                const { stdout } = await (0, child_process_promise_1.exec)(cliPath + " new:read-model CartWithProjectionReadModel --fields 'items:Array<Item>' --projects Cart:id", { cwd: readModelSandboxDir });
                (0, chai_1.expect)(stdout).to.match(EXPECTED_OUTPUT_REGEX);
                const expectedEntityContent = (0, file_helper_1.loadFixture)('read-models/cart-with-projection-read-model.ts');
                const entityContent = (0, file_helper_1.readFileContent)(FILE_CART_WITH_PROJECTION_READ_MODEL);
                (0, chai_1.expect)(entityContent).to.equal(expectedEntityContent);
                // set Auth
                let updatedReadModelContent = entityContent.replace(READ_MODEL_AUTH_PLACEHOLDER, "'all'");
                // Set projection return
                updatedReadModelContent = updatedReadModelContent.replace(READ_MODEL_PROJECTION_PLACEHOLDER, 'new CartWithProjectionReadModel(entity.id, entity.cartItems)');
                // Add Item import
                updatedReadModelContent = `import { Item } from '../common/item'\n${updatedReadModelContent}`;
                (0, file_helper_1.writeFileContent)(FILE_CART_WITH_PROJECTION_READ_MODEL, updatedReadModelContent);
            });
        });
    });
    context('invalid read model', () => {
        describe('missing read model name', () => {
            it('should fail', async () => {
                const { stderr } = await (0, child_process_promise_1.exec)(`${cliPath} new:read-model`, { cwd: readModelSandboxDir });
                (0, chai_1.expect)(stderr).to.match(/You haven't provided a read model name, but it is required, run with --help for usage/);
            });
        });
    });
});
