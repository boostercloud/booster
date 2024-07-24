"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
const expect_1 = require("../expect");
const decorators_1 = require("../../src/decorators");
const src_1 = require("../../src");
const sinon_1 = require("sinon");
const booster_authorizer_1 = require("../../src/booster-authorizer");
describe('the `Command` decorator', () => {
    afterEach(() => {
        const booster = src_1.Booster;
        delete booster.config.commandHandlers['PostComment'];
    });
    context('when an authorizer function is provided', () => {
        it('injects the command handler metadata in the Booster configuration with the provided authorizer function', () => {
            const fakeCommandAuthorizer = sinon_1.fake.resolves(undefined);
            let PostComment = class PostComment {
                constructor(comment) {
                    this.comment = comment;
                }
                // eslint-disable-next-line @typescript-eslint/no-unused-vars
                static async handle(_command, _register) {
                    throw new Error('Not implemented');
                }
            };
            PostComment = tslib_1.__decorate([
                (0, decorators_1.Command)({ authorize: fakeCommandAuthorizer })
            ], PostComment);
            // Make Booster be of any type to access private members
            const booster = src_1.Booster;
            const commandMetadata = booster.config.commandHandlers[PostComment.name];
            (0, expect_1.expect)(commandMetadata).to.be.an('object');
            (0, expect_1.expect)(commandMetadata.class).to.equal(PostComment);
            (0, expect_1.expect)(commandMetadata.properties[0].name).to.equal('comment');
            (0, expect_1.expect)(commandMetadata.properties[0].typeInfo.name).to.equal('string');
            (0, expect_1.expect)(commandMetadata.methods[0].name).to.equal('handle');
            (0, expect_1.expect)(commandMetadata.methods[0].typeInfo.name).to.equal('Promise<void>');
            (0, expect_1.expect)(commandMetadata.authorizer).to.equal(fakeCommandAuthorizer);
            (0, expect_1.expect)(commandMetadata.before).to.be.an('Array');
            (0, expect_1.expect)(commandMetadata.before).to.be.empty;
        });
    });
    context('when an authorizer function is not provided', () => {
        it('injects the command handler metadata in the Booster configuration and denies access', () => {
            let PostComment = class PostComment {
                constructor(comment) {
                    this.comment = comment;
                }
                // eslint-disable-next-line @typescript-eslint/no-unused-vars
                static async handle(_command, _register) {
                    throw new Error('Not implemented');
                }
            };
            PostComment = tslib_1.__decorate([
                (0, decorators_1.Command)({})
            ], PostComment);
            // Make Booster be of any type to access private members
            const booster = src_1.Booster;
            const commandMetadata = booster.config.commandHandlers[PostComment.name];
            (0, expect_1.expect)(commandMetadata).to.be.an('object');
            (0, expect_1.expect)(commandMetadata.class).to.equal(PostComment);
            (0, expect_1.expect)(commandMetadata.properties[0].name).to.equal('comment');
            (0, expect_1.expect)(commandMetadata.properties[0].typeInfo.name).to.equal('string');
            (0, expect_1.expect)(commandMetadata.methods[0].name).to.equal('handle');
            (0, expect_1.expect)(commandMetadata.methods[0].typeInfo.name).to.equal('Promise<void>');
            (0, expect_1.expect)(commandMetadata.authorizer).to.equal(booster_authorizer_1.BoosterAuthorizer.denyAccess);
            (0, expect_1.expect)(commandMetadata.before).to.be.an('Array');
            (0, expect_1.expect)(commandMetadata.before).to.be.empty;
        });
    });
    context('when a `before` hook is provided', () => {
        it('injects the command handler metadata in the Booster configuration with the provided before hook', () => {
            const fakeBeforeHook = sinon_1.fake.resolves(undefined);
            let PostComment = class PostComment {
                constructor(comment) {
                    this.comment = comment;
                }
                // eslint-disable-next-line @typescript-eslint/no-unused-vars
                static async handle(_command, _register) {
                    throw new Error('Not implemented');
                }
            };
            PostComment = tslib_1.__decorate([
                (0, decorators_1.Command)({ before: [fakeBeforeHook] })
            ], PostComment);
            // Make Booster be of any type to access private members
            const booster = src_1.Booster;
            const commandMetadata = booster.config.commandHandlers[PostComment.name];
            (0, expect_1.expect)(commandMetadata).to.be.an('object');
            (0, expect_1.expect)(commandMetadata.class).to.equal(PostComment);
            (0, expect_1.expect)(commandMetadata.properties[0].name).to.equal('comment');
            (0, expect_1.expect)(commandMetadata.properties[0].typeInfo.name).to.equal('string');
            (0, expect_1.expect)(commandMetadata.methods[0].name).to.equal('handle');
            (0, expect_1.expect)(commandMetadata.methods[0].typeInfo.name).to.equal('Promise<void>');
            (0, expect_1.expect)(commandMetadata.authorizer).to.equal(booster_authorizer_1.BoosterAuthorizer.denyAccess);
            (0, expect_1.expect)(commandMetadata.before).to.be.an('Array');
            (0, expect_1.expect)(commandMetadata.before).to.include(fakeBeforeHook);
        });
    });
});
