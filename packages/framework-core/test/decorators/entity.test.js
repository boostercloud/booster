"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const expect_1 = require("../expect");
const decorators_1 = require("../../src/decorators/");
const src_1 = require("../../src");
const booster_authorizer_1 = require("../../src/booster-authorizer");
const sinon_1 = require("sinon");
describe('the `Entity` decorator', () => {
    afterEach(() => {
        src_1.Booster.configure('test', (config) => {
            config.appName = '';
            for (const propName in config.reducers) {
                delete config.reducers[propName];
            }
            for (const propName in config.entities) {
                delete config.entities[propName];
            }
            for (const propName in config.roles) {
                delete config.roles[propName];
            }
        });
    });
    context('when no parameters are provided', () => {
        it('injects the entity metadata and sets up the reducers in the booster config denying event reads', () => {
            let CommentPosted = class CommentPosted {
                constructor(foo) {
                    this.foo = foo;
                }
                entityID() {
                    return '123';
                }
            };
            CommentPosted = tslib_1.__decorate([
                decorators_1.Event
            ], CommentPosted);
            let Comment = class Comment {
                constructor(id, content) {
                    this.id = id;
                    this.content = content;
                }
                static react(_event) {
                    throw new Error('Not implemented');
                }
            };
            tslib_1.__decorate([
                (0, decorators_1.Reduces)(CommentPosted)
            ], Comment, "react", null);
            Comment = tslib_1.__decorate([
                decorators_1.Entity
            ], Comment);
            (0, expect_1.expect)(src_1.Booster.config.entities['Comment'].class).to.be.equal(Comment);
            (0, expect_1.expect)(src_1.Booster.config.entities['Comment'].eventStreamAuthorizer).to.be.equal(booster_authorizer_1.BoosterAuthorizer.denyAccess);
            (0, expect_1.expect)(src_1.Booster.config.reducers['CommentPosted']).to.deep.include({
                class: Comment,
                methodName: 'react',
            });
        });
    });
    context("when `authorizeRoleAccess` is set to 'all'", () => {
        it('injects the entity metadata and sets up the reducers in the booster config allowing event reads', () => {
            let Comment = class Comment {
                constructor(id, content) {
                    this.id = id;
                    this.content = content;
                }
            };
            Comment = tslib_1.__decorate([
                (0, decorators_1.Entity)({
                    authorizeReadEvents: 'all',
                })
            ], Comment);
            (0, expect_1.expect)(src_1.Booster.config.entities['Comment']).to.deep.equal({
                class: Comment,
                eventStreamAuthorizer: booster_authorizer_1.BoosterAuthorizer.allowAccess,
            });
        });
    });
    context('when `authorizeRoleAccess` is set to an array of roles', () => {
        it('injects the entity metadata and sets up the reducers in the booster config allowing event reads to the specified roles', async () => {
            const fakeAuthorizeRoles = (0, sinon_1.fake)();
            (0, sinon_1.replace)(booster_authorizer_1.BoosterAuthorizer, 'authorizeRoles', fakeAuthorizeRoles);
            let Manager = class Manager {
            };
            Manager = tslib_1.__decorate([
                (0, decorators_1.Role)({
                    auth: {},
                })
            ], Manager);
            let User = class User {
                constructor(id, content) {
                    this.id = id;
                    this.content = content;
                }
            };
            User = tslib_1.__decorate([
                (0, decorators_1.Entity)({
                    authorizeReadEvents: [Manager],
                })
            ], User);
            (0, expect_1.expect)(src_1.Booster.config.entities['User'].class).to.be.equal(User);
            const fakeUserEnvelope = {
                username: 'asdf',
            };
            await src_1.Booster.config.entities['User'].eventStreamAuthorizer(fakeUserEnvelope);
            (0, expect_1.expect)(fakeAuthorizeRoles).to.have.been.calledWithMatch([Manager], fakeUserEnvelope);
        });
    });
    context('when `authorizeRoleAccess` is set to a function', () => {
        it('injects the entity metadata and sets up the reducers in the booster config allowing event reads to tokens that fulfill the authorizer function', async () => {
            let User = class User {
                constructor(id, content) {
                    this.id = id;
                    this.content = content;
                }
            };
            User = tslib_1.__decorate([
                (0, decorators_1.Entity)({
                    authorizeReadEvents: (currentUser) => {
                        if ((currentUser === null || currentUser === void 0 ? void 0 : currentUser.username) !== 'asdf')
                            return Promise.reject('Unauthorized');
                        return Promise.resolve();
                    },
                })
            ], User);
            (0, expect_1.expect)(src_1.Booster.config.entities['User'].class).to.be.equal(User);
            const fakeUserEnvelope = {
                username: 'asdf',
            };
            await (0, expect_1.expect)(src_1.Booster.config.entities['User'].eventStreamAuthorizer(fakeUserEnvelope)).to.be.fulfilled;
            const fakeUserEnvelope2 = {
                username: 'qwer',
            };
            await (0, expect_1.expect)(src_1.Booster.config.entities['User'].eventStreamAuthorizer(fakeUserEnvelope2)).to.be.rejected;
        });
    });
});
