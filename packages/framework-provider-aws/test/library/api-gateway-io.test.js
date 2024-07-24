"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
const framework_types_1 = require("@boostercloud/framework-types");
const api_gateway_io_1 = require("../../src/library/api-gateway-io");
describe('the requestFailed method', () => {
    it('returns a proper body with several errors', async () => {
        const testCases = [
            {
                input: new framework_types_1.InvalidParameterError('error message'),
                expectedOutput: {
                    statusCode: 400,
                    title: 'Invalid Parameter Error',
                },
            },
            {
                input: new framework_types_1.NotAuthorizedError('error message'),
                expectedOutput: {
                    statusCode: 401,
                    title: 'Not Authorized Error',
                },
            },
            {
                input: new framework_types_1.NotFoundError('error message'),
                expectedOutput: {
                    statusCode: 404,
                    title: 'Not Found Error',
                },
            },
            {
                input: new framework_types_1.InvalidVersionError('error message'),
                expectedOutput: {
                    statusCode: 422,
                    title: 'Invalid Version Error',
                },
            },
        ];
        for (const testCase of testCases) {
            const testDescription = `In test case '${testCase.input.constructor.name}'`;
            const got = await (0, api_gateway_io_1.requestFailed)(testCase.input);
            (0, expect_1.expect)(got.statusCode).to.be.equal(testCase.expectedOutput.statusCode, testDescription);
            const body = JSON.parse(got.body);
            (0, expect_1.expect)(body.title).to.be.equal(testCase.expectedOutput.title, testDescription);
        }
    });
});
