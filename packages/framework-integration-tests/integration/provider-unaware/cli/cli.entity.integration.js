"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const chai_1 = require("chai");
const file_helper_1 = require("../../helper/file-helper");
const path = require("path");
const child_process_promise_1 = require("child-process-promise");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../cli/src/common/sandbox");
const EVENT_ENTITY_ID_PLACEHOLDER = '/* the associated entity ID */';
const ENTITY_REDUCER_PLACEHOLDER = '/* NEW PostWithReducer HERE */';
describe('Entity', () => {
    let entitySandboxDir;
    before(async () => {
        entitySandboxDir = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('entity'));
    });
    after(async () => {
        await (0, file_helper_1.removeFolders)([entitySandboxDir]);
    });
    const cliPath = path.join('..', '..', 'cli', 'bin', 'run');
    context('valid entity', () => {
        describe('without fields', () => {
            it('should create new entity', async () => {
                const expectedOutputRegex = new RegExp(['boost new:entity', 'Verifying project', 'Creating new entity', 'Entity generated'].join('(.|\n)*'), 'm');
                const { stdout } = await (0, child_process_promise_1.exec)(`${cliPath} new:entity Post`, { cwd: entitySandboxDir });
                (0, chai_1.expect)(stdout).to.match(expectedOutputRegex);
                const expectedEntityContent = (0, file_helper_1.readFileContent)('integration/fixtures/entities/post.ts');
                const entityContent = (0, file_helper_1.readFileContent)(`${entitySandboxDir}/src/entities/post.ts`);
                (0, chai_1.expect)(entityContent).to.equal(expectedEntityContent);
            });
        });
        describe('with fields', () => {
            it('should create new entity with expected fields', async () => {
                const expectedOutputRegex = new RegExp(['boost new:entity', 'Verifying project', 'Creating new entity', 'Entity generated'].join('(.|\n)*'), 'm');
                const { stdout } = await (0, child_process_promise_1.exec)(`${cliPath} new:entity PostWithFields --fields title:string body:string`, {
                    cwd: entitySandboxDir,
                });
                (0, chai_1.expect)(stdout).to.match(expectedOutputRegex);
                const expectedEntityContent = (0, file_helper_1.readFileContent)('integration/fixtures/entities/post-with-fields.ts');
                const entityContent = (0, file_helper_1.readFileContent)(`${entitySandboxDir}/src/entities/post-with-fields.ts`);
                (0, chai_1.expect)(entityContent).to.equal(expectedEntityContent);
            });
        });
        describe('with reducer', () => {
            it('should create new entity with reducer', async () => {
                const FILE_POST_WITH_REDUCER_ENTITY = `${entitySandboxDir}/src/entities/post-with-reducer.ts`;
                const FILE_POST_CREATED_EVENT = `${entitySandboxDir}/src/events/post-created.ts`;
                // Create event
                await (0, child_process_promise_1.exec)(`${cliPath} new:event PostCreated --fields postId:UUID title:string body:string`, {
                    cwd: entitySandboxDir,
                });
                const expectedEventContent = (0, file_helper_1.loadFixture)('events/post-created.ts');
                const eventContent = (0, file_helper_1.readFileContent)(FILE_POST_CREATED_EVENT);
                (0, chai_1.expect)(eventContent).to.equal(expectedEventContent);
                // Set event entity ID
                const updatedEventContent = eventContent.replace(EVENT_ENTITY_ID_PLACEHOLDER, 'this.postId');
                (0, file_helper_1.writeFileContent)(FILE_POST_CREATED_EVENT, updatedEventContent);
                // Create entity
                await (0, child_process_promise_1.exec)(`${cliPath} new:entity PostWithReducer --fields title:string body:string --reduces PostCreated`, {
                    cwd: entitySandboxDir,
                });
                const expectedEntityContent = (0, file_helper_1.loadFixture)('entities/post-with-reducer.ts');
                const entityContent = (0, file_helper_1.readFileContent)(FILE_POST_WITH_REDUCER_ENTITY);
                (0, chai_1.expect)(entityContent).to.equal(expectedEntityContent);
                // Set reducer response
                const updatedEntityContent = entityContent.replace(ENTITY_REDUCER_PLACEHOLDER, 'new PostWithReducer(event.postId, event.title, event.body)');
                (0, file_helper_1.writeFileContent)(FILE_POST_WITH_REDUCER_ENTITY, updatedEntityContent);
            });
        });
    });
    context('invalid entity', () => {
        describe('missing entity name', () => {
            it('should fail', async () => {
                const { stderr } = await (0, child_process_promise_1.exec)(`${cliPath} new:entity`, { cwd: entitySandboxDir });
                (0, chai_1.expect)(stderr).to.match(/You haven't provided an entity name, but it is required, run with --help for usage/m);
            });
        });
    });
});
