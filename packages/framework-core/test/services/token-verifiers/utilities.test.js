"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../../expect");
const sinon_1 = require("sinon");
const utilities_1 = require("../../../src/services/token-verifiers/utilities");
const jwt = require("jsonwebtoken");
describe('function `getJwksClient`', () => {
    it('returns a JwksClient instance', () => {
        const fakeJwksClient = sinon_1.fake.returns({});
        // This is a workaround to stub the default export function from `jwks-rsa`
        require('jwks-rsa');
        delete require.cache[require.resolve('jwks-rsa')];
        require.cache[require.resolve('jwks-rsa')] = {
            exports: fakeJwksClient,
        };
        (0, utilities_1.getJwksClient)('https://example.com/jwks');
        (0, expect_1.expect)(fakeJwksClient).to.have.been.calledWith({
            jwksUri: 'https://example.com/jwks',
            cache: true,
            cacheMaxAge: 15 * 60 * 1000,
        });
        // Undo the workaround
        delete require.cache[require.resolve('jwks-rsa')];
    });
});
describe('function `getKeyWithClient`', () => {
    context('when the header does not include a "kid" property', () => {
        it('calls the callback function with an error', () => {
            const fakeJwksClient = {};
            const fakeHeader = {};
            const fakeCallback = (0, sinon_1.fake)();
            (0, utilities_1.getKeyWithClient)(fakeJwksClient, fakeHeader, fakeCallback);
            (0, expect_1.expect)(fakeCallback).to.have.been.calledWithMatch({ message: 'JWT kid not found' });
        });
    });
    context('when getting the public key fails', () => {
        it('calls the callback function with an error', () => {
            // eslint-disable-next-line @typescript-eslint/ban-types
            const fakeGetSigningKeyCallback = (0, sinon_1.fake)((_kid, callback) => callback(new Error('Error getting public key')));
            const fakeJwksClient = {
                getSigningKey: fakeGetSigningKeyCallback,
            };
            const fakeHeader = { kid: '123' };
            const fakeCallback = (0, sinon_1.fake)();
            (0, utilities_1.getKeyWithClient)(fakeJwksClient, fakeHeader, fakeCallback);
            (0, expect_1.expect)(fakeCallback).to.have.been.calledWithMatch({ message: 'Error getting public key' });
        });
    });
    context('when getting the public key succeeds', () => {
        it('calls the callback function with the public key', () => {
            // eslint-disable-next-line @typescript-eslint/ban-types
            const fakeGetSigningKeyCallback = (0, sinon_1.fake)((_kid, callback) => callback(null, { getPublicKey: () => 'public-key' }));
            const fakeJwksClient = {
                getSigningKey: fakeGetSigningKeyCallback,
            };
            const fakeHeader = { kid: '123' };
            const fakeCallback = (0, sinon_1.fake)();
            (0, utilities_1.getKeyWithClient)(fakeJwksClient, fakeHeader, fakeCallback);
            (0, expect_1.expect)(fakeCallback).to.have.been.calledWith(null, 'public-key');
        });
    });
});
describe('function `verifyJWT`', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    context('when the token is verified', () => {
        it('resolves to a decoded token', async () => {
            const fakeToken = 'Bearer token';
            const fakeIssuer = 'issuer';
            const fakePublicKey = 'public-key';
            const fakeDecodedToken = { a: 'token' };
            // eslint-disable-next-line @typescript-eslint/ban-types
            const fakeVerify = (0, sinon_1.fake)((_token, _key, _options, callback) => callback(null, fakeDecodedToken));
            (0, sinon_1.replace)(jwt, 'verify', fakeVerify);
            await (0, expect_1.expect)((0, utilities_1.verifyJWT)(fakeToken, 'issuer', fakePublicKey)).to.eventually.become(fakeDecodedToken);
            (0, expect_1.expect)(fakeVerify).to.have.been.calledWith('token', fakePublicKey, {
                algorithms: ['RS256'],
                issuer: fakeIssuer,
                complete: true,
            }, sinon_1.match.func);
        });
    });
    context('when the token is not verified', () => {
        it('rejects with an error', async () => {
            const fakeToken = 'Bearer token';
            const fakeIssuer = 'issuer';
            const fakePublicKey = 'public-key';
            const fakeError = new Error('Error verifying token');
            // eslint-disable-next-line @typescript-eslint/ban-types
            const fakeVerify = (0, sinon_1.fake)((_token, _key, _options, callback) => callback(fakeError));
            (0, sinon_1.replace)(jwt, 'verify', fakeVerify);
            await (0, expect_1.expect)((0, utilities_1.verifyJWT)(fakeToken, 'issuer', fakePublicKey)).to.eventually.be.rejectedWith(fakeError);
            (0, expect_1.expect)(fakeVerify).to.have.been.calledWith('token', fakePublicKey, {
                algorithms: ['RS256'],
                issuer: fakeIssuer,
                complete: true,
            }, sinon_1.match.func);
        });
    });
});
