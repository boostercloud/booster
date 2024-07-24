"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
const method_generator_1 = require("../../src/services/method-generator");
const sinon_1 = require("sinon");
const Filenames = require("../../src/common/filenames");
const filenames_1 = require("../../src/common/filenames");
const ts_morph_1 = require("ts-morph");
describe('method generator', () => {
    it('generates projection', async () => {
        const info = {
            name: 'PostReadModel',
            projection: { entityName: 'Post', entityId: 'id' },
        };
        const projectionMethodParams = await (0, method_generator_1.generateProjection)(info.name, info.projection);
        (0, expect_1.expect)(projectionMethodParams)
            .to.be.an('object')
            .that.has.all.keys(['decorators', 'scope', 'isStatic', 'name', 'parameters', 'returnType', 'statements']);
        (0, expect_1.expect)(projectionMethodParams).to.be.deep.equal({
            decorators: [
                {
                    name: 'Projects',
                    arguments: [info.projection.entityName, `'${info.projection.entityId}'`],
                },
            ],
            scope: 'public',
            isStatic: true,
            name: `project${info.projection.entityName}`,
            parameters: [
                {
                    name: 'entity',
                    type: info.projection.entityName,
                },
                {
                    name: `current${info.name}`,
                    type: info.name,
                    hasQuestionToken: true,
                },
            ],
            returnType: `ProjectionResult<${info.name}>`,
            statements: [`return /* NEW ${info.name} HERE */`],
        });
    });
    it('generates reducers', async () => {
        it('generates single reducer', async () => {
            const info = {
                name: 'Post',
                events: [{ eventName: 'PostCreated' }],
            };
            const reducerMethodParams = await (0, method_generator_1.generateReducers)(info.name, info.events);
            (0, expect_1.expect)(reducerMethodParams).to.have.lengthOf(1);
            (0, expect_1.expect)(reducerMethodParams[0]).to.deep.include({ name: `reduce${info.events[0].eventName}` });
            (0, expect_1.expect)(reducerMethodParams[0])
                .to.be.an('object')
                .that.has.all.keys(['decorators', 'scope', 'isStatic', 'name', 'parameters', 'returnType', 'statements']);
            (0, expect_1.expect)(reducerMethodParams).to.have.deep.members([
                {
                    decorators: [
                        {
                            name: 'Reduces',
                            arguments: [info.events[0].eventName],
                        },
                    ],
                    scope: 'public',
                    isStatic: true,
                    name: `reduce${info.events[0].eventName}`,
                    returnType: info.name,
                    parameters: [
                        {
                            name: 'event',
                            type: info.events[0].eventName,
                        },
                        {
                            name: `current${info.name}`,
                            type: info.name,
                            hasQuestionToken: true,
                        },
                    ],
                    statements: [`return /* NEW ${info.name} HERE */`],
                },
            ]);
        });
        it('generates multiple reducers', async () => {
            const info = {
                name: 'Post',
                events: [{ eventName: 'PostCreated' }, { eventName: 'PostUpdated' }],
            };
            const reducerMethodParams = await (0, method_generator_1.generateReducers)(info.name, info.events);
            (0, expect_1.expect)(reducerMethodParams).to.have.lengthOf(2);
            reducerMethodParams.forEach((member, index) => {
                (0, expect_1.expect)(member.name).to.be.equal(`reduce${info.events[index].eventName}`);
                (0, expect_1.expect)(member)
                    .to.be.an('object')
                    .that.has.all.keys(['decorators', 'scope', 'isStatic', 'name', 'parameters', 'returnType', 'statements']);
                (0, expect_1.expect)(member).to.deep.include({
                    scope: 'public',
                    isStatic: true,
                    returnType: info.name,
                    statements: [`return /* NEW ${info.name} HERE */`],
                    decorators: [
                        {
                            name: 'Reduces',
                            arguments: [info.events[index].eventName],
                        },
                    ],
                    name: `reduce${info.events[index].eventName}`,
                    parameters: [
                        {
                            name: 'event',
                            type: info.events[index].eventName,
                        },
                        {
                            name: `current${info.name}`,
                            type: info.name,
                            hasQuestionToken: true,
                        },
                    ],
                });
            });
            (0, expect_1.expect)(reducerMethodParams).to.have.deep.members([
                {
                    scope: 'public',
                    isStatic: true,
                    returnType: info.name,
                    statements: [`return /* NEW ${info.name} HERE */`],
                    decorators: [
                        {
                            name: 'Reduces',
                            arguments: [info.events[0].eventName],
                        },
                    ],
                    name: `reduce${info.events[0].eventName}`,
                    parameters: [
                        {
                            name: 'event',
                            type: info.events[0].eventName,
                        },
                        {
                            name: `current${info.name}`,
                            type: info.name,
                            hasQuestionToken: true,
                        },
                    ],
                },
                {
                    scope: 'public',
                    isStatic: true,
                    returnType: info.name,
                    statements: [`return /* NEW ${info.name} HERE */`],
                    decorators: [
                        {
                            name: 'Reduces',
                            arguments: [info.events[1].eventName],
                        },
                    ],
                    name: `reduce${info.events[1].eventName}`,
                    parameters: [
                        {
                            name: 'event',
                            type: info.events[1].eventName,
                        },
                        {
                            name: `current${info.name}`,
                            type: info.name,
                            hasQuestionToken: true,
                        },
                    ],
                },
            ]);
        });
    });
    describe('getResourceSourceFile', () => {
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('should return source file', () => {
            const resourceName = 'Post';
            (0, sinon_1.replace)(Filenames, 'fileNameWithExtension', sinon_1.fake.returns('post.ts'));
            const project = new ts_morph_1.Project();
            const fileText = `
      import { Entity } from '@boostercloud/framework-core'
      import { UUID } from '@boostercloud/framework-types'
      
      @Entity
      export class Post {
        public constructor(public id: UUID, readonly title: string, readonly content: string, readonly author: string) {}
      }`;
            const fakeSourceFile = project.createSourceFile('src/entities/post.ts', fileText);
            (0, sinon_1.stub)(ts_morph_1.Project.prototype, 'getSourceFileOrThrow').returns(fakeSourceFile);
            const sourceFile = (0, method_generator_1.getResourceSourceFile)(resourceName);
            const sourceFileClasses = sourceFile.getClasses().map((className) => className.getName());
            (0, expect_1.expect)(ts_morph_1.Project.prototype.getSourceFileOrThrow).to.have.been.called;
            (0, expect_1.expect)(ts_morph_1.Project.prototype.getSourceFileOrThrow).to.have.been.calledOnce;
            (0, expect_1.expect)(ts_morph_1.Project.prototype.getSourceFileOrThrow).to.not.have.been.calledTwice;
            (0, expect_1.expect)(ts_morph_1.Project.prototype.getSourceFileOrThrow).to.have.been.calledWith((0, filenames_1.fileNameWithExtension)(resourceName));
            (0, expect_1.expect)(sourceFile).to.be.equal(fakeSourceFile);
            (0, expect_1.expect)(filenames_1.fileNameWithExtension).to.have.been.calledWith(resourceName);
            (0, expect_1.expect)(sourceFileClasses).to.contain(resourceName);
            (0, expect_1.expect)(sourceFile.getFilePath()).to.match(/src\/entities\/post.ts/);
        });
        it("should throw error if source file doesn't exist", () => {
            (0, sinon_1.replace)(Filenames, 'fileNameWithExtension', sinon_1.fake.returns('fake-post.ts'));
            (0, sinon_1.stub)(ts_morph_1.Project.prototype, 'getSourceFileOrThrow').throws(new Error('Could not find source file in project with the provided file name: fake-post.ts'));
            const resourceName = 'FakePost';
            let exceptionThrown = false;
            let exceptionMessage = '';
            try {
                (0, method_generator_1.getResourceSourceFile)(resourceName);
            }
            catch (e) {
                exceptionThrown = true;
                exceptionMessage = e.message;
            }
            (0, expect_1.expect)(filenames_1.fileNameWithExtension).to.have.been.calledWith(resourceName);
            (0, expect_1.expect)(method_generator_1.getResourceSourceFile).to.throw();
            (0, expect_1.expect)(exceptionThrown).to.be.true;
            (0, expect_1.expect)(exceptionMessage).to.be.equal('Could not find source file in project with the provided file name: fake-post.ts');
        });
    });
});
