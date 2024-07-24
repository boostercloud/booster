"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const faker_1 = require("faker");
const s3utils_1 = require("../../src/infrastructure/s3utils");
const sinon_1 = require("sinon");
const expect_1 = require("../expect");
describe('s3 utils', () => {
    let s3;
    describe('s3BucketExists', () => {
        let bucketName;
        let promiseStub;
        let headBucketStub;
        beforeEach(() => {
            bucketName = faker_1.random.alphaNumeric(10);
            promiseStub = (0, sinon_1.stub)();
            headBucketStub = (0, sinon_1.stub)();
        });
        context('bucket found', () => {
            beforeEach(() => {
                promiseStub.resolves();
                headBucketStub.returns({
                    promise: promiseStub,
                });
                s3 = {
                    headBucket: headBucketStub,
                };
            });
            it('should return true', async () => {
                const result = await (0, s3utils_1.s3BucketExists)(bucketName, s3);
                (0, expect_1.expect)(headBucketStub).to.have.been.calledOnceWith({ Bucket: bucketName });
                (0, expect_1.expect)(result).to.be.true;
            });
        });
        context('an error is thrown', () => {
            beforeEach(() => {
                promiseStub.rejects();
                headBucketStub.returns({
                    promise: promiseStub,
                });
                s3 = {
                    headBucket: headBucketStub,
                };
            });
            it('should return false', async () => {
                const result = await (0, s3utils_1.s3BucketExists)(bucketName, s3);
                (0, expect_1.expect)(headBucketStub).to.have.been.calledOnceWith({ Bucket: bucketName });
                (0, expect_1.expect)(result).to.be.false;
            });
        });
    });
});
