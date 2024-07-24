"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const expect_1 = require("../../expect");
const framework_types_1 = require("@boostercloud/framework-types");
const core_1 = require("@aws-cdk/core");
const aws_lambda_1 = require("@aws-cdk/aws-lambda");
const sinon_1 = require("sinon");
const rewire = require('rewire');
const applicationStack = rewire('../../../src/infrastructure/stacks/application-stack');
describe('the application stack builder', () => {
    class TestReadModel1 {
        constructor() {
            this.id = '';
        }
    }
    class TestReadModel2 {
        constructor() {
            this.id = '';
        }
    }
    const readModels = [TestReadModel1, TestReadModel2];
    const config = new framework_types_1.BoosterConfig('test');
    config.appName = 'testing-app';
    config.userProjectRootPath = '.';
    // eslint-disable-next-line prettier/prettier
    readModels.forEach((readModel) => {
        config.readModels[readModel.name] = {
            class: readModel,
            authorizer: () => Promise.resolve(),
            properties: [],
            before: [],
        };
    });
    config.env['A_CUSTOM_ENV_VARIABLE'] = 'important-value';
    it('builds the application stack of a simple app correctly', () => {
        const boosterApp = new core_1.App();
        new applicationStack.ApplicationStackBuilder(config).buildOn(boosterApp);
        const appStackName = config.resourceNames.applicationStack;
        const appStack = boosterApp.node.findChild(appStackName).node;
        const restAPIName = appStackName + '-rest-api';
        const websocketAPIName = appStackName + '-websocket-api';
        const eventsStore = 'events-store';
        const eventsLambda = 'events-main';
        const graphQLLambda = 'graphql-handler';
        const subscriptionsNotifierLambda = 'subscriptions-notifier';
        const subscriptionsStore = appStackName + '-subscriptions-store';
        const connectionsStore = appStackName + '-connections-store';
        const websocketRoutes = ['route-$connect', 'route-$disconnect', 'route-$default'];
        const restAPI = appStack.tryFindChild(restAPIName);
        const websocketAPI = appStack.tryFindChild(websocketAPIName);
        const numberOfLambdas = appStack.children.filter((child) => child instanceof aws_lambda_1.Function).length;
        // First check for all the constructs that must be created
        // REST API-related
        (0, expect_1.expect)(restAPI).not.to.be.undefined;
        (0, expect_1.expect)(restAPI.root.getResource('graphql')).not.to.be.undefined;
        // Websocket API-related
        (0, expect_1.expect)(websocketAPI).not.to.be.undefined;
        (0, expect_1.expect)(websocketAPI.protocolType).to.be.eq('WEBSOCKET');
        websocketRoutes.forEach((route) => (0, expect_1.expect)(appStack.tryFindChild(route)).not.to.be.undefined);
        (0, expect_1.expect)(numberOfLambdas).to.equal(3);
        // GraphQL related
        (0, expect_1.expect)(appStack.tryFindChild(graphQLLambda)).not.to.be.undefined;
        (0, expect_1.expect)(appStack.tryFindChild(subscriptionsNotifierLambda)).not.to.be.undefined;
        (0, expect_1.expect)(appStack.tryFindChild(subscriptionsStore)).not.to.be.undefined;
        (0, expect_1.expect)(appStack.tryFindChild(connectionsStore)).not.to.be.undefined;
        // Events-related
        (0, expect_1.expect)(appStack.tryFindChild(eventsLambda)).not.to.be.undefined;
        (0, expect_1.expect)(appStack.tryFindChild(eventsStore)).not.to.be.undefined;
        // ReadModels
        readModels.forEach(({ name }) => (0, expect_1.expect)(appStack.tryFindChild(name)).not.to.be.undefined);
    });
    it('builds the application stack of an app with roles correctly', () => {
        config.roles['Admin'] = {
            auth: {
                signUpMethods: [],
            },
        };
        const boosterApp = new core_1.App();
        new applicationStack.ApplicationStackBuilder(config).buildOn(boosterApp);
        const appStackName = config.resourceNames.applicationStack;
        const appStack = boosterApp.node.findChild(appStackName).node;
        const apiName = appStackName + '-rest-api';
        const api = appStack.tryFindChild(apiName);
        const lambdas = appStack.children.filter((child) => child instanceof aws_lambda_1.Function);
        const numberOfLambdas = lambdas.length;
        // Just check for all the EXTRA constructs that must be created to support roles
        // API-related
        (0, expect_1.expect)(api).not.to.be.undefined;
        // Lambdas
        (0, expect_1.expect)(numberOfLambdas).to.equal(3);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        lambdas.forEach((lambda) => {
            (0, expect_1.expect)(lambda.environment.BOOSTER_ENV.value).to.equal('test');
            (0, expect_1.expect)(lambda.environment.A_CUSTOM_ENV_VARIABLE.value).to.equal('important-value');
        });
        // Check all read models
        readModels.forEach(({ name }) => (0, expect_1.expect)(appStack.tryFindChild(name)).not.to.be.undefined);
    });
    it('allows rockets to extend the stack', () => {
        const boosterApp = new core_1.App();
        const fakeBuildStack = (0, sinon_1.fake)((app, applicationStack, props) => new core_1.Stack(app, applicationStack, props));
        const restoreBuildStack = applicationStack.__set__('buildStack', fakeBuildStack);
        const fakeRocket = {
            mountStack: (0, sinon_1.fake)(),
            unmountStack: (0, sinon_1.fake)(),
        };
        new applicationStack.ApplicationStackBuilder(config).buildOn(boosterApp, [fakeRocket]);
        const stack = fakeBuildStack.returnValues[0];
        console.log(stack);
        (0, expect_1.expect)(fakeRocket.mountStack).to.have.been.calledWith(stack);
        restoreBuildStack();
    });
});
