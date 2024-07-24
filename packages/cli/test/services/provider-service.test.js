"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const mocha_1 = require("mocha");
const providerService = require("../../src/services/provider-service");
const sinon_1 = require("sinon");
const expect_1 = require("../expect");
const faker_1 = require("faker");
(0, mocha_1.describe)('providerService', () => {
    (0, mocha_1.afterEach)(() => {
        (0, sinon_1.restore)();
    });
    (0, mocha_1.describe)('assertNameIsCorrect', () => {
        it('should throw an error on surpassing project name max length', () => {
            const inputString = faker_1.random.alphaNumeric(faker_1.random.number({ min: 38 }));
            const errorString = `Project name cannot be longer than 37 characters:\n\n    Found: '${inputString}'`;
            (0, expect_1.expect)(() => providerService.assertNameIsCorrect(inputString)).to.throw(errorString);
        });
        it('should throw an error if project name includes a space', () => {
            const inputString = faker_1.lorem.words(2);
            const errorString = `Project name cannot contain spaces:\n\n    Found: '${inputString}'`;
            (0, expect_1.expect)(() => providerService.assertNameIsCorrect(inputString)).to.throw(errorString);
        });
        it('should throw an error if project name includes an uppercase letter', () => {
            const inputString = faker_1.random.alphaNumeric(37).toUpperCase();
            const errorString = `Project name cannot contain uppercase letters:\n\n    Found: '${inputString}'`;
            (0, expect_1.expect)(() => providerService.assertNameIsCorrect(inputString)).to.throw(errorString);
        });
        it('should throw an error if project name includes an underscore', () => {
            const inputString = 'test_project_name';
            const errorString = `Project name cannot contain underscore:\n\n    Found: '${inputString}'`;
            (0, expect_1.expect)(() => providerService.assertNameIsCorrect(inputString)).to.throw(errorString);
        });
        it('should not throw an error if project name is correct', () => {
            const inputString = faker_1.random.alphaNumeric(37);
            (0, expect_1.expect)(() => providerService.assertNameIsCorrect(inputString)).to.not.throw();
        });
    });
    mocha_1.describe.skip('deployToCloudProvider', () => { });
    (0, mocha_1.describe)('startProvider', () => {
        context('when the configured provider implements the run function', () => {
            it('calls the provider start method', async () => {
                const fakeInfrastructure = {
                    start: (0, sinon_1.fake)(),
                };
                const fakeProvider = {
                    infrastructure: sinon_1.fake.returns(fakeInfrastructure),
                };
                const fakeConfig = {
                    appName: 'lolapp',
                    provider: fakeProvider,
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                };
                await providerService.startProvider(3000, fakeConfig);
                (0, expect_1.expect)(fakeInfrastructure.start).to.have.been.calledOnceWith(fakeConfig);
            });
        });
        context('when the configured provider does not implement the start function', () => {
            it('throws an error', async () => {
                const fakeProvider = {
                    infrastructure: sinon_1.fake.returns({}),
                };
                const fakeConfig = {
                    appName: 'lolapp',
                    provider: fakeProvider,
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                };
                await (0, expect_1.expect)(providerService.startProvider(3000, fakeConfig)).to.eventually.be.rejectedWith("Attempted to perform the 'start' operation with a provider that does not support this feature, please check your environment configuration.");
            });
        });
    });
    mocha_1.describe.skip('nukeCloudProviderResources', () => { });
});
