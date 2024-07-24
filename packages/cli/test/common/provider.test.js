"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
describe('selectedProvider', () => {
    it('get selected provider: AWS', async () => {
        (0, expect_1.expect)("@boostercloud/framework-provider-aws (AWS) - Currently deprecated" /* Provider.AWS */).to.be.equal('@boostercloud/framework-provider-aws (AWS) - Currently deprecated');
    });
    it('get selected provider: Azure', async () => {
        (0, expect_1.expect)("@boostercloud/framework-provider-azure (Azure)" /* Provider.AZURE */).to.be.equal('@boostercloud/framework-provider-azure (Azure)');
    });
});
