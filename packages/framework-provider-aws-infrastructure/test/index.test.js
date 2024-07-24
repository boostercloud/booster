"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_1 = require("sinon");
const expect_1 = require("./expect");
const infra = require("../src/infrastructure");
const index_1 = require("../src/index");
const framework_common_helpers_1 = require("@boostercloud/framework-common-helpers");
describe('the `framework-provider-aws-infrastructure` package', () => {
    // eslint-disable-next-line @typescript-eslint/ban-types
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('the `Infrastructure` function', () => {
        context('with no rockets', () => {
            const providerInfrastructure = (0, index_1.Infrastructure)();
            it('returns a `ProviderInfrastructure` object', () => {
                (0, expect_1.expect)(providerInfrastructure).to.be.an('object');
                (0, expect_1.expect)(providerInfrastructure.deploy).to.be.a('function');
                (0, expect_1.expect)(providerInfrastructure.nuke).to.be.a('function');
            });
            describe('deploy', () => {
                it('is called with no rockets', async () => {
                    (0, sinon_1.replace)(infra, 'deploy', (0, sinon_1.fake)());
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const fakeConfig = { fake: 'config' };
                    if (providerInfrastructure.deploy)
                        await providerInfrastructure.deploy(fakeConfig);
                    (0, expect_1.expect)(infra.deploy).to.have.been.calledWith(fakeConfig);
                });
                it('throws an error if the deploy process failed', async () => {
                    const errorMessage = new Error('Ooops');
                    (0, sinon_1.replace)(infra, 'deploy', sinon_1.fake.throws(errorMessage));
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const fakeConfig = { fake: 'config' };
                    (0, expect_1.expect)(providerInfrastructure.deploy).to.be.a('function');
                    if (providerInfrastructure.deploy)
                        await (0, expect_1.expect)(providerInfrastructure.deploy(fakeConfig)).to.be.rejectedWith(errorMessage);
                });
            });
            describe('nuke', () => {
                xit('initializes nuke with no rockets');
                it('throws error if the nuke process fails', async () => {
                    const errorMessage = new Error('Ooops');
                    (0, sinon_1.replace)(infra, 'nuke', sinon_1.fake.throws(errorMessage));
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const fakeConfig = { fake: 'config' };
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const providerInfrastructureAlias = providerInfrastructure;
                    await (0, expect_1.expect)(providerInfrastructureAlias.nuke(fakeConfig)).to.be.rejectedWith(errorMessage);
                });
            });
        });
        context('with a list of rockets', () => {
            const fakePackageList = [
                {
                    packageName: 'some-package-name',
                    parameters: {
                        some: 'parameters',
                    },
                },
            ];
            it('returns a `ProviderInfrastructure` object', () => {
                (0, sinon_1.replace)(framework_common_helpers_1.RocketLoader, 'loadRocket', (0, sinon_1.fake)());
                const providerInfrastructure = (0, index_1.Infrastructure)(fakePackageList);
                (0, expect_1.expect)(providerInfrastructure).to.be.an('object');
                (0, expect_1.expect)(providerInfrastructure.deploy).to.be.a('function');
                (0, expect_1.expect)(providerInfrastructure.nuke).to.be.a('function');
            });
            describe('deploy', () => {
                it('is called with rockets', async () => {
                    const fakeLoadedRocket = { thisIs: 'aRocket' };
                    const fakeLoadRocket = sinon_1.fake.returns(fakeLoadedRocket);
                    (0, sinon_1.replace)(framework_common_helpers_1.RocketLoader, 'loadRocket', fakeLoadRocket);
                    const providerInfrastructure = (0, index_1.Infrastructure)(fakePackageList);
                    (0, sinon_1.replace)(infra, 'deploy', (0, sinon_1.fake)());
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const fakeConfig = { fake: 'config' };
                    if (providerInfrastructure.deploy) {
                        await providerInfrastructure.deploy(fakeConfig);
                    }
                    (0, expect_1.expect)(fakeLoadRocket).to.have.been.calledOnceWith(fakePackageList[0]);
                    (0, expect_1.expect)(infra.deploy).to.have.been.calledWith(fakeConfig, [fakeLoadedRocket]);
                });
            });
            describe('nuke', () => {
                it('is called with rockets', async () => {
                    const fakeLoadedRocket = { thisIs: 'aRocket' };
                    const fakeLoadRocket = sinon_1.fake.returns(fakeLoadedRocket);
                    (0, sinon_1.replace)(framework_common_helpers_1.RocketLoader, 'loadRocket', fakeLoadRocket);
                    const providerInfrastructure = (0, index_1.Infrastructure)(fakePackageList);
                    (0, sinon_1.replace)(infra, 'nuke', (0, sinon_1.fake)());
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const fakeConfig = { fake: 'config' };
                    if (providerInfrastructure.nuke) {
                        await providerInfrastructure.nuke(fakeConfig);
                    }
                    (0, expect_1.expect)(fakeLoadRocket).to.have.been.calledOnceWith(fakePackageList[0]);
                    (0, expect_1.expect)(infra.nuke).to.have.been.calledWith(fakeConfig, [fakeLoadedRocket]);
                });
            });
        });
    });
});
