"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const permissions_1 = require("../../../src/infrastructure/stacks/permissions");
const sinon_1 = require("sinon");
const expect_1 = require("../../expect");
const faker_1 = require("faker");
const policies = require("../../../src/infrastructure/stacks/policies");
const aws_iam_1 = require("@aws-cdk/aws-iam");
const core_1 = require("@aws-cdk/core");
const framework_types_1 = require("@boostercloud/framework-types");
describe('permissions', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('setupPermissions', () => {
        let mockSubscriptionsStoreArn;
        let mockConnectionsStoreArn;
        let mockReadModelTableArn;
        let mockEventsStoreTableArn;
        let mockWebSocketAPIRef;
        let mockPolicyStatement;
        let mockFnJoin;
        let mockFnRef;
        let mockGraphQLLambda;
        let mockSubscriptionDispatcherLambda;
        let mockEventsLambda;
        let mockSubscriptionsStore;
        let mockConnectionsStore;
        let mockEventsStoreTable;
        let mockReadModelTable;
        let mockReadModelTables;
        let mockWebsocketAPI;
        let createPolicyStatementStub;
        let graphQLAddToRolePolicyStub;
        let subscriptionAddToRolePolicyStub;
        let eventsAddToRolePolicyStub;
        let fnJoinStub;
        let fnRefStub;
        const config = new framework_types_1.BoosterConfig('test');
        config.appName = 'testing-app';
        config.userProjectRootPath = '.';
        beforeEach(() => {
            mockSubscriptionsStoreArn = faker_1.random.alphaNumeric(10);
            mockConnectionsStoreArn = faker_1.random.alphaNumeric(10);
            mockReadModelTableArn = faker_1.random.alphaNumeric(10);
            mockEventsStoreTableArn = faker_1.random.alphaNumeric(10);
            mockWebSocketAPIRef = faker_1.random.alphaNumeric(10);
            mockPolicyStatement = new aws_iam_1.PolicyStatement();
            mockFnJoin = faker_1.random.alphaNumeric(10);
            mockFnRef = faker_1.random.alphaNumeric(10);
            createPolicyStatementStub = (0, sinon_1.stub)(policies, 'createPolicyStatement').returns(mockPolicyStatement);
            graphQLAddToRolePolicyStub = (0, sinon_1.stub)();
            subscriptionAddToRolePolicyStub = (0, sinon_1.stub)();
            eventsAddToRolePolicyStub = (0, sinon_1.stub)();
            mockGraphQLLambda = {};
            mockSubscriptionDispatcherLambda = {};
            mockEventsLambda = {};
            mockSubscriptionsStore = {
                tableArn: mockSubscriptionsStoreArn,
            };
            mockConnectionsStore = {
                tableArn: mockConnectionsStoreArn,
            };
            mockEventsStoreTable = {
                tableArn: mockEventsStoreTableArn,
            };
            mockReadModelTable = {
                tableArn: mockReadModelTableArn,
            };
            mockReadModelTables = [mockReadModelTable];
            mockWebsocketAPI = {
                ref: mockWebSocketAPIRef,
            };
            mockGraphQLLambda.addToRolePolicy = graphQLAddToRolePolicyStub;
            mockSubscriptionDispatcherLambda.addToRolePolicy = subscriptionAddToRolePolicyStub;
            mockEventsLambda.addToRolePolicy = eventsAddToRolePolicyStub;
            fnJoinStub = (0, sinon_1.stub)(core_1.Fn, 'join').returns(mockFnJoin);
            fnRefStub = (0, sinon_1.stub)(core_1.Fn, 'ref').returns(mockFnRef);
            const graphQLStackMembers = {
                subscriptionNotifier: mockSubscriptionDispatcherLambda,
                subscriptionsStore: mockSubscriptionsStore,
                graphQLLambda: mockGraphQLLambda,
                connectionsStore: mockConnectionsStore,
            };
            const eventsStackMembers = {
                eventsStore: mockEventsStoreTable,
                eventsLambda: mockEventsLambda,
            };
            (0, permissions_1.setupPermissions)(config, graphQLStackMembers, eventsStackMembers, mockReadModelTables, mockWebsocketAPI);
        });
        describe('GraphQL Lambda', () => {
            it('should call addToRolePolicy', () => {
                (0, expect_1.expect)(graphQLAddToRolePolicyStub).to.has.callCount(7);
                (0, expect_1.expect)(graphQLAddToRolePolicyStub).calledWithExactly(mockPolicyStatement);
            });
            describe('policy statements', () => {
                it('should add events store permissions', () => {
                    (0, expect_1.expect)(createPolicyStatementStub).calledWithExactly([mockEventsStoreTableArn], ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:BatchGetItem', 'dynamodb:BatchWriteItem']);
                    (0, expect_1.expect)(createPolicyStatementStub).calledWithExactly([mockEventsStoreTableArn + '*'], ['dynamodb:Query*']);
                });
                it('should create subscriptions table permissions', () => {
                    (0, expect_1.expect)(createPolicyStatementStub).calledWithExactly([mockSubscriptionsStoreArn], ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:DeleteItem', 'dynamodb:BatchWriteItem']);
                    (0, expect_1.expect)(createPolicyStatementStub).calledWithExactly([mockSubscriptionsStoreArn + '*'], ['dynamodb:Query*']);
                });
                it('should create connections table permissions', () => {
                    (0, expect_1.expect)(createPolicyStatementStub).calledWithExactly([mockConnectionsStoreArn], ['dynamodb:GetItem', 'dynamodb:PutItem', 'dynamodb:DeleteItem']);
                });
                it('should create read model permissions', () => {
                    (0, expect_1.expect)(createPolicyStatementStub).calledWithExactly([mockReadModelTableArn], ['dynamodb:Query*', 'dynamodb:Scan*']);
                });
            });
        });
        describe('Subscriptions Lambda', () => {
            it('should call addToRolePolicy', () => {
                (0, expect_1.expect)(subscriptionAddToRolePolicyStub).to.be.calledTwice;
                (0, expect_1.expect)(subscriptionAddToRolePolicyStub).to.be.calledWithExactly(mockPolicyStatement);
            });
            describe('policy statements', () => {
                it('should create subscriptions table permissions', () => {
                    (0, expect_1.expect)(createPolicyStatementStub).to.be.calledWithExactly([mockSubscriptionsStoreArn], ['dynamodb:Query*']);
                });
                describe('web socket API', () => {
                    it('should call Fn Join', () => {
                        (0, expect_1.expect)(fnJoinStub).to.be.calledOnce;
                        (0, expect_1.expect)(fnJoinStub).to.be.calledWithExactly(':', [
                            'arn',
                            mockFnRef,
                            'execute-api',
                            mockFnRef,
                            mockFnRef,
                            `${mockWebSocketAPIRef}/*`,
                        ]);
                    });
                    it('should call Fn Ref', () => {
                        (0, expect_1.expect)(fnRefStub).to.be.calledThrice;
                    });
                    it('should create web socket API permissions', () => {
                        (0, expect_1.expect)(createPolicyStatementStub).calledWithExactly([mockFnJoin], ['execute-api:ManageConnections']);
                    });
                });
            });
        });
        describe('Events Lambda', () => {
            it('should call addToRolePolicy', () => {
                (0, expect_1.expect)(eventsAddToRolePolicyStub).calledThrice;
                (0, expect_1.expect)(eventsAddToRolePolicyStub).calledWithExactly(mockPolicyStatement);
            });
            describe('policy statements', () => {
                it('should add events store permissions', () => {
                    (0, expect_1.expect)(createPolicyStatementStub).calledWithExactly([mockEventsStoreTableArn], ['dynamodb:Query*', 'dynamodb:Put*', 'dynamodb:BatchGetItem', 'dynamodb:BatchWriteItem']);
                    (0, expect_1.expect)(createPolicyStatementStub).calledWithExactly([mockEventsStoreTableArn + '*'], ['dynamodb:Query*']);
                });
                it('should create read model permissions', () => {
                    (0, expect_1.expect)(createPolicyStatementStub).calledWithExactly([mockReadModelTableArn], ['dynamodb:Get*', 'dynamodb:Query*', 'dynamodb:Scan*', 'dynamodb:Put*', 'dynamodb:DeleteItem*']);
                });
            });
        });
    });
});
