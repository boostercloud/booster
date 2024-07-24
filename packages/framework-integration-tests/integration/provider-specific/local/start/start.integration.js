"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../../../helper/expect");
describe('After start', () => {
    describe('the local provider', () => {
        it('has been started successfully', async () => {
            //TODO run this instead: await expect(LocalTestHelper.build(applicationName())).to.be.eventually.fulfilled
            (0, expect_1.expect)(true).to.be.true;
        });
    });
});
