"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
const expect_1 = require("./expect");
const framework_types_1 = require("@boostercloud/framework-types");
const mock_jwks_1 = require("mock-jwks");
const faker_1 = require("faker");
const booster_token_verifier_1 = require("../src/booster-token-verifier");
const jwks_uri_token_verifier_1 = require("../src/services/token-verifiers/jwks-uri-token-verifier");
describe('the "verifyToken" method', () => {
    const auth0VerifierUri = 'https://myauth0app.auth0.com/';
    const issuer = 'auth0';
    const jwks = (0, mock_jwks_1.default)(auth0VerifierUri);
    const email = faker_1.internet.email();
    const phoneNumber = faker_1.phone.phoneNumber();
    const userId = faker_1.random.uuid();
    const config = new framework_types_1.BoosterConfig('test');
    let boosterTokenVerifier;
    config.tokenVerifiers = [new jwks_uri_token_verifier_1.JwksUriTokenVerifier(issuer, auth0VerifierUri + '.well-known/jwks.json')];
    beforeEach(() => {
        jwks.start();
        boosterTokenVerifier = new booster_token_verifier_1.BoosterTokenVerifier(config);
    });
    afterEach(async () => {
        await jwks.stop();
    });
    it('accepts custom claims and generates a UserEnvelope with them', async () => {
        var _a, _b;
        const token = jwks.token({
            sub: userId,
            iss: issuer,
            'custom:role': 'User',
            extraParam: 'claims',
            anotherParam: 111,
            email,
            phoneNumber,
        });
        const expectedUser = {
            id: userId,
            username: email,
            roles: ['User'],
            claims: {
                sub: userId,
                iss: issuer,
                'custom:role': 'User',
                extraParam: 'claims',
                anotherParam: 111,
                email,
                phoneNumber,
            },
            header: {
                alg: 'RS256',
            },
        };
        const user = await boosterTokenVerifier.verify(token);
        (0, expect_1.expect)(user.claims).to.deep.equals(expectedUser.claims);
        (0, expect_1.expect)((_a = user.header) === null || _a === void 0 ? void 0 : _a.alg).equals((_b = expectedUser.header) === null || _b === void 0 ? void 0 : _b.alg);
        (0, expect_1.expect)(user.roles).to.have.all.members(expectedUser.roles);
    });
    it('decode and verify an auth token with the custom roles', async () => {
        var _a, _b;
        const token = jwks.token({
            sub: userId,
            iss: issuer,
            'custom:role': 'User',
            email,
            phoneNumber,
        });
        const expectedUser = {
            id: userId,
            username: email,
            roles: ['User'],
            claims: {
                sub: userId,
                iss: issuer,
                'custom:role': 'User',
                email,
                phoneNumber,
            },
            header: {
                alg: 'RS256',
            },
        };
        const user = await boosterTokenVerifier.verify(token);
        (0, expect_1.expect)(user.claims).to.deep.equals(expectedUser.claims);
        (0, expect_1.expect)((_a = user.header) === null || _a === void 0 ? void 0 : _a.alg).equals((_b = expectedUser.header) === null || _b === void 0 ? void 0 : _b.alg);
        (0, expect_1.expect)(user.roles).to.have.all.members(expectedUser.roles);
    });
    it('decode and verify an auth token with an empty custom role', async () => {
        var _a, _b;
        const token = jwks.token({
            sub: userId,
            iss: issuer,
            'custom:role': '',
            email,
            phoneNumber,
        });
        const expectedUser = {
            id: userId,
            username: email,
            roles: [],
            claims: {
                sub: userId,
                iss: issuer,
                'custom:role': '',
                email,
                phoneNumber,
            },
            header: {
                alg: 'RS256',
            },
        };
        const user = await boosterTokenVerifier.verify(token);
        (0, expect_1.expect)(user.claims).to.deep.equals(expectedUser.claims);
        (0, expect_1.expect)((_a = user.header) === null || _a === void 0 ? void 0 : _a.alg).equals((_b = expectedUser.header) === null || _b === void 0 ? void 0 : _b.alg);
        (0, expect_1.expect)(user.roles).to.have.all.members(expectedUser.roles);
    });
    it('decode and verify an auth token with a list of custom roles', async () => {
        var _a, _b;
        const token = jwks.token({
            sub: userId,
            iss: issuer,
            'custom:role': ['User', 'Other'],
            email,
            phoneNumber,
        });
        const expectedUser = {
            id: userId,
            username: email,
            roles: ['User', 'Other'],
            claims: {
                sub: userId,
                iss: issuer,
                'custom:role': ['User', 'Other'],
                email,
                phoneNumber,
            },
            header: {
                alg: 'RS256',
            },
        };
        const user = await boosterTokenVerifier.verify(token);
        (0, expect_1.expect)(user.claims).to.deep.equals(expectedUser.claims);
        (0, expect_1.expect)((_a = user.header) === null || _a === void 0 ? void 0 : _a.alg).equals((_b = expectedUser.header) === null || _b === void 0 ? void 0 : _b.alg);
        (0, expect_1.expect)(user.roles).to.have.all.members(expectedUser.roles);
    });
    it('fails if role is a number', async () => {
        const token = jwks.token({
            sub: userId,
            iss: issuer,
            'custom:role': 123,
            email,
            phoneNumber,
        });
        const verifyFunction = boosterTokenVerifier.verify(token);
        await (0, expect_1.expect)(verifyFunction).to.eventually.be.rejectedWith('Error: Invalid role format 123. Valid format are Array<string> or string');
    });
    it('fails if role is not a list of strings', async () => {
        const token = jwks.token({
            sub: userId,
            iss: issuer,
            'custom:role': ['a', 'b', 123],
            email,
            phoneNumber,
        });
        const user = boosterTokenVerifier.verify(token);
        await (0, expect_1.expect)(user).to.eventually.be.rejectedWith('Error: Invalid role format 123. Valid format are Array<string> or string');
    });
    it('fails if a different issuer emitted the token', async () => {
        const token = jwks.token({
            iss: 'firebase',
        });
        const verifyFunction = boosterTokenVerifier.verify(token);
        await (0, expect_1.expect)(verifyFunction).to.eventually.be.rejected;
    });
    it('fails if a token has expired', async () => {
        const token = jwks.token({
            sub: userId,
            iss: issuer,
            'custom:role': 'User',
            email: email,
            phoneNumber,
            exp: 0,
        });
        const verifyFunction = boosterTokenVerifier.verify(token);
        await (0, expect_1.expect)(verifyFunction).to.eventually.be.rejectedWith('jwt expired');
    });
    it('fails if current time is before the notBefore claim of the token ', async () => {
        const token = jwks.token({
            sub: userId,
            iss: issuer,
            'custom:role': ['User', 'Other'],
            email,
            phoneNumber,
            nbf: Math.floor(Date.now() / 1000) + 999999,
        });
        const verifyFunction = boosterTokenVerifier.verify(token);
        await (0, expect_1.expect)(verifyFunction).to.eventually.be.rejectedWith('jwt not active');
    });
    it("fails if extra validation doesn't match", async () => {
        const token = jwks.token({
            sub: userId,
            iss: issuer,
            'custom:role': 'User',
            email: email,
            phoneNumber,
        });
        class ExtendedJwksUriTokenVerifier extends jwks_uri_token_verifier_1.JwksUriTokenVerifier {
            async verify(token) {
                const decodedToken = await super.verify(token);
                const promiseSolved = await Promise.resolve();
                console.log(promiseSolved);
                if (decodedToken.payload['custom:role'] !== 'Admin') {
                    throw 'Unauthorized';
                }
                return decodedToken;
            }
        }
        const configWithExtraValidation = new framework_types_1.BoosterConfig('test with extra validation');
        configWithExtraValidation.tokenVerifiers = [
            new ExtendedJwksUriTokenVerifier(issuer, auth0VerifierUri + '.well-known/jwks.json'),
        ];
        const tokenVerifier = new booster_token_verifier_1.BoosterTokenVerifier(configWithExtraValidation);
        const verifyFunction = tokenVerifier.verify(token);
        await (0, expect_1.expect)(verifyFunction).to.eventually.be.rejectedWith('Unauthorized');
    });
    it('fails if a custom token verifier raises an exception', async () => {
        const token = jwks.token({
            sub: userId,
            iss: issuer,
        });
        class ExtendedJwksUriTokenVerifier2 extends jwks_uri_token_verifier_1.JwksUriTokenVerifier {
            async verify(token) {
                const decodedToken = await super.verify(token);
                const header = decodedToken.header;
                const promiseSolved = await Promise.resolve();
                console.log(promiseSolved);
                if ((header === null || header === void 0 ? void 0 : header.alg) !== 'RS512') {
                    throw 'Invalid token encoding';
                }
                return decodedToken;
            }
        }
        const configWithExtraValidation = new framework_types_1.BoosterConfig('test with extra validation');
        configWithExtraValidation.tokenVerifiers = [
            new ExtendedJwksUriTokenVerifier2(issuer, auth0VerifierUri + '.well-known/jwks.json'),
        ];
        const tokenVerifier = new booster_token_verifier_1.BoosterTokenVerifier(configWithExtraValidation);
        const verifyFunction = tokenVerifier.verify(token);
        await (0, expect_1.expect)(verifyFunction).to.eventually.be.rejectedWith('Invalid token encoding');
    });
});
