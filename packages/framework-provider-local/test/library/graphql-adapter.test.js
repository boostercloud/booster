"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const sinon_express_mock_1 = require("sinon-express-mock");
const sinon_1 = require("sinon");
const graphql_adapter_1 = require("../../src/library/graphql-adapter");
const expect_1 = require("../expect");
const framework_types_1 = require("@boostercloud/framework-types");
const faker_1 = require("faker");
describe('Local provider graphql-adapter', () => {
    describe('rawGraphQLRequestToEnvelope', () => {
        let mockUuid;
        let mockBody;
        let mockRequest;
        let mockUserToken;
        const mockConfig = new framework_types_1.BoosterConfig('test');
        mockConfig.logger = {
            debug: (0, sinon_1.fake)(),
            info: (0, sinon_1.fake)(),
            warn: (0, sinon_1.fake)(),
            error: (0, sinon_1.fake)(),
        };
        let generateStub;
        beforeEach(() => {
            mockUuid = faker_1.random.uuid();
            mockUserToken = faker_1.random.uuid();
            mockBody = {
                query: '',
                variables: {},
            };
            mockRequest = (0, sinon_express_mock_1.mockReq)();
            mockRequest.body = mockBody;
            mockRequest.headers = {
                authorization: mockUserToken,
            };
            generateStub = (0, sinon_1.stub)().returns(mockUuid);
            (0, sinon_1.replace)(framework_types_1.UUID, 'generate', generateStub);
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('should call logger.debug', async () => {
            var _a;
            await (0, graphql_adapter_1.rawGraphQLRequestToEnvelope)(mockConfig, mockRequest);
            (0, expect_1.expect)((_a = mockConfig.logger) === null || _a === void 0 ? void 0 : _a.debug).to.have.been.calledOnceWith('[Booster]|graphql-adapter#expressHttpMessageToEnvelope: ', 'Received GraphQL request: \n- Headers: ', mockRequest.headers, '\n- Body: ', mockRequest.body);
        });
        it('should generate expected envelop', async () => {
            const result = await (0, graphql_adapter_1.rawGraphQLRequestToEnvelope)(mockConfig, mockRequest);
            (0, expect_1.expect)(result).to.be.deep.equal({
                requestID: mockUuid,
                eventType: 'MESSAGE',
                connectionID: undefined,
                token: mockUserToken,
                value: mockBody,
                context: {
                    request: {
                        headers: mockRequest.headers,
                        body: mockRequest.body,
                    },
                    rawContext: mockRequest,
                },
            });
        });
    });
});
