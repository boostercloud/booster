"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
const framework_types_1 = require("@boostercloud/framework-types");
const api_adapter_1 = require("../../src/library/api-adapter");
describe('API adapter', () => {
    describe('The "requestFailed" method', () => {
        const testCases = [
            {
                input: new framework_types_1.InvalidParameterError('error message'),
                expectedOutput: {
                    status: 400,
                    title: 'Invalid Parameter Error',
                },
            },
            {
                input: new framework_types_1.NotAuthorizedError('error message'),
                expectedOutput: {
                    status: 401,
                    title: 'Not Authorized Error',
                },
            },
            {
                input: new framework_types_1.NotFoundError('error message'),
                expectedOutput: {
                    status: 404,
                    title: 'Not Found Error',
                },
            },
            {
                input: new framework_types_1.InvalidVersionError('error message'),
                expectedOutput: {
                    status: 422,
                    title: 'Invalid Version Error',
                },
            },
        ];
        for (const testCase of testCases) {
            it(`returns the proper body for error '${testCase.input.constructor.name}'`, async () => {
                const got = await (0, api_adapter_1.requestFailed)(testCase.input);
                (0, expect_1.expect)(got.status).to.be.equal(testCase.expectedOutput.status);
                const body = JSON.parse(got.body);
                (0, expect_1.expect)(body.title).to.be.equal(testCase.expectedOutput.title);
            });
        }
    });
});
