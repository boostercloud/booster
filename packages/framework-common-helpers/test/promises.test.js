"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const src_1 = require("../src");
const expect_1 = require("./helpers/expect");
describe('the `Promises` helpers', () => {
    describe('the `allSettledAndFulfilled` method', () => {
        it('Does not throw if all promises are fulfilled', async () => {
            const promises = [Promise.resolve(), Promise.resolve(), Promise.resolve()];
            await src_1.Promises.allSettledAndFulfilled(promises);
        });
        it('throws with an array of rejected promises', async () => {
            const rejectedReason1 = 'rejection 1';
            const rejectedReason2 = 'rejection 2';
            const promises = [
                Promise.resolve(),
                Promise.reject(rejectedReason1),
                Promise.reject(rejectedReason2),
                Promise.resolve(),
            ];
            await (0, expect_1.expect)(src_1.Promises.allSettledAndFulfilled(promises)).to.be.rejectedWith(src_1.PromisesError, new RegExp(`.*${rejectedReason1}.*${rejectedReason2}.*`));
        });
        it('it waits for all the promises to finish, even if one of them throws early', async () => {
            const rejectedReason = 'rejected';
            let successfulPromise1Finished = false;
            let successfulPromise2Finished = false;
            const promises = [
                Promise.reject(rejectedReason),
                new Promise((resolve) => setTimeout(() => {
                    successfulPromise1Finished = true;
                    resolve();
                }, 100)),
                new Promise((resolve) => setTimeout(() => {
                    successfulPromise2Finished = true;
                    resolve();
                }, 500)),
            ];
            await (0, expect_1.expect)(src_1.Promises.allSettledAndFulfilled(promises)).to.be.rejectedWith(src_1.PromisesError, new RegExp(rejectedReason));
            (0, expect_1.expect)(successfulPromise1Finished).to.be.true;
            (0, expect_1.expect)(successfulPromise2Finished).to.be.true;
        });
    });
});
