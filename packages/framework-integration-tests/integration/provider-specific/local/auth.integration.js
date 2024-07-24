"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const utils_1 = require("./utils");
const chai_1 = require("chai");
const cross_fetch_1 = require("cross-fetch");
const faker_1 = require("faker");
const utils_2 = require("./utils");
// FIXME: When JWT auth is merged
xdescribe('With the auth API', () => {
    describe('sign-up', () => {
        context('new user', () => {
            let userEmail;
            let userPassword;
            beforeEach(() => {
                userEmail = faker_1.internet.email();
                userPassword = faker_1.internet.password();
            });
            it('should successfully register a new user', async () => {
                const response = await (0, cross_fetch_1.default)((0, utils_1.signUpURL)(), {
                    method: 'POST',
                    body: JSON.stringify({
                        username: userEmail,
                        password: userPassword,
                        userAttributes: {
                            role: '',
                        },
                    }),
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                const message = await response.json();
                (0, chai_1.expect)(message).to.be.empty;
                (0, chai_1.expect)(response.status).to.equal(200);
            });
        });
        context('repeated user', () => {
            let userEmail;
            let userPassword;
            beforeEach(() => {
                userEmail = faker_1.internet.email();
                userPassword = faker_1.internet.password();
            });
            it('should return a 401 error, username already registered', async () => {
                // First user registration
                let response = await (0, cross_fetch_1.default)((0, utils_1.signUpURL)(), {
                    method: 'POST',
                    body: JSON.stringify({
                        username: userEmail,
                        password: userPassword,
                        userAttributes: {
                            role: '',
                        },
                    }),
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                let message = await response.json();
                (0, chai_1.expect)(message).to.be.empty;
                (0, chai_1.expect)(response.status).to.equal(200);
                // repeated user registration
                response = await (0, cross_fetch_1.default)((0, utils_1.signUpURL)(), {
                    method: 'POST',
                    body: JSON.stringify({
                        username: userEmail,
                        password: userPassword,
                        userAttributes: {
                            role: '',
                        },
                    }),
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                message = await response.json();
                (0, chai_1.expect)(message).not.to.be.empty;
                (0, chai_1.expect)(message).to.be.deep.equal({
                    title: 'Not Authorized Error',
                    reason: `User with username ${userEmail} is already registered`,
                });
                (0, chai_1.expect)(response.status).to.equal(401);
            });
        });
    });
    describe('confirm user', () => {
        let userEmail;
        let userPassword;
        beforeEach(() => {
            userEmail = faker_1.internet.email();
            userPassword = faker_1.internet.password();
        });
        context('registered username', () => {
            it('should successfully confirm user', async () => {
                await (0, utils_1.createUser)(userEmail, userPassword);
                const response = await (0, cross_fetch_1.default)((0, utils_1.confirmUserURL)(userEmail), {
                    method: 'GET',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                const message = await response.json();
                (0, chai_1.expect)(message).to.be.equal('User confirmed!');
                (0, chai_1.expect)(response.status).to.equal(200);
            });
        });
        context('not registered username', () => {
            let notRegisteredUserEmail;
            beforeEach(() => {
                notRegisteredUserEmail = faker_1.internet.email();
            });
            it('should return a 404 error with expected message', async () => {
                const response = await (0, cross_fetch_1.default)((0, utils_1.confirmUserURL)(notRegisteredUserEmail), {
                    method: 'GET',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                const message = await response.json();
                (0, chai_1.expect)(message).to.be.deep.equal({
                    title: 'Not Found Error',
                    reason: `Incorrect username ${notRegisteredUserEmail}`,
                });
                (0, chai_1.expect)(response.status).to.equal(404);
            });
        });
    });
    describe('sign-in', () => {
        context('invalid username', () => {
            let userEmail;
            let userPassword;
            beforeEach(() => {
                userEmail = faker_1.internet.email();
                userPassword = faker_1.internet.password();
            });
            it('should return a 401 error with expected message', async () => {
                const response = await (0, cross_fetch_1.default)((0, utils_2.signInURL)(), {
                    method: 'POST',
                    body: JSON.stringify({
                        username: userEmail,
                        password: userPassword,
                    }),
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                const message = await response.json();
                (0, chai_1.expect)(message).to.be.deep.equal({
                    title: 'Not Authorized Error',
                    reason: 'Incorrect username or password',
                });
                (0, chai_1.expect)(response.status).to.equal(401);
            });
        });
        context('not confirmed username', () => {
            let userEmail;
            let userPassword;
            beforeEach(async () => {
                userEmail = faker_1.internet.email();
                userPassword = faker_1.internet.password();
                await (0, utils_1.createUser)(userEmail, userPassword);
            });
            it('should return a 401 error with expected message', async () => {
                const response = await (0, cross_fetch_1.default)((0, utils_2.signInURL)(), {
                    method: 'POST',
                    body: JSON.stringify({
                        username: userEmail,
                        password: userPassword,
                    }),
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                const message = await response.json();
                (0, chai_1.expect)(message).to.be.deep.equal({
                    title: 'Not Authorized Error',
                    reason: `User with username ${userEmail} has not been confirmed`,
                });
                (0, chai_1.expect)(response.status).to.equal(401);
            });
        });
        context('valid confirmed username', () => {
            let userEmail;
            let userPassword;
            beforeEach(async () => {
                userEmail = faker_1.internet.email();
                userPassword = faker_1.internet.password();
                await (0, utils_1.createUser)(userEmail, userPassword);
                await (0, utils_1.confirmUser)(userEmail);
            });
            it('should sign-in successfully', async () => {
                const response = await (0, cross_fetch_1.default)((0, utils_2.signInURL)(), {
                    method: 'POST',
                    body: JSON.stringify({
                        username: userEmail,
                        password: userPassword,
                    }),
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                const message = await response.json();
                (0, chai_1.expect)(message).not.to.be.empty;
                (0, chai_1.expect)(response.status).to.equal(200);
            });
        });
    });
    describe('sign-out', () => {
        context('missing token', () => {
            it('should return a 400 error with expected message', async () => {
                const response = await (0, cross_fetch_1.default)((0, utils_1.signOutURL)(), {
                    method: 'POST',
                    body: JSON.stringify({}),
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                const message = await response.json();
                (0, chai_1.expect)(message).to.be.equal('accessToken field not set');
                (0, chai_1.expect)(response.status).to.equal(400);
            });
        });
        context('token provided', () => {
            it('should successfully sign-out', async () => {
                const response = await (0, cross_fetch_1.default)((0, utils_1.signOutURL)(), {
                    method: 'POST',
                    body: JSON.stringify({
                        accessToken: faker_1.random.uuid(),
                    }),
                    headers: {
                        'Content-Type': 'application/json',
                    },
                });
                const message = await response.json();
                (0, chai_1.expect)(message).to.be.equal('');
                (0, chai_1.expect)(response.status).to.equal(200);
            });
        });
    });
});
