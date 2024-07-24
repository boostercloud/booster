"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../../expect");
const sensor_1 = require("../../../src/sensor");
const framework_types_1 = require("@boostercloud/framework-types");
const sinon_1 = require("sinon");
const mock_jwks_1 = require("mock-jwks");
const faker_1 = require("faker");
const src_1 = require("../../../src");
const jwksUri = 'https://myauth0app.auth0.com/' + '.well-known/jwks.json';
const issuer = 'auth0';
describe('BoosterHealthService', () => {
    const config = new framework_types_1.BoosterConfig('test');
    before(() => {
        config.provider = {
            api: {
                requestSucceeded: (0, sinon_1.fake)((request) => request),
                requestFailed: (0, sinon_1.fake)((error) => error),
            },
        };
    });
    beforeEach(() => {
        Object.values(config.sensorConfiguration.health.booster).forEach((indicator) => {
            indicator.enabled = true;
        });
        config.sensorConfiguration.health.globalAuthorizer = {
            authorize: 'all',
        };
    });
    it('All indicators are UP', async () => {
        config.provider.sensor = defaultSensor();
        const boosterResult = await boosterHealth(config);
        const boosterFunction = getBoosterFunction(boosterResult);
        const boosterDatabase = getBoosterDatabase(boosterResult);
        const databaseEvents = getEventDatabase(boosterDatabase);
        const databaseReadModels = getReadModelsDatabase(boosterDatabase);
        const expectedStatus = 'UP';
        expectBooster(boosterResult, '', expectedStatus);
        expectBoosterFunction(boosterFunction, '', expectedStatus);
        expectBoosterDatabase(boosterDatabase, expectedStatus);
        expectDatabaseEvents(databaseEvents, expectedStatus);
        expectDatabaseReadModels(databaseReadModels, expectedStatus);
    });
    it('All indicators are DOWN', async () => {
        config.provider.sensor = defaultSensor();
        config.provider.sensor.isGraphQLFunctionUp = (0, sinon_1.fake)(() => false);
        config.provider.sensor.isDatabaseEventUp = (0, sinon_1.fake)(() => false);
        config.provider.sensor.areDatabaseReadModelsUp = (0, sinon_1.fake)(() => false);
        const expectedStatus = 'DOWN';
        const boosterResult = await boosterHealth(config);
        const boosterFunction = getBoosterFunction(boosterResult);
        const boosterDatabase = getBoosterDatabase(boosterResult);
        const databaseEvents = getEventDatabase(boosterDatabase);
        const databaseReadModels = getReadModelsDatabase(boosterDatabase);
        expectBooster(boosterResult, '', expectedStatus);
        expectBoosterFunction(boosterFunction, '', expectedStatus);
        expectBoosterDatabase(boosterDatabase, expectedStatus);
        expectDatabaseEvents(databaseEvents, expectedStatus);
        expectDatabaseReadModels(databaseReadModels, expectedStatus);
    });
    it('Details are processed', async () => {
        config.provider.sensor = defaultSensor();
        config.provider.sensor.databaseEventsHealthDetails = (0, sinon_1.fake)(() => ({
            test: true,
        }));
        config.provider.sensor.databaseReadModelsHealthDetails = (0, sinon_1.fake)(() => ({
            test: true,
        }));
        const boosterResult = await boosterHealth(config);
        const boosterFunction = getBoosterFunction(boosterResult);
        const boosterDatabase = getBoosterDatabase(boosterResult);
        const databaseEvents = getEventDatabase(boosterDatabase);
        const databaseReadModels = getReadModelsDatabase(boosterDatabase);
        const expectedStatus = 'UP';
        expectBooster(boosterResult, '', expectedStatus);
        expectBoosterFunction(boosterFunction, '', expectedStatus);
        expectBoosterDatabase(boosterDatabase, expectedStatus);
        expectDatabaseEventsWithDetails(databaseEvents, expectedStatus, {
            test: true,
        });
        expectDatabaseReadModelsWithDetails(databaseReadModels, expectedStatus, {
            test: true,
        });
    });
    it('Validates with the expected Role', async () => {
        const jwks = (0, mock_jwks_1.default)('https://myauth0app.auth0.com/');
        jwks.start();
        const token = jwks.token({
            sub: faker_1.random.uuid(),
            iss: issuer,
            'custom:role': 'UserRole',
            extraParam: 'claims',
            anotherParam: 111,
            email: faker_1.internet.email(),
            phoneNumber: faker_1.phone.phoneNumber(),
        });
        config.provider.sensor = defaultSensor(token);
        config.sensorConfiguration.health.globalAuthorizer = {
            authorize: [UserRole],
        };
        config.tokenVerifiers = [
            new src_1.JwksUriTokenVerifier(issuer, 'https://myauth0app.auth0.com/' + '.well-known/jwks.json'),
        ];
        const boosterResult = await boosterHealth(config);
        expectBooster(boosterResult, '', 'UP');
    });
    it('Validates fails with wrong role', async () => {
        const jwks = (0, mock_jwks_1.default)('https://myauth0app.auth0.com/');
        jwks.start();
        const token = jwks.token({
            sub: faker_1.random.uuid(),
            iss: issuer,
            'custom:role': 'UserRole1',
            extraParam: 'claims',
            anotherParam: 111,
            email: faker_1.internet.email(),
            phoneNumber: faker_1.phone.phoneNumber(),
        });
        config.provider.sensor = defaultSensor(token);
        config.sensorConfiguration.health.globalAuthorizer = {
            authorize: [UserRole],
        };
        config.tokenVerifiers = [new src_1.JwksUriTokenVerifier(issuer, jwksUri)];
        const boosterHealthService = new sensor_1.BoosterHealthService(config);
        const boosterResult = (await boosterHealthService.boosterHealth(undefined));
        await jwks.stop();
        (0, expect_1.expect)(boosterResult.code).to.be.eq('NotAuthorizedError');
    });
    it('Only root enabled and without children and details', async () => {
        config.provider.sensor = defaultSensor();
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.ROOT].enabled = true;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].enabled = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].enabled = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].enabled = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].enabled = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.ROOT].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.ROOT].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].showChildren = false;
        // get root
        const boosterResult = await boosterHealth(config);
        // root without children and details
        expectDefaultResult(boosterResult, 'UP', 'booster', 'Booster', 0);
        (0, expect_1.expect)(boosterResult.details).to.be.undefined;
        // other indicators are undefined
        (0, expect_1.expect)(getBoosterDatabase(boosterResult)).to.be.undefined;
        (0, expect_1.expect)(getEventDatabase(boosterResult)).to.be.undefined;
        (0, expect_1.expect)(getBoosterFunction(boosterResult)).to.be.undefined;
        (0, expect_1.expect)(getReadModelsDatabase(boosterResult)).to.be.undefined;
    });
    it('if parent disabled then children are disabled', async () => {
        config.provider.sensor = defaultSensor('', 'booster/database/readmodels');
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.ROOT].enabled = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].enabled = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].enabled = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].enabled = true;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].enabled = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.ROOT].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.ROOT].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].showChildren = false;
        const readModelsResult = await boosterHealth(config);
        (0, expect_1.expect)(readModelsResult).to.be.undefined;
    });
    it('Only ReadModels enabled and without children and details', async () => {
        config.provider.sensor = defaultSensor('', 'booster/database/readmodels');
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.ROOT].enabled = true;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].enabled = true;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].enabled = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].enabled = true;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].enabled = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.ROOT].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].details = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.ROOT].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].showChildren = false;
        config.sensorConfiguration.health.booster[framework_types_1.BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].showChildren = false;
        const readModelsResult = await boosterHealth(config);
        expectDatabaseReadModels(readModelsResult, 'UP');
    });
});
function defaultSensor(token, url) {
    return {
        databaseEventsHealthDetails: (0, sinon_1.fake)(() => { }),
        databaseReadModelsHealthDetails: (0, sinon_1.fake)(() => { }),
        isGraphQLFunctionUp: (0, sinon_1.fake)(() => true),
        isDatabaseEventUp: (0, sinon_1.fake)(() => true),
        areDatabaseReadModelsUp: (0, sinon_1.fake)(() => true),
        databaseUrls: (0, sinon_1.fake)(() => []),
        graphQLFunctionUrl: (0, sinon_1.fake)(() => ''),
        rawRequestToHealthEnvelope: (0, sinon_1.fake)(() => {
            return { token: token, componentPath: url };
        }),
    };
}
async function boosterHealth(config) {
    const boosterHealthService = new sensor_1.BoosterHealthService(config);
    const result = (await boosterHealthService.boosterHealth(undefined));
    return result[0];
}
function getBoosterFunction(boosterResult) {
    var _a;
    return (_a = boosterResult.components) === null || _a === void 0 ? void 0 : _a.find((element) => element.id === 'booster/function');
}
function getBoosterDatabase(boosterResult) {
    var _a;
    return (_a = boosterResult.components) === null || _a === void 0 ? void 0 : _a.find((element) => element.id === 'booster/database');
}
function getEventDatabase(boosterDatabase) {
    var _a;
    return (_a = boosterDatabase.components) === null || _a === void 0 ? void 0 : _a.find((element) => element.id === 'booster/database/events');
}
function getReadModelsDatabase(boosterDatabase) {
    var _a;
    return (_a = boosterDatabase.components) === null || _a === void 0 ? void 0 : _a.find((element) => element.id === 'booster/database/readmodels');
}
function expectDefaultResult(result, status, id, name, componentsLength) {
    (0, expect_1.expect)(result.id).to.be.eq(id);
    (0, expect_1.expect)(result.status).to.be.eq(status);
    (0, expect_1.expect)(result.name).to.be.eq(name);
    if (componentsLength === 0) {
        (0, expect_1.expect)(result.components).to.be.undefined;
    }
    else {
        (0, expect_1.expect)(result.components.length).to.be.eq(componentsLength);
    }
}
function expectBooster(boosterResult, version, status) {
    expectDefaultResult(boosterResult, status, 'booster', 'Booster', 2);
    (0, expect_1.expect)(boosterResult.details.boosterVersion).to.be.eq(version);
}
function expectBoosterFunction(boosterFunction, url, status) {
    expectDefaultResult(boosterFunction, status, 'booster/function', 'Booster Function', 0);
    (0, expect_1.expect)(boosterFunction.details.cpus.length).to.be.gt(0);
    (0, expect_1.expect)(boosterFunction.details.cpus[0].timesPercentages.length).to.be.gt(0);
    (0, expect_1.expect)(boosterFunction.details.memory.totalBytes).to.be.gt(0);
    (0, expect_1.expect)(boosterFunction.details.memory.freeBytes).to.be.gt(0);
    (0, expect_1.expect)(boosterFunction.details.graphQL_url).to.be.eq(url);
}
function expectBoosterDatabase(boosterDatabase, status) {
    expectDefaultResult(boosterDatabase, status, 'booster/database', 'Booster Database', 2);
    (0, expect_1.expect)(boosterDatabase.details).to.not.be.undefined;
}
function expectDatabaseEvents(databaseEvents, status) {
    expectDefaultResult(databaseEvents, status, 'booster/database/events', 'Booster Database Events', 0);
    (0, expect_1.expect)(databaseEvents.details).to.be.undefined;
}
function expectDatabaseEventsWithDetails(databaseEvents, status, details) {
    expectDefaultResult(databaseEvents, status, 'booster/database/events', 'Booster Database Events', 0);
    (0, expect_1.expect)(databaseEvents.details).to.be.deep.eq(details);
}
function expectDatabaseReadModels(databaseReadModels, status) {
    expectDefaultResult(databaseReadModels, status, 'booster/database/readmodels', 'Booster Database ReadModels', 0);
    (0, expect_1.expect)(databaseReadModels.details).to.be.undefined;
}
function expectDatabaseReadModelsWithDetails(databaseReadModels, status, details) {
    expectDefaultResult(databaseReadModels, status, 'booster/database/readmodels', 'Booster Database ReadModels', 0);
    (0, expect_1.expect)(databaseReadModels.details).to.be.deep.eq(details);
}
class UserRole {
}
