"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const sinon_1 = require("sinon");
const expect_1 = require("./helpers/expect");
const rocket_loader_1 = require("../src/rocket-loader");
const rocketDescriptor = {
    packageName: 'some-package-name',
    parameters: { some: 'parameters' },
};
describe('RocketLoader', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('loadRocket', () => {
        it('throws an error when the rocket infrastructure package is not found', () => {
            (0, sinon_1.replace)(rocket_loader_1.RocketLoader, 'requireRocket', (0, sinon_1.fake)());
            (0, expect_1.expect)(() => {
                rocket_loader_1.RocketLoader.loadRocket(rocketDescriptor);
            }).to.throw(/Could not load the rocket infrastructure package/);
        });
        it("throws an error when the package don't implement a builder method", () => {
            (0, sinon_1.replace)(rocket_loader_1.RocketLoader, 'requireRocket', sinon_1.fake.returns({
                whatever: true,
            }));
            (0, expect_1.expect)(() => {
                rocket_loader_1.RocketLoader.loadRocket(rocketDescriptor);
            }).to.throw(/Could not initialize rocket infrastructure package/);
        });
        it("throws an error when the package don't implement the 'InfrastructureRocket' interface", () => {
            (0, sinon_1.replace)(rocket_loader_1.RocketLoader, 'requireRocket', sinon_1.fake.returns(() => {
                return {
                    whatever: true,
                };
            }));
            (0, expect_1.expect)(() => {
                rocket_loader_1.RocketLoader.loadRocket(rocketDescriptor);
            }).to.throw(/The package.*doesn't seem to be a rocket/);
        });
        it('returns the loaded rocket properly initialized when it passes all checks', () => {
            (0, sinon_1.replace)(rocket_loader_1.RocketLoader, 'requireRocket', sinon_1.fake.returns(() => ({ mountStack: (0, sinon_1.fake)() })));
            const rocket = rocket_loader_1.RocketLoader.loadRocket(rocketDescriptor);
            (0, expect_1.expect)(rocket.mountStack).to.be.a('function');
        });
    });
});
