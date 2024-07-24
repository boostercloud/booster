"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../../expect");
const public_key_token_verifier_1 = require("../../../src/services/token-verifiers/public-key-token-verifier");
const utilities = require("../../../src/services/token-verifiers/utilities");
const sinon_1 = require("sinon");
describe('PublicKeyTokenVerifier', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    it('resolves the public key and calls `verifyJWT`', async () => {
        const fakeDecodedToken = { header: { kid: '123' }, payload: { sub: '123' } };
        const fakeVerifyJWT = sinon_1.fake.resolves(fakeDecodedToken);
        (0, sinon_1.replace)(utilities, 'verifyJWT', fakeVerifyJWT);
        const publicKey = 'public key';
        const publicKeyResolver = Promise.resolve(publicKey);
        const verifier = new public_key_token_verifier_1.PublicKeyTokenVerifier('https://example.com/jwks', publicKeyResolver);
        await (0, expect_1.expect)(verifier.verify('token')).to.eventually.become(fakeDecodedToken);
        (0, expect_1.expect)(fakeVerifyJWT).to.have.been.calledWith('token', 'https://example.com/jwks', publicKey);
    });
});
