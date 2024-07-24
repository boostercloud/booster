"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
const chai_1 = require("chai");
const src_1 = require("../../src");
describe('the `Role` decorator', () => {
    afterEach(() => {
        for (const roleName in src_1.Booster.config.roles) {
            delete src_1.Booster.config.roles[roleName];
        }
    });
    context('when no auth metadata is provided', () => {
        it('registers a role in the Booster configuration', () => {
            let SomeRole = class SomeRole {
            };
            SomeRole = tslib_1.__decorate([
                (0, src_1.Role)({ auth: {} })
            ], SomeRole);
            (0, chai_1.expect)(src_1.Booster.config.roles[SomeRole.name]).to.deep.equal({ auth: {} });
        });
    });
    context('when auth metadata is provided', () => {
        it('registers a role in the Booster configuration and sets the auth metadata', () => {
            let SomeRole = class SomeRole {
            };
            SomeRole = tslib_1.__decorate([
                (0, src_1.Role)({ auth: { signUpMethods: ['email', 'phone'], skipConfirmation: true } })
            ], SomeRole);
            (0, chai_1.expect)(src_1.Booster.config.roles[SomeRole.name]).to.deep.equal({
                auth: { signUpMethods: ['email', 'phone'], skipConfirmation: true },
            });
        });
    });
});
