"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../../helper/expect");
const framework_common_helpers_1 = require("@boostercloud/framework-common-helpers");
const setup_1 = require("./setup");
const mocha_1 = require("mocha");
describe('Health end-to-end tests', () => {
    if (process.env.TESTED_PROVIDER === 'AWS') {
        console.log('****************** Warning **********************');
        console.log('AWS provider does not support sensor health so these tests are skipped for AWS');
        console.log('*************************************************');
        return;
    }
    let url = '';
    (0, mocha_1.before)(async () => {
        url = setup_1.applicationUnderTest.http.getHealthUrl();
    });
    it('root health returns all indicators', async () => {
        const jsonResult = await getHealth(url);
        const boosterResult = jsonResult.find((element) => element.id === 'booster');
        expectBooster(boosterResult);
        const boosterFunction = boosterResult.components.find((element) => element.id === 'booster/function');
        expectBoosterFunction(boosterFunction);
        const boosterDatabase = boosterResult.components.find((element) => element.id === 'booster/database');
        expectBoosterDatabase(boosterDatabase);
        const databaseEvents = boosterDatabase.components.find((element) => element.id === 'booster/database/events');
        expectDatabaseEvents(databaseEvents);
        const databaseReadModels = boosterDatabase.components.find((element) => element.id === 'booster/database/readmodels');
        expectDatabaseReadModels(databaseReadModels);
        const myApplicationDatabase = boosterDatabase.components.find((element) => element.id === 'booster/database/myApplication');
        expectApplicationAddDatabase(myApplicationDatabase);
        const myApplication2Database = boosterDatabase.components.find((element) => element.id === 'booster/database/myApplication2');
        expectApplication2AddDatabase(myApplication2Database);
        const appResult = jsonResult.find((element) => element.id === 'myApplication');
        expectApplication(appResult);
        const appChildResult = appResult.components.find((element) => element.id === 'myApplication/child');
        expectApplicationChild(appChildResult);
    });
    it('function health returns the indicator', async () => {
        const boosterFunction = (await getHealth(url, 'function'))[0];
        expectBoosterFunction(boosterFunction);
    });
    it('database health returns the indicator', async () => {
        const boosterDatabase = (await getHealth(url, 'database'))[0];
        expectBoosterDatabase(boosterDatabase);
        const databaseEvents = boosterDatabase.components.find((element) => element.id === 'booster/database/events');
        expectDatabaseEvents(databaseEvents);
        const databaseReadModels = boosterDatabase.components.find((element) => element.id === 'booster/database/readmodels');
        expectDatabaseReadModels(databaseReadModels);
        const myApplicationDatabase = boosterDatabase.components.find((element) => element.id === 'booster/database/myApplication');
        expectApplicationAddDatabase(myApplicationDatabase);
        const myApplication2Database = boosterDatabase.components.find((element) => element.id === 'booster/database/myApplication2');
        expectApplication2AddDatabase(myApplication2Database);
    });
    it('events database health returns the indicator', async () => {
        const databaseEvents = (await getHealth(url, 'database/events'))[0];
        expectDatabaseEvents(databaseEvents);
    });
    it('readmodels database health returns the indicator', async () => {
        const databaseReadModels = (await getHealth(url, 'database/readmodels'))[0];
        expectDatabaseReadModels(databaseReadModels);
    });
});
function expectBooster(boosterResult) {
    (0, expect_1.expect)(boosterResult.id).to.be.eq('booster');
    (0, expect_1.expect)(boosterResult.status).to.be.eq('UP');
    (0, expect_1.expect)(boosterResult.name).to.be.eq('Booster');
    (0, expect_1.expect)(boosterResult.details.boosterVersion.length).to.be.gt(0);
    (0, expect_1.expect)(boosterResult.components.length).to.be.eq(2);
}
function expectBoosterFunction(boosterFunction) {
    (0, expect_1.expect)(boosterFunction.id).to.be.eq('booster/function');
    (0, expect_1.expect)(boosterFunction.status).to.be.eq('UP');
    (0, expect_1.expect)(boosterFunction.name).to.be.eq('Booster Function');
    (0, expect_1.expect)(boosterFunction.details.cpus.length).to.be.gt(0);
    (0, expect_1.expect)(boosterFunction.details.cpus[0].timesPercentages.length).to.be.gt(0);
    (0, expect_1.expect)(boosterFunction.details.memory.totalBytes).to.be.gt(0);
    (0, expect_1.expect)(boosterFunction.details.memory.freeBytes).to.be.gt(0);
    (0, expect_1.expect)(boosterFunction.details.graphQL_url.endsWith('/graphql')).to.be.true;
    (0, expect_1.expect)(boosterFunction.components).to.be.undefined;
}
function expectBoosterDatabase(boosterDatabase) {
    (0, expect_1.expect)(boosterDatabase.id).to.be.eq('booster/database');
    (0, expect_1.expect)(boosterDatabase.status).to.be.eq('UP');
    (0, expect_1.expect)(boosterDatabase.name).to.be.eq('Booster Database');
    (0, expect_1.expect)(boosterDatabase.details).to.not.be.undefined;
    (0, expect_1.expect)(boosterDatabase.components.length).to.be.eq(4);
}
function expectDatabaseEvents(databaseEvent) {
    (0, expect_1.expect)(databaseEvent.id).to.be.eq('booster/database/events');
    (0, expect_1.expect)(databaseEvent.status).to.be.eq('UP');
    (0, expect_1.expect)(databaseEvent.name).to.be.eq('Booster Database Events');
    (0, expect_1.expect)(databaseEvent.details).to.not.be.undefined;
    (0, expect_1.expect)(databaseEvent.components).to.be.undefined;
}
function expectDatabaseReadModels(databaseReadModels) {
    (0, expect_1.expect)(databaseReadModels.id).to.be.eq('booster/database/readmodels');
    (0, expect_1.expect)(databaseReadModels.status).to.be.eq('UP');
    (0, expect_1.expect)(databaseReadModels.name).to.be.eq('Booster Database ReadModels');
    (0, expect_1.expect)(databaseReadModels.details).to.not.be.undefined;
    (0, expect_1.expect)(databaseReadModels.components).to.be.undefined;
}
function expectApplicationAddDatabase(applicationDatabase) {
    (0, expect_1.expect)(applicationDatabase.id).to.be.eq('booster/database/myApplication');
    (0, expect_1.expect)(applicationDatabase.status).to.be.eq('UNKNOWN');
    (0, expect_1.expect)(applicationDatabase.name).to.be.eq('Indicator added to the Booster Database indicator through My Application');
    (0, expect_1.expect)(applicationDatabase.details).to.be.undefined;
    (0, expect_1.expect)(applicationDatabase.components).to.be.undefined;
}
function expectApplication2AddDatabase(databaseApplication2) {
    (0, expect_1.expect)(databaseApplication2.id).to.be.eq('booster/database/myApplication2');
    (0, expect_1.expect)(databaseApplication2.status).to.be.eq('UNKNOWN');
    (0, expect_1.expect)(databaseApplication2.name).to.be.eq('A second indicator added to the Booster Database indicator through My Application');
    (0, expect_1.expect)(databaseApplication2.details).to.be.undefined;
    (0, expect_1.expect)(databaseApplication2.components).to.be.undefined;
}
function expectApplication(boosterResult) {
    (0, expect_1.expect)(boosterResult.id).to.be.eq('myApplication');
    (0, expect_1.expect)(boosterResult.status).to.be.eq('UP');
    (0, expect_1.expect)(boosterResult.name).to.be.eq('my-application');
    (0, expect_1.expect)(boosterResult.details).to.be.undefined;
    (0, expect_1.expect)(boosterResult.components.length).to.be.eq(1);
}
function expectApplicationChild(boosterResult) {
    (0, expect_1.expect)(boosterResult.id).to.be.eq('myApplication/child');
    (0, expect_1.expect)(boosterResult.status).to.be.eq('OUT_OF_SERVICE');
    (0, expect_1.expect)(boosterResult.name).to.be.eq('My Application child');
    (0, expect_1.expect)(boosterResult.details).to.be.undefined;
    (0, expect_1.expect)(boosterResult.components).to.be.undefined;
}
async function getHealth(url, componentUrl) {
    const path = componentUrl ? `${url}booster/${componentUrl}` : url;
    console.log(path);
    const result = await (0, framework_common_helpers_1.request)(path);
    (0, expect_1.expect)(result).to.not.be.undefined;
    (0, expect_1.expect)(result.status).to.be.eq(200);
    return JSON.parse(result.body);
}
