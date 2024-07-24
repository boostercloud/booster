"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const path = require("path");
const fs = require("fs-extra");
const Mustache = require("mustache");
const projectChecker = require("../../src/services/project-checker");
const generator_1 = require("../../src/services/generator");
const sinon_1 = require("sinon");
const expect_1 = require("../expect");
describe('generate service', () => {
    beforeEach(() => {
        (0, sinon_1.replace)(fs, 'outputFile', sinon_1.fake.resolves({}));
        (0, sinon_1.replace)(projectChecker, 'checkResourceExists', sinon_1.fake.resolves({}));
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('generates file from default template', () => {
        it('command', async () => {
            const info = {
                name: 'NewCommand',
                fields: [
                    { name: 'name', type: 'string' },
                    { name: 'description', type: 'string' },
                ],
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'commands'),
                template: (0, generator_1.template)('command'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch('src/commands/new-command.ts', rendered);
            (0, expect_1.expect)(projectChecker.checkResourceExists).to.have.been.called;
            (0, expect_1.expect)(rendered).not.to.contain('-> Custom code in the command!');
        });
        it('entity', async () => {
            const info = {
                name: 'NewEntity',
                fields: [
                    { name: 'name', type: 'string' },
                    { name: 'description', type: 'string' },
                ],
                events: [{ eventName: 'product-purchased' }],
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'entities'),
                template: (0, generator_1.template)('entity'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch('src/entities/new-entity.ts', rendered);
            (0, expect_1.expect)(projectChecker.checkResourceExists).to.have.been.called;
            (0, expect_1.expect)(rendered).not.to.contain('-> Custom code in the entity!');
        });
        it('event handler', async () => {
            const info = {
                name: 'NewEventHandler',
                event: 'product-purchased',
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'event-handlers'),
                template: (0, generator_1.template)('event-handler'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch('src/event-handlers/new-event-handler.ts', rendered);
            (0, expect_1.expect)(projectChecker.checkResourceExists).to.have.been.called;
            (0, expect_1.expect)(rendered).not.to.contain('-> Custom code in the event handler!');
        });
        it('event', async () => {
            const info = {
                name: 'NewEvent',
                fields: [
                    { name: 'name', type: 'string' },
                    { name: 'description', type: 'string' },
                ],
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'events'),
                template: (0, generator_1.template)('event'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch('src/events/new-event.ts', rendered);
            (0, expect_1.expect)(projectChecker.checkResourceExists).to.have.been.called;
            (0, expect_1.expect)(rendered).not.to.contain('-> Custom code in the event!');
        });
        it('read model', async () => {
            const info = {
                name: 'NewReadModel',
                fields: [
                    { name: 'name', type: 'string' },
                    { name: 'description', type: 'string' },
                ],
                projections: [{ entityName: 'account', entityId: 'id' }],
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'read-models'),
                template: (0, generator_1.template)('read-model'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch('src/read-models/new-read-model.ts', rendered);
            (0, expect_1.expect)(projectChecker.checkResourceExists).to.have.been.called;
            (0, expect_1.expect)(rendered).not.to.contain('-> Custom code in the read model!');
        });
        it('scheduled command', async () => {
            const info = {
                name: 'NewScheduledCommand',
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'scheduled-commands'),
                template: (0, generator_1.template)('scheduled-command'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch('src/scheduled-commands/new-scheduled-command.ts', rendered);
            (0, expect_1.expect)(projectChecker.checkResourceExists).to.have.been.called;
            (0, expect_1.expect)(rendered).not.to.contain('-> Custom code in the scheduled command!');
        });
        it('type', async () => {
            const info = {
                name: 'NewType',
                fields: [
                    { name: 'name', type: 'string' },
                    { name: 'description', type: 'string' },
                ],
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'common'),
                template: (0, generator_1.template)('type'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch('src/common/new-type.ts', rendered);
            (0, expect_1.expect)(projectChecker.checkResourceExists).to.have.been.called;
            (0, expect_1.expect)(rendered).not.to.contain('-> Custom code in the type!');
        });
    });
    describe('generates file from custom template', () => {
        beforeEach(() => {
            (0, sinon_1.replace)(process, 'cwd', sinon_1.fake.returns(path.join(process.cwd(), 'test', 'fixtures', 'mock_project')));
        });
        it('command', async () => {
            const info = {
                name: 'NewCommand',
                fields: [
                    { name: 'name', type: 'string' },
                    { name: 'description', type: 'string' },
                ],
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'commands'),
                template: (0, generator_1.template)('command'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(rendered).to.contain('-> Custom code in the command!');
        });
        it('entity', async () => {
            const info = {
                name: 'NewEntity',
                fields: [
                    { name: 'name', type: 'string' },
                    { name: 'description', type: 'string' },
                ],
                events: [{ eventName: 'product-purchased' }],
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'entities'),
                template: (0, generator_1.template)('entity'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(rendered).to.contain('-> Custom code in the entity!');
        });
        it('event handler', async () => {
            const info = {
                name: 'NewEventHandler',
                event: 'product-purchased',
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'event-handlers'),
                template: (0, generator_1.template)('event-handler'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(rendered).to.contain('-> Custom code in the event handler!');
        });
        it('event', async () => {
            const info = {
                name: 'NewEvent',
                fields: [
                    { name: 'name', type: 'string' },
                    { name: 'description', type: 'string' },
                ],
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'events'),
                template: (0, generator_1.template)('event'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(rendered).to.contain('-> Custom code in the event!');
        });
        it('read model', async () => {
            const info = {
                name: 'NewReadModel',
                fields: [
                    { name: 'name', type: 'string' },
                    { name: 'description', type: 'string' },
                ],
                projections: [{ entityName: 'account', entityId: 'id' }],
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'read-models'),
                template: (0, generator_1.template)('read-model'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(rendered).to.contain('-> Custom code in the read model!');
        });
        it('scheduled command', async () => {
            const info = {
                name: 'NewScheduledCommand',
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'scheduled-commands'),
                template: (0, generator_1.template)('scheduled-command'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(rendered).to.contain('-> Custom code in the scheduled command!');
        });
        it('type', async () => {
            const info = {
                name: 'NewType',
                fields: [
                    { name: 'name', type: 'string' },
                    { name: 'description', type: 'string' },
                ],
            };
            const target = {
                name: info.name,
                extension: '.ts',
                placementDir: path.join('src', 'common'),
                template: (0, generator_1.template)('type'),
                info: info,
            };
            const rendered = Mustache.render(target.template, { ...target.info });
            await (0, generator_1.generate)(target);
            (0, expect_1.expect)(rendered).to.contain('-> Custom code in the type!');
        });
    });
});
