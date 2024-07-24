"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const ProjectChecker = require("../../../src/services/project-checker");
const sinon_1 = require("sinon");
const event_handler_1 = require("../../../src/commands/new/event-handler");
const Mustache = require("mustache");
const fs = require("fs-extra");
const expect_1 = require("../../expect");
const generator_1 = require("../../../src/services/generator");
describe('new', () => {
    describe('Event', () => {
        const eventHandlerName = 'ExampleEventHandler';
        const eventHandlersRoot = 'src/event-handlers/';
        const eventHandlerPath = `${eventHandlersRoot}example-event-handler.ts`;
        const defaultEventHandlerImports = [
            {
                packagePath: '../events/comment-posted',
                commaSeparatedComponents: 'CommentPosted',
            },
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'EventHandler',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'Register',
            },
        ];
        beforeEach(() => {
            (0, sinon_1.stub)(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis();
            (0, sinon_1.replace)(fs, 'outputFile', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(ProjectChecker, 'checkCurrentDirBoosterVersion', sinon_1.fake.resolves({}));
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new event_handler_1.default([], {}).init();
            (0, expect_1.expect)(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        describe('Created correctly', () => {
            it('creates Event with a event', async () => {
                await new event_handler_1.default([eventHandlerName, '--event', 'CommentPosted'], {}).run();
                const renderedEventHandler = Mustache.render((0, generator_1.template)('event-handler'), {
                    imports: defaultEventHandlerImports,
                    name: eventHandlerName,
                    event: 'CommentPosted',
                });
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(eventHandlerPath, renderedEventHandler);
            });
        });
        describe('displays an error', () => {
            it('with no event', async () => {
                (0, sinon_1.replace)(console, 'error', sinon_1.fake.resolves({}));
                await new event_handler_1.default([eventHandlerName], {}).run();
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(eventHandlerPath);
                (0, expect_1.expect)(console.error).to.have.been.calledWithMatch(/You haven't provided an event/);
            });
            it('with empty EventHandler name', async () => {
                (0, sinon_1.replace)(console, 'error', sinon_1.fake.resolves({}));
                await new event_handler_1.default([], {}).run();
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(eventHandlersRoot);
                (0, expect_1.expect)(console.error).to.have.been.calledWithMatch(/You haven't provided an event handler name/);
            });
            it('with empty event', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new event_handler_1.default([eventHandlerName, '--event'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('--event expects a value');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(eventHandlerPath);
            });
            it('creates EventHandler with two events', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new event_handler_1.default([eventHandlerName, '--event', 'CommentPosted', 'ArticlePosted'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Unexpected argument: ArticlePosted');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(eventHandlerPath);
            });
        });
    });
});
