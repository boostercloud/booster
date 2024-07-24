"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const ProjectChecker = require("../../../src/services/project-checker");
const sinon_1 = require("sinon");
const read_model_1 = require("../../../src/commands/new/read-model");
const Mustache = require("mustache");
const fs = require("fs-extra");
const expect_1 = require("../../expect");
const generator_1 = require("../../../src/services/generator");
describe('new', () => {
    describe('ReadModel', () => {
        const readModelName = 'ExampleReadModel';
        const readModelsRoot = 'src/read-models/';
        const readModelPath = `${readModelsRoot}example-read-model.ts`;
        const defaultReadModelImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'ReadModel',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'UUID',
            },
        ];
        const projectingReadModelImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'ReadModel, Projects',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'UUID, ProjectionResult',
            },
            {
                packagePath: '../entities/post',
                commaSeparatedComponents: 'Post',
            },
        ];
        const projectingTwoReadModelImports = [
            {
                packagePath: '@boostercloud/framework-core',
                commaSeparatedComponents: 'ReadModel, Projects',
            },
            {
                packagePath: '@boostercloud/framework-types',
                commaSeparatedComponents: 'UUID, ProjectionResult',
            },
            {
                packagePath: '../entities/post',
                commaSeparatedComponents: 'Post',
            },
            {
                packagePath: '../entities/comment',
                commaSeparatedComponents: 'Comment',
            },
        ];
        const renderReadModel = (imports, name, fields, projections) => {
            return Mustache.render((0, generator_1.template)('read-model'), {
                imports: imports,
                name: name,
                fields: fields,
                projections: projections,
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
            await new read_model_1.default([], {}).init();
            (0, expect_1.expect)(ProjectChecker.checkCurrentDirBoosterVersion).to.have.been.called;
        });
        describe('Created correctly', () => {
            it('with no fields and no projects', async () => {
                await new read_model_1.default([readModelName], {}).run();
                const renderedReadModel = renderReadModel(defaultReadModelImports, readModelName, [], []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel);
            });
            it('creates ReadModel with a string field', async () => {
                await new read_model_1.default([readModelName, '--fields', 'title:string'], {}).run();
                const renderedReadModel = renderReadModel(defaultReadModelImports, readModelName, [{ name: 'title', type: 'string' }], []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel);
            });
            it('creates ReadModel with a string field projecting Post:id', async () => {
                await new read_model_1.default([readModelName, '--fields', 'title:string', '--projects', 'Post:id'], {}).run();
                const renderedReadModel = renderReadModel(projectingReadModelImports, readModelName, [{ name: 'title', type: 'string' }], [{ entityName: 'Post', entityId: 'id' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel);
            });
            it('creates ReadModel with a number field', async () => {
                await new read_model_1.default([readModelName, '--fields', 'quantity:number'], {}).run();
                const renderedReadModel = renderReadModel(defaultReadModelImports, readModelName, [{ name: 'quantity', type: 'number' }], []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel);
            });
            it('creates ReadModel with a number field projecting Post:id', async () => {
                await new read_model_1.default([readModelName, '--fields', 'quantity:number', '--projects', 'Post:id'], {}).run();
                const renderedReadModel = renderReadModel(projectingReadModelImports, readModelName, [{ name: 'quantity', type: 'number' }], [{ entityName: 'Post', entityId: 'id' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel);
            });
            it('creates ReadModel with UUID field', async () => {
                await new read_model_1.default([readModelName, '--fields', 'identifier:UUID'], {}).run();
                const renderedReadModel = renderReadModel(defaultReadModelImports, readModelName, [{ name: 'identifier', type: 'UUID' }], []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel);
            });
            it('creates ReadModel with UUID field projecting Post:id', async () => {
                await new read_model_1.default([readModelName, '--fields', 'identifier:UUID', '--projects', 'Post:id'], {}).run();
                const renderedReadModel = renderReadModel(projectingReadModelImports, readModelName, [{ name: 'identifier', type: 'UUID' }], [{ entityName: 'Post', entityId: 'id' }]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel);
            });
            it('creates ReadModel with multiple fields', async () => {
                await new read_model_1.default([readModelName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID'], {}).run();
                const fields = [
                    { name: 'title', type: 'string' },
                    { name: 'quantity', type: 'number' },
                    { name: 'identifier', type: 'UUID' },
                ];
                const renderedReadModel = renderReadModel(defaultReadModelImports, readModelName, fields, []);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel);
            });
            it('creates ReadModel with multiple fields projecting Post:id', async () => {
                await new read_model_1.default([readModelName, '--fields', 'title:string', 'quantity:number', 'identifier:UUID', '--projects', 'Post:id'], {}).run();
                const fields = [
                    { name: 'title', type: 'string' },
                    { name: 'quantity', type: 'number' },
                    { name: 'identifier', type: 'UUID' },
                ];
                const renderedReadModel = renderReadModel(projectingReadModelImports, readModelName, fields, [
                    { entityName: 'Post', entityId: 'id' },
                ]);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel);
            });
            it('creates ReadModel with multiple fields projecting Post:id and Comment:id', async () => {
                await new read_model_1.default([
                    readModelName,
                    '--fields',
                    'title:string',
                    'quantity:number',
                    'identifier:UUID',
                    '--projects',
                    'Post:id',
                    'Comment:id',
                ], {}).run();
                const fields = [
                    { name: 'title', type: 'string' },
                    { name: 'quantity', type: 'number' },
                    { name: 'identifier', type: 'UUID' },
                ];
                const projections = [
                    { entityName: 'Post', entityId: 'id' },
                    { entityName: 'Comment', entityId: 'id' },
                ];
                const renderedReadModel = renderReadModel(projectingTwoReadModelImports, readModelName, fields, projections);
                (0, expect_1.expect)(fs.outputFile).to.have.been.calledWithMatch(readModelPath, renderedReadModel);
            });
        });
        describe('displays an error', () => {
            it('with empty ReadModel name', async () => {
                (0, sinon_1.replace)(console, 'error', sinon_1.fake.resolves({}));
                await new read_model_1.default([], {}).run();
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(readModelsRoot);
                (0, expect_1.expect)(console.error).to.have.been.calledWithMatch(/You haven't provided a read model name/);
            });
            it('with empty fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new read_model_1.default([readModelName, '--fields'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('--fields expects a value');
            });
            it('with empty projection', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new read_model_1.default([readModelName, '--fields', 'title:string', '--projects'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('--projects expects a value');
            });
            it('with empty fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new read_model_1.default([readModelName, '--fields'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Flag --fields expects a value');
            });
            it('with empty projection', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new read_model_1.default([readModelName, '--projects'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Flag --projects expects a value');
            });
            it('with field with no type', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new read_model_1.default([readModelName, '--fields', 'title'], {}).run();
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
                    await new read_model_1.default([readModelName, '--fields', 'title:'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing field title');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath);
            });
            it('with projection with no entity id', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new read_model_1.default([readModelName, '--fields', 'title:string', '--projects', 'Post'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection Post');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath);
            });
            it('with projection with empty entity id', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new read_model_1.default([readModelName, '--fields', 'title:string', '--projects', 'Post:'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection Post');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath);
            });
            it('with projection with empty entity name', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new read_model_1.default([readModelName, '--fields', 'title:string', '--projects', ':id'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Error parsing projection :id');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath);
            });
            it('with repeated fields', async () => {
                let exceptionThrown = false;
                let exceptionMessage = '';
                try {
                    await new read_model_1.default([readModelName, '--fields', 'title:string', 'title:string', 'quantity:number'], {}).run();
                }
                catch (e) {
                    exceptionThrown = true;
                    exceptionMessage = e.message;
                }
                (0, expect_1.expect)(exceptionThrown).to.be.equal(true);
                (0, expect_1.expect)(exceptionMessage).to.contain('Fields cannot be duplicated');
                (0, expect_1.expect)(fs.outputFile).to.have.not.been.calledWithMatch(readModelPath);
            });
        });
    });
});
