"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const ProjectChecker = require("../../../src/services/project-checker");
const sinon_1 = require("sinon");
const event_1 = require("../../../src/commands/new/event");
const Mustache = require("mustache");
const fs = require("fs-extra");
const expect_1 = require("../../expect");
const generator_1 = require("../../../src/services/generator");
describe('new', () => {
    describe('Event', () => {
        const eventName = 'ExampleEvent';
        const eventsRoot = 'src/events/';
        const eventPath = `${eventsRoot}example-event.ts`;
        const defaultEventImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'Event',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'UUID',
            },
        ];
        const renderEvent = (name, fields) => {
            return Mustache.render((0, generator_1.template)('event'), {
                imports: defaultEventImports,
                name: name,
                fields: fields,
            });
        };
        beforeEach(() => {
            (0, sinon_1.stub)(ProjectChecker, 'checkCurrentDirIsABoosterProject').returnsThis();
            (0, sinon_1.replace)(fs, 'outputFile', sinon_1.fake.resolves({}));
            (0, sinon_1.replace)(ProjectChecker, 'checkCurrentDirBoosterVersion', sinon_1.fake.resolves({}));
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new event_1.default([], {}).init();
            (0, expect_1.expect)(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        describe('Created correctly', () => {
            it('with no fields', async () => {
                await new event_1.default([eventName], {}).run();
                const renderedEvent = renderEvent(eventName, []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(eventPath, renderedEvent);
            });
            it('creates Event with a string field', async () => {
                await new event_1.default([eventName, '--fields', 'title:string'], {}).run();
                const renderedEvent = renderEvent(eventName, [{ name: 'title', type: 'string' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(eventPath, renderedEvent);
            });
            it('creates Event with a number field', async () => {
                await new event_1.default([eventName, '--fields', 'quantity:number'], {}).run();
                const renderedEvent = renderEvent(eventName, [{ name: 'quantity', type: 'number' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(eventPath, renderedEvent);
            });
            it('creates Event with UUID field', async () => {
                await new event_1.default([eventName, '--fields', 'identifier:UUID'], {}).run();
                const renderedEvent = renderEvent(eventName, [{ name: 'identifier', type: 'UUID' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(eventPath, renderedEvent);
            });
            it('creates Event with multiple fields', async () => {
                await new event_1.default([eventName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'], {}).run();
                const fields = [
                    { name: 'title', type: 'string' },
                    { name: 'quantity', type: 'number' },
                    { name: 'identifier', type: 'UUID' },
                ];
                const renderedEvent = renderEvent(eventName, fields);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(eventPath, renderedEvent);
            });
        });
        describe('displays an error', () => {
            it('with empty Event name', async () => {
                (0, sinon_1.replace)(console, 'error', sinon_1.fake.resolves({}));
                await new event_1.default([], {}).run();
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(eventsRoot);
                (0, expect_1.expect)(console.error).to.have.been.calledWithMatch(/You haven't provided an event name/);
            });
            it('with empty fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new event_1.default([eventName, '--fields'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('--fields expects a value');
            });
            it('with field with no type', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new event_1.default([eventName, '--fields', 'title'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
            });
            it('with no field type after :', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new event_1.default([eventName, '--fields', 'title:'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(eventPath);
            });
            it('with repeated fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new event_1.default([eventName, '--fields', 'title:string', 'title:string', 'quantity:number'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(eventPath);
            });
        });
    });
});
