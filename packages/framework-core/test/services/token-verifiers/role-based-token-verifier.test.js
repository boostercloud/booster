"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const role_based_token_verifier_1 = require("../../../src/services/token-verifiers/role-based-token-verifier");
const expect_1 = require("../../expect");
describe('DEFAULT_ROLES_CLAIM', () => {
    it('should be "custom:role"', () => {
        (0, expect_1.expect)(role_based_token_verifier_1.DEFAULT_ROLES_CLAIM).to.equal('custom:role');
    });
});
describe('abstract class RoleBasedTokenVerifier', () => {
    context('a class that extends it', () => {
        context('when the roles claim is not set', () => {
            it('can build a `UserEnvelope` from a decoded token taking the roles from the default roles claim', async () => {
                const roles = ['ProUser'];
                const header = {
                    kid: 'kid123',
                };
                const payload = {
                    sub: 'sub123',
                    email: 'morenito19@example.com',
                    'custom:role': roles,
                };
                class UselessTokenVerifier extends role_based_token_verifier_1.RoleBasedTokenVerifier {
                    constructor() {
                        super();
                    }
                    // eslint-disable-next-line @typescript-eslint/no-unused-vars
                    verify(_token) {
                        return Promise.resolve({ header, payload });
                    }
                }
                const verifier = new UselessTokenVerifier();
                const decodedToken = await verifier.verify('123');
                const userEnvelope = verifier.toUserEnvelope(decodedToken);
                (0, expect_1.expect)(userEnvelope).to.deep.equal({
                    id: 'sub123',
                    username: 'morenito19@example.com',
                    roles,
                    claims: payload,
                    header,
                });
            });
        });
        context('when the roles claim is set', () => {
            it('can build a `UserEnvelope` from a decoded token taking the roles from the roles claim', async () => {
                const roles = ['ProUser'];
                const header = {
                    kid: 'kid123',
                };
                const payload = {
                    sub: 'sub123',
                    email: 'morenito19@example.com',
                    ekipaso: roles,
                };
                class UselessTokenVerifier extends role_based_token_verifier_1.RoleBasedTokenVerifier {
                    constructor() {
                        super('ekipaso');
                    }
                    // eslint-disable-next-line @typescript-eslint/no-unused-vars
                    verify(_token) {
                        return Promise.resolve({ header, payload });
                    }
                }
                const verifier = new UselessTokenVerifier();
                const decodedToken = await verifier.verify('123');
                const userEnvelope = verifier.toUserEnvelope(decodedToken);
                (0, expect_1.expect)(userEnvelope).to.deep.equal({
                    id: 'sub123',
                    username: 'morenito19@example.com',
                    roles,
                    claims: payload,
                    header,
                });
            });
        });
    });
});
