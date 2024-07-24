"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../../expect");
const jwks_uri_token_verifier_1 = require("../../../src/services/token-verifiers/jwks-uri-token-verifier");
const utilities = require("../../../src/services/token-verifiers/utilities");
const sinon_1 = require("sinon");
describe('JwksUriTokenVerifier', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    it('builds a key resolver and calls `verifyJWT`', async () => {
        const fakeClient = { fakeClient: true };
        (0, sinon_1.replace)(utilities, 'getJwksClient', sinon_1.fake.returns(fakeClient));
        const fakeGetKey = { fakeGetKey: true };
        (0, sinon_1.replace)(utilities, 'getKeyWithClient', sinon_1.fake.returns(fakeGetKey));
        const fakeDecodedToken = { header: { kid: '123' }, payload: { sub: '123' } };
        const fakeHeader = { header: true };
        const fakeCallback = (0, sinon_1.fake)();
        const fakeVerifyJWT = (0, sinon_1.fake)((_token, _issuer, getKey) => {
            getKey(fakeHeader, fakeCallback);
            return Promise.resolve(fakeDecodedToken);
        });
        (0, sinon_1.replace)(utilities, 'verifyJWT', fakeVerifyJWT);
        const verifier = new jwks_uri_token_verifier_1.JwksUriTokenVerifier('issuer', 'https://example.com/jwks');
        await (0, expect_1.expect)(verifier.verify('token')).to.eventually.become(fakeDecodedToken);
        (0, expect_1.expect)(utilities.getJwksClient).to.have.been.calledWith('https://example.com/jwks');
        (0, expect_1.expect)(utilities.getKeyWithClient).to.have.been.calledWith(fakeClient, fakeHeader, fakeCallback);
        (0, expect_1.expect)(utilities.verifyJWT).to.have.been.calledWith('token', 'issuer', sinon_1.match.func);
    });
});
