"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("./expect");
const sinon_1 = require("sinon");
const rewire = require('rewire');
const awsSdk = require('aws-sdk');
const providerPackage = rewire('../src/index');
const providerPackageSetup = rewire('../src/setup');
const fakeInfrastructure = sinon_1.fake.returns({});
providerPackageSetup.__set__('loadInfrastructurePackage', () => ({
    Infrastructure: fakeInfrastructure,
}));
describe('the `framework-provider-aws` package', () => {
    describe('the `Provider` function', () => {
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        context('with no rockets', () => {
            it('returns an empty `ProviderLibrary` when DynamoDB is undefined', () => {
                (0, sinon_1.stub)(awsSdk, 'DynamoDB').returns(undefined);
                const providerLibrary = providerPackage.Provider();
                (0, expect_1.expect)(providerLibrary).to.be.an('object');
                (0, expect_1.expect)(providerLibrary).to.deep.equal({});
            });
        });
        context('with a list of rockets', () => {
            const rockets = [
                {
                    packageName: 'some-package-name',
                    parameters: {
                        whatever: true,
                    },
                },
            ];
            const providerLibrary = providerPackageSetup.Provider(rockets);
            it('returns a `ProviderLibrary` object', () => {
                (0, expect_1.expect)(providerLibrary).to.be.an('object');
                (0, expect_1.expect)(providerLibrary.api).to.be.an('object');
                (0, expect_1.expect)(providerLibrary.connections).to.be.an('object');
                (0, expect_1.expect)(providerLibrary.events).to.be.an('object');
                (0, expect_1.expect)(providerLibrary.graphQL).to.be.an('object');
                (0, expect_1.expect)(providerLibrary.infrastructure).to.be.a('function');
                (0, expect_1.expect)(providerLibrary.readModels).to.be.an('object');
            });
            describe('infrastructure', () => {
                it('is loaded with a list of rockets', () => {
                    providerLibrary.infrastructure();
                    (0, expect_1.expect)(fakeInfrastructure).to.have.been.calledWith(rockets);
                });
            });
        });
    });
});
