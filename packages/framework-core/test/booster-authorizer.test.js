"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("./expect");
const framework_types_1 = require("@boostercloud/framework-types");
const booster_authorizer_1 = require("../src/booster-authorizer");
describe('BoosterAuthorizer', () => {
    class Admin {
    }
    describe('The `allowAccess` method', () => {
        it('should return a resolved promise', async () => {
            await (0, expect_1.expect)(booster_authorizer_1.BoosterAuthorizer.allowAccess()).to.eventually.be.fulfilled;
        });
    });
    describe('The `denyAccess` method', () => {
        it('should return a rejected promise', async () => {
            await (0, expect_1.expect)(booster_authorizer_1.BoosterAuthorizer.denyAccess()).to.eventually.be.rejectedWith(framework_types_1.NotAuthorizedError);
        });
    });
    describe('The `authorizeRoles` method', () => {
        it('should return a resolved promise if the user has one of the authorized roles', async () => {
            const user = {
                roles: ['Admin', 'Developer'],
            };
            await (0, expect_1.expect)(booster_authorizer_1.BoosterAuthorizer.authorizeRoles([Admin], user)).to.eventually.be.fulfilled;
        });
        it('should return a rejected promise if the user does not have any of the authorized roles', async () => {
            const user = {
                roles: ['Reader'],
            };
            await (0, expect_1.expect)(booster_authorizer_1.BoosterAuthorizer.authorizeRoles([Admin], user)).to.eventually.be.rejectedWith(framework_types_1.NotAuthorizedError);
        });
    });
});
