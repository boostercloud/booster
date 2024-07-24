"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const aws_dynamodb_1 = require("@aws-cdk/aws-dynamodb");
const framework_types_1 = require("@boostercloud/framework-types");
const sinon_1 = require("sinon");
const expect_1 = require("../expect");
const application_stack_1 = require("../../src/infrastructure/stacks/application-stack");
const aws_cdk_1 = require("aws-cdk");
const rewire = require('rewire');
const StackTools = rewire('../../src/infrastructure/stack-tools');
const testEnvironment = {
    account: 'testAccount',
    region: 'testRegion',
};
describe('the `stack-tools` module', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('the `getStackServiceConfiguration` method', () => {
        it('builds the configuration using the `assemble` method', async () => {
            (0, sinon_1.replace)(aws_cdk_1.SdkProvider.prototype, 'forEnvironment', sinon_1.fake.returns((0, sinon_1.mock)()));
            const fakeAssemble = (0, sinon_1.fake)();
            const revertAssemble = StackTools.__set__('assemble', fakeAssemble);
            const revertGetEnvironment = StackTools.__set__('getEnvironment', sinon_1.fake.returns(Promise.resolve(testEnvironment)));
            const config = {};
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const { cdkToolkit } = (await StackTools.getStackServiceConfiguration(config));
            // We're hacking the CDK to make it believe it's deploying the app
            cdkToolkit.props.cloudExecutable.props.synthesizer();
            // Even with no parameters, `assemble` should receive the config via closure
            (0, expect_1.expect)(fakeAssemble).to.have.been.calledWithMatch(config);
            revertAssemble();
            revertGetEnvironment();
        });
        context('with rockets', () => {
            it('forwards the rocket list to the `assemble` method for initialization', async () => {
                (0, sinon_1.replace)(aws_cdk_1.SdkProvider.prototype, 'forEnvironment', sinon_1.fake.returns((0, sinon_1.mock)()));
                const fakeAssemble = (0, sinon_1.fake)();
                const revertRewire = StackTools.__set__('assemble', fakeAssemble);
                const revertGetEnvironment = StackTools.__set__('getEnvironment', sinon_1.fake.returns(Promise.resolve(testEnvironment)));
                const config = {};
                const fakeRocket = {
                    mountStack: (0, sinon_1.fake)(),
                    unmountStack: (0, sinon_1.fake)(),
                };
                const { cdkToolkit } = (await StackTools.getStackServiceConfiguration(config, [
                    fakeRocket,
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                ]));
                // We're hacking the CDK to make it believe it's deploying the app
                cdkToolkit.props.cloudExecutable.props.synthesizer();
                (0, expect_1.expect)(fakeAssemble).to.have.been.calledWithMatch(config, [fakeRocket]);
                revertRewire();
                revertGetEnvironment();
            });
        });
    });
    describe('the `assemble` method', () => {
        const assemble = StackTools.__get__('assemble');
        it('generates the CloudAssembly correctly for a simple configuration', () => {
            class EmptyEntity {
                constructor() {
                    this.id = '';
                }
            }
            const config = new framework_types_1.BoosterConfig('test');
            config.userProjectRootPath = '.';
            config.appName = 'testing-app';
            config.entities[EmptyEntity.name] = {
                class: EmptyEntity,
                eventStreamAuthorizer: () => Promise.resolve(),
            };
            // Just checks that the assemble method does not fail,
            // meaning that the stack is built correctly according to the
            // AWS validations
            (0, expect_1.expect)(() => assemble(config)).not.to.throw();
        });
        context('when roles and permissions have been defined', () => {
            it('generates the auth endpoints'); // TODO
            it('generates a lambda to check authorization'); // TODO
        });
        context('when there is a configured command', () => {
            it('generates an API endpoint to submit it'); // TODO
            it('generates a lambda to dispatch the commands'); // TODO
        });
        context('when there is a configured event', () => {
            it('generates a DynamoDB table to store the events'); // TODO
            it('generates a lambda to dispatch the events'); // TODO
        });
        context('for a configured read model', () => {
            class SomeReadModel {
                constructor() {
                    this.id = '';
                }
            }
            const config = new framework_types_1.BoosterConfig('test');
            config.userProjectRootPath = '.';
            config.appName = 'testing-app';
            config.readModels[SomeReadModel.name] = {
                class: SomeReadModel,
                authorizer: () => Promise.resolve(),
                properties: [],
                before: [],
            };
            const cloudAssembly = assemble(config);
            it('generates cloudformation for a DynamoDB table to store its state', () => {
                const stackResources = cloudAssembly.getStackByName('testing-app-app').template['Resources'];
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const table = Object.values(stackResources).find((obj) => {
                    return obj.Properties.TableName == 'testing-app-app-SomeReadModel';
                });
                (0, expect_1.expect)(table).to.deep.equal({
                    DeletionPolicy: 'Delete',
                    Properties: {
                        AttributeDefinitions: [
                            {
                                AttributeName: 'id',
                                AttributeType: 'S',
                            },
                        ],
                        BillingMode: 'PAY_PER_REQUEST',
                        KeySchema: [
                            {
                                AttributeName: 'id',
                                KeyType: 'HASH',
                            },
                        ],
                        StreamSpecification: {
                            StreamViewType: aws_dynamodb_1.StreamViewType.NEW_IMAGE,
                        },
                        TableName: 'testing-app-app-SomeReadModel',
                    },
                    Type: 'AWS::DynamoDB::Table',
                    UpdateReplacePolicy: 'Delete',
                });
            });
            it('generates cloudformation for an API endpoint for graphQL', () => {
                const stackResources = cloudAssembly.getStackByName('testing-app-app').template['Resources'];
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const fun = Object.values(stackResources).find((obj) => {
                    return obj.Properties.FunctionName == 'testing-app-app-graphql-handler';
                });
                (0, expect_1.expect)(fun.Properties.Handler).to.equal('dist/index.boosterServeGraphQL');
            });
        });
        context('with rockets', () => {
            it('initializes the `ApplicationStackBuilder` with the list of rockets', async () => {
                (0, sinon_1.spy)(application_stack_1.ApplicationStackBuilder.prototype, 'buildOn');
                class EmptyEntity {
                    constructor() {
                        this.id = '';
                    }
                }
                const config = new framework_types_1.BoosterConfig('test');
                config.userProjectRootPath = '.';
                config.appName = 'testing-app';
                config.entities[EmptyEntity.name] = {
                    class: EmptyEntity,
                    eventStreamAuthorizer: () => Promise.reject('unauthorized'),
                };
                const fakeRocket = {
                    mountStack: (0, sinon_1.fake)(),
                    unmountStack: (0, sinon_1.fake)(),
                };
                assemble(config, [fakeRocket]);
                (0, expect_1.expect)(application_stack_1.ApplicationStackBuilder.prototype.buildOn).to.have.been.calledWithMatch({}, [fakeRocket]);
            });
        });
    });
    describe('the `getStackNames` method', () => {
        const fakeGetStackNames = (0, sinon_1.fake)(StackTools.__get__('getStackNames'));
        const config = {
            resourceNames: {
                applicationStack: 'fake-stack',
            },
        };
        fakeGetStackNames(config);
        (0, expect_1.expect)(fakeGetStackNames).to.have.returned([config.resourceNames.applicationStack]);
    });
    describe('the `getStackToolkitName` method', () => {
        const fakeGetStackToolkitName = (0, sinon_1.fake)(StackTools.__get__('getStackToolkitName'));
        const config = {
            appName: 'fake-app-name',
        };
        fakeGetStackToolkitName(config);
        (0, expect_1.expect)(fakeGetStackToolkitName).to.have.returned(config.appName + '-toolkit');
    });
    describe('the `getStackToolkitBucketName` method', () => {
        const fakeGetStackToolkitBucketName = (0, sinon_1.fake)(StackTools.__get__('getStackToolkitBucketName'));
        const config = {
            appName: 'fake-app-name',
        };
        fakeGetStackToolkitBucketName(config);
        (0, expect_1.expect)(fakeGetStackToolkitBucketName).to.have.returned(config.appName + '-toolkit-bucket');
    });
    describe('the `getEnvironments` method', () => {
        it('returns default account and region', async () => {
            const fakeGetEnvironment = (0, sinon_1.fake)(StackTools.__get__('getEnvironment'));
            const fakeSDKProvider = {
                defaultAccount: sinon_1.fake.resolves({ accountId: 'default-account' }),
                defaultRegion: 'default-region',
            };
            await (0, expect_1.expect)(fakeGetEnvironment(fakeSDKProvider)).to.eventually.become({
                name: 'Default environment',
                account: 'default-account',
                region: 'default-region',
            });
        });
        it("throws an error if it can't load the default account", async () => {
            const fakeGetEnvironment = (0, sinon_1.fake)(StackTools.__get__('getEnvironment'));
            const fakeSDKProvider = {
                defaultAccount: sinon_1.fake.resolves(undefined),
                defaultRegion: 'default-region',
            };
            await (0, expect_1.expect)(fakeGetEnvironment(fakeSDKProvider)).to.eventually.be.rejectedWith(/Unable to load default AWS account/);
        });
        it("throws an error if it can't load the default region", async () => {
            const fakeGetEnvironment = (0, sinon_1.fake)(StackTools.__get__('getEnvironment'));
            const fakeSDKProvider = {
                defaultAccount: sinon_1.fake.resolves({ accountId: 'default-accoung' }),
            };
            await (0, expect_1.expect)(fakeGetEnvironment(fakeSDKProvider)).to.eventually.be.rejectedWith(/Unable to determine default region/);
        });
    });
});
