"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const path = require("path");
const chai_1 = require("chai");
const file_helper_1 = require("../../helper/file-helper");
const child_process_promise_1 = require("child-process-promise");
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
const sandbox_1 = require("../../../../cli/src/common/sandbox");
const EVENT_ENTITY_ID_PLACEHOLDER = '/* the associated entity ID */';
describe('Event', () => {
    let eventSandboxDir;
    before(async () => {
        eventSandboxDir = (0, sandbox_1.createSandboxProject)((0, file_helper_1.sandboxPathFor)('event'));
    });
    after(async () => {
        await (0, file_helper_1.removeFolders)([eventSandboxDir]);
    });
    const cliPath = path.join('..', '..', 'cli', 'bin', 'run');
    context('Valid event', () => {
        it('should create new event', async () => {
            const expectedOutputRegex = new RegExp(['boost new:event', 'Verifying project', 'Creating new event', 'Event generated'].join('(.|\n)*'));
            const { stdout } = await (0, child_process_promise_1.exec)(`${cliPath} new:event CartChanged`, { cwd: eventSandboxDir });
            (0, chai_1.expect)(stdout).to.match(expectedOutputRegex);
        });
        describe('without fields', () => {
            it('should create new event', async () => {
                const FILE_CART_CHANGED_EVENT = `${eventSandboxDir}/src/events/cart-changed.ts`;
                (0, file_helper_1.removeFiles)([FILE_CART_CHANGED_EVENT]);
                await (0, child_process_promise_1.exec)(`${cliPath} new:event CartChanged`, { cwd: eventSandboxDir });
                const expectedEventContent = (0, file_helper_1.loadFixture)('events/cart-changed.ts');
                const eventContent = (0, file_helper_1.readFileContent)(FILE_CART_CHANGED_EVENT);
                (0, chai_1.expect)(eventContent).to.equal(expectedEventContent);
                // Set event entity ID
                const updatedEventContent = eventContent.replace(EVENT_ENTITY_ID_PLACEHOLDER, "'some-id'");
                (0, file_helper_1.writeFileContent)(FILE_CART_CHANGED_EVENT, updatedEventContent);
            });
        });
        describe('with fields', () => {
            it('should create new event', async () => {
                const FILE_CART_CHANGED_WITH_FIELDS_EVENT = `${eventSandboxDir}/src/events/cart-changed-with-fields.ts`;
                await (0, child_process_promise_1.exec)(`${cliPath} new:event CartChangedWithFields --fields cartId:UUID sku:string quantity:number`, {
                    cwd: eventSandboxDir,
                });
                const expectedEventContent = (0, file_helper_1.loadFixture)('events/cart-changed-with-fields.ts');
                const eventContent = (0, file_helper_1.readFileContent)(FILE_CART_CHANGED_WITH_FIELDS_EVENT);
                (0, chai_1.expect)(eventContent).to.equal(expectedEventContent);
                // Set event entity ID
                const updatedEventContent = eventContent.replace(EVENT_ENTITY_ID_PLACEHOLDER, 'this.cartId');
                (0, file_helper_1.writeFileContent)(FILE_CART_CHANGED_WITH_FIELDS_EVENT, updatedEventContent);
            });
        });
    });
    context('Invalid event', () => {
        describe('missing event name', () => {
            it('should fail', async () => {
                const { stderr } = await (0, child_process_promise_1.exec)(`${cliPath} new:event`, { cwd: eventSandboxDir });
                (0, chai_1.expect)(stderr).to.match(/You haven't provided an event name, but it is required, run with --help for usage/);
            });
        });
    });
});
