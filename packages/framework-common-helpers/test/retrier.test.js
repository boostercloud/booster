"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const src_1 = require("../src");
const expect_1 = require("./helpers/expect");
class ErrorToRetry extends Error {
}
describe('the `retrier` helpers', () => {
    describe('the `retryIfError` method', () => {
        it('returns the result and does not retry if there is no error', async () => {
            let retries = 0;
            const returnedValue = await (0, src_1.retryIfError)(async () => {
                retries++;
                return 'returned value';
            }, ErrorToRetry);
            (0, expect_1.expect)(returnedValue).to.be.equal('returned value');
            (0, expect_1.expect)(retries).to.be.equal(1);
        });
        it('rethrows a non-expected error and does not retry', async () => {
            let retries = 0;
            const returnedValuePromise = (0, src_1.retryIfError)(async () => {
                retries++;
                throw new Error('unexpected error');
            }, ErrorToRetry);
            await (0, expect_1.expect)(returnedValuePromise).to.eventually.be.rejectedWith('unexpected error');
            (0, expect_1.expect)(retries).to.be.equal(1);
        });
        it('retries 5 times if the expected error happens 4 times', async () => {
            let retries = 0;
            const result = await (0, src_1.retryIfError)(async () => {
                retries++;
                if (retries <= 4)
                    throw new ErrorToRetry('expected error');
                return 'success';
            }, ErrorToRetry);
            (0, expect_1.expect)(result).to.be.equal('success');
            (0, expect_1.expect)(retries).to.be.equal(5);
        });
        it('throws after "maxRetries" is reached with the expected error happening', async () => {
            const maxRetries = 20;
            let retries = 0;
            const returnedValuePromise = (0, src_1.retryIfError)(async () => {
                retries++;
                throw new ErrorToRetry('expected error');
            }, ErrorToRetry, undefined, maxRetries);
            await (0, expect_1.expect)(returnedValuePromise).to.eventually.be.rejectedWith('Reached the maximum number of retries');
            (0, expect_1.expect)(retries).to.be.equal(maxRetries);
        });
    });
});
