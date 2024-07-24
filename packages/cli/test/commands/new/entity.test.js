"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const ProjectChecker = require("../../../src/services/project-checker");
const sinon_1 = require("sinon");
const entity_1 = require("../../../src/commands/new/entity");
const Mustache = require("mustache");
const fs = require("fs-extra");
const expect_1 = require("../../expect");
const generator_1 = require("../../../src/services/generator");
describe('new', () => {
    describe('Entity', () => {
        const entityName = 'ExampleEntity';
        const entitysRoot = 'src/entities/';
        const entityPath = `${entitysRoot}example-entity.ts`;
        const defaultEntityImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'Entity',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'UUID',
            },
        ];
        const reducingEntityImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'Entity, Reduces',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'UUID',
            },
            {
                packagePath: '../events/post-created',
                commaSeparatedComponents: 'PostCreated',
            },
        ];
        const reducingTwoEntityImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'Entity, Reduces',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'UUID',
            },
            {
                packagePath: '../events/post-created',
                commaSeparatedComponents: 'PostCreated',
            },
            {
                packagePath: '../events/comment-created',
                commaSeparatedComponents: 'CommentCreated',
            },
        ];
        const renderEntity = (imports, name, fields, events) => {
            return Mustache.render((0, generator_1.template)('entity'), {
                imports: imports,
                name: name,
                fields: fields,
                events: events,
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
            await new entity_1.default([], {}).init();
            (0, expect_1.expect)(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        describe('Created correctly', () => {
            it('with no fields and no reduces', async () => {
                await new entity_1.default([entityName], {}).run();
                const renderedEntity = renderEntity(defaultEntityImports, entityName, [], []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity);
            });
            it('creates Entity with a string field', async () => {
                await new entity_1.default([entityName, '--fields', 'title:string'], {}).run();
                const renderedEntity = renderEntity(defaultEntityImports, entityName, [{ name: 'title', type: 'string' }], []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity);
            });
            it('creates Entity with a string field reducing PostCreated', async () => {
                await new entity_1.default([entityName, '--fields', 'title:string', '--reduces', 'PostCreated'], {}).run();
                const renderedEntity = renderEntity(reducingEntityImports, entityName, [{ name: 'title', type: 'string' }], [{ eventName: 'PostCreated' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity);
            });
            it('creates Entity with a number field', async () => {
                await new entity_1.default([entityName, '--fields', 'quantity:number'], {}).run();
                const renderedEntity = renderEntity(defaultEntityImports, entityName, [{ name: 'quantity', type: 'number' }], []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity);
            });
            it('creates Entity with a number field reducing PostCreated', async () => {
                await new entity_1.default([entityName, '--fields', 'quantity:number', '--reduces', 'PostCreated'], {}).run();
                const renderedEntity = renderEntity(reducingEntityImports, entityName, [{ name: 'quantity', type: 'number' }], [{ eventName: 'PostCreated' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity);
            });
            it('creates Entity with UUID field', async () => {
                await new entity_1.default([entityName, '--fields', 'identifier:UUID'], {}).run();
                const renderedEntity = renderEntity(defaultEntityImports, entityName, [{ name: 'identifier', type: 'UUID' }], []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity);
            });
            it('creates Entity with UUID field reducing PostCreated', async () => {
                await new entity_1.default([entityName, '--fields', 'identifier:UUID', '--reduces', 'PostCreated'], {}).run();
                const renderedEntity = renderEntity(reducingEntityImports, entityName, [{ name: 'identifier', type: 'UUID' }], [{ eventName: 'PostCreated' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity);
            });
            it('creates Entity with multiple fields', async () => {
                await new entity_1.default([entityName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'], {}).run();
                const fields = [
                    { name: 'title', type: 'string' },
                    { name: 'quantity', type: 'number' },
                    { name: 'identifier', type: 'UUID' },
                ];
                const renderedEntity = renderEntity(defaultEntityImports, entityName, fields, []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity);
            });
            it('creates Entity with multiple fields reducing PostCreated', async () => {
                await new entity_1.default([entityName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID', '--reduces', 'PostCreated'], {}).run();
                const fields = [
                    { name: 'title', type: 'string' },
                    { name: 'quantity', type: 'number' },
                    { name: 'identifier', type: 'UUID' },
                ];
                const renderedEntity = renderEntity(reducingEntityImports, entityName, fields, [{ eventName: 'PostCreated' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity);
            });
            it('creates Entity with multiple fields reducing PostCreated and CommentCreated', async () => {
                await new entity_1.default([
                    entityName,
                    '--fields',
                    'title:string',
                    'quantity:number',
                    'identifier:UUID',
                    '--reduces',
                    'PostCreated',
                    'CommentCreated',
                ], {}).run();
                const fields = [
                    { name: 'title', type: 'string' },
                    { name: 'quantity', type: 'number' },
                    { name: 'identifier', type: 'UUID' },
                ];
                const renderedEntity = renderEntity(reducingTwoEntityImports, entityName, fields, [
                    { eventName: 'PostCreated' },
                    { eventName: 'CommentCreated' },
                ]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(entityPath, renderedEntity);
            });
        });
        describe('displays an error', () => {
            it('with empty Entity name', async () => {
                (0, sinon_1.replace)(console, 'error', sinon_1.fake.resolves({}));
                await new entity_1.default([], {}).run();
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(entitysRoot);
                (0, expect_1.expect)(console.error).to.have.been.calledWithMatch(/You haven't provided an entity name/);
            });
            it('with empty fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new entity_1.default([entityName, '--fields'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('--fields expects a value');
            });
            it('with empty reduces', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new entity_1.default([entityName, '--fields', 'title:string', '--reduces'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('--reduces expects a value');
            });
            it('with empty fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new entity_1.default([entityName, '--fields', '--reduces'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Flag --fields expects a value');
            });
            it('with empty reduces', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new entity_1.default([entityName, '--fields', 'title', '--reduces'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Flag --reduces expects a value');
            });
            it('with field with no type', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new entity_1.default([entityName, '--fields', 'title'], {}).run();
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
                    await new entity_1.default([entityName, '--fields', 'title:'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(entityPath);
            });
            it('with repeated fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new entity_1.default([entityName, '--fields', 'title:string', 'title:string', 'quantity:number'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(entityPath);
            });
        });
    });
});
