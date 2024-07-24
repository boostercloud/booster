"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_express_mock_1 = require("sinon-express-mock");
const sinon_1 = require("sinon");
const framework_provider_local_1 = require("@boostercloud/framework-provider-local");
const graphql_1 = require("../../src/controllers/graphql");
const expect_1 = require("../expect");
const faker_1 = require("faker");
describe('GraphQL controller', () => {
    let mockQueryResponse;
    let graphQLServiceStub;
    let queryStub;
    let nextStub;
    let sut;
    beforeEach(() => {
        mockQueryResponse = {
            status: 'success',
            result: {
                data: {
                    ChangeCartItem: true,
                },
            },
        };
        graphQLServiceStub = (0, sinon_1.createStubInstance)(framework_provider_local_1.GraphQLService);
        queryStub = (0, sinon_1.stub)().resolves(mockQueryResponse);
        nextStub = (0, sinon_1.stub)();
        (0, sinon_1.replace)(graphQLServiceStub, 'handleGraphQLRequest', queryStub);
        sut = new graphql_1.GraphQLController(graphQLServiceStub);
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('handleGraphQL', () => {
        let mockRequest;
        let mockResponse;
        let jsonStub;
        beforeEach(() => {
            mockRequest = (0, sinon_express_mock_1.mockReq)({});
            mockResponse = (0, sinon_express_mock_1.mockRes)();
            jsonStub = (0, sinon_1.stub)();
            (0, sinon_1.replace)(mockResponse, 'json', jsonStub);
        });
        it('should call GraphQLService.handleGraphQLRequest', async () => {
            await sut.handleGraphQL(mockRequest, mockResponse, nextStub);
            (0, expect_1.expect)(queryStub).to.have.been.calledOnce.and.calledWith(mockRequest);
        });
        context('on success', () => {
            beforeEach(async () => {
                await sut.handleGraphQL(mockRequest, mockResponse, nextStub);
            });
            it('should not call next', () => {
                (0, expect_1.expect)(nextStub).not.to.be.called;
            });
            it('should return expected status code', async () => {
                (0, expect_1.expect)(mockResponse.status).to.be.calledOnceWith(200);
            });
            it('should call response.json with expected arguments', () => {
                (0, expect_1.expect)(jsonStub).to.be.calledOnceWith({ ...mockQueryResponse.result });
            });
        });
        context('on failure', () => {
            let error;
            beforeEach(async () => {
                error = new Error(faker_1.lorem.words());
                queryStub.rejects(error);
                await sut.handleGraphQL(mockRequest, mockResponse, nextStub);
            });
            it('should return expected status code', async () => {
                (0, expect_1.expect)(mockResponse.status).to.be.calledOnceWith(500);
            });
            it('should call response.json with expected arguments', () => {
                (0, expect_1.expect)(jsonStub).to.be.calledOnceWith({ title: 'Error', reason: error.message });
            });
            it('should call next', () => {
                (0, expect_1.expect)(nextStub).to.have.been.calledOnceWith(error);
            });
        });
    });
});
