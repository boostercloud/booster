"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const tslib_1 = require("tslib");
/* eslint-disable @typescript-eslint/no-unused-vars */
const expect_1 = require("../expect");
const mocha_1 = require("mocha");
const src_1 = require("../../src");
const framework_types_1 = require("@boostercloud/framework-types");
const booster_authorizer_1 = require("../../src/booster-authorizer");
const sinon_1 = require("sinon");
(0, mocha_1.describe)('the `ReadModel` decorator', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
        src_1.Booster.configure('test', (config) => {
            for (const propName in config.readModels) {
                delete config.readModels[propName];
            }
        });
    });
    context('when the `authorize` parameter is not provided', () => {
        it('injects the read model metadata in the Booster configuration and denies access', () => {
            let Post = class Post {
                constructor(id, title) {
                    this.id = id;
                    this.title = title;
                }
            };
            Post = tslib_1.__decorate([
                (0, src_1.ReadModel)({})
            ], Post);
            (0, expect_1.expect)(src_1.Booster.config.readModels['Post']).to.deep.equal({
                class: Post,
                authorizer: booster_authorizer_1.BoosterAuthorizer.denyAccess,
                before: [],
                properties: [
                    {
                        name: 'id',
                        typeInfo: {
                            importPath: '@boostercloud/framework-types',
                            isNullable: false,
                            isGetAccessor: false,
                            name: 'UUID',
                            parameters: [],
                            type: framework_types_1.UUID,
                            typeGroup: 'Class',
                            typeName: 'UUID',
                        },
                    },
                    {
                        name: 'title',
                        typeInfo: {
                            isNullable: false,
                            isGetAccessor: false,
                            name: 'string',
                            parameters: [],
                            type: String,
                            typeGroup: 'String',
                            typeName: 'String',
                        },
                    },
                ],
            });
        });
    });
    context('when before filter functions are provided', () => {
        it('injects the read model metadata in the Booster configuration with the provided before functions', () => {
            const fakeBeforeFilter = sinon_1.fake.resolves(undefined);
            let Post = class Post {
                constructor(id, aStringProp) {
                    this.id = id;
                    this.aStringProp = aStringProp;
                }
            };
            Post = tslib_1.__decorate([
                (0, src_1.ReadModel)({
                    before: [fakeBeforeFilter],
                })
            ], Post);
            (0, expect_1.expect)(src_1.Booster.config.readModels['Post'].class).to.equal(Post);
            (0, expect_1.expect)(src_1.Booster.config.readModels['Post'].authorizer).to.be.equal(booster_authorizer_1.BoosterAuthorizer.denyAccess);
            (0, expect_1.expect)(src_1.Booster.config.readModels['Post'].before).to.be.an('Array');
            (0, expect_1.expect)(src_1.Booster.config.readModels['Post'].before).to.have.lengthOf(1);
            (0, expect_1.expect)(src_1.Booster.config.readModels['Post'].before[0]).to.be.equal(fakeBeforeFilter);
        });
    });
    context('when the `authorize` parameter is set to `all`', () => {
        it('registers the read model in Booster configuration and allows public access', () => {
            let SomeReadModel = class SomeReadModel {
                constructor(id, aStringProp, aNumberProp, aReadonlyArray) {
                    this.id = id;
                    this.aStringProp = aStringProp;
                    this.aNumberProp = aNumberProp;
                    this.aReadonlyArray = aReadonlyArray;
                }
            };
            SomeReadModel = tslib_1.__decorate([
                (0, src_1.ReadModel)({
                    authorize: 'all',
                })
            ], SomeReadModel);
            (0, expect_1.expect)(src_1.Booster.config.readModels['SomeReadModel']).to.be.deep.equal({
                class: SomeReadModel,
                authorizer: booster_authorizer_1.BoosterAuthorizer.allowAccess,
                before: [],
                properties: [
                    {
                        name: 'id',
                        typeInfo: {
                            importPath: '@boostercloud/framework-types',
                            isNullable: false,
                            isGetAccessor: false,
                            name: 'UUID',
                            parameters: [],
                            type: framework_types_1.UUID,
                            typeGroup: 'Class',
                            typeName: 'UUID',
                        },
                    },
                    {
                        name: 'aStringProp',
                        typeInfo: {
                            isNullable: false,
                            isGetAccessor: false,
                            name: 'string',
                            parameters: [],
                            type: String,
                            typeGroup: 'String',
                            typeName: 'String',
                        },
                    },
                    {
                        name: 'aNumberProp',
                        typeInfo: {
                            isNullable: false,
                            isGetAccessor: false,
                            name: 'number',
                            parameters: [],
                            type: Number,
                            typeGroup: 'Number',
                            typeName: 'Number',
                        },
                    },
                    {
                        name: 'aReadonlyArray',
                        typeInfo: {
                            isNullable: false,
                            isGetAccessor: false,
                            name: 'readonly string[]',
                            parameters: [
                                {
                                    isNullable: false,
                                    isGetAccessor: false,
                                    name: 'string',
                                    parameters: [],
                                    type: String,
                                    typeGroup: 'String',
                                    typeName: 'String',
                                },
                            ],
                            type: undefined,
                            typeGroup: 'ReadonlyArray',
                            typeName: 'ReadonlyArray',
                        },
                    },
                ],
            });
        });
    });
    context('when the `authorize` parameter is set to an array of roles', () => {
        it('registers the read model in Booster configuration and allows access to the specified roles', async () => {
            var _a;
            let Admin = class Admin {
            };
            Admin = tslib_1.__decorate([
                (0, src_1.Role)({
                    auth: {},
                })
            ], Admin);
            let SomeReadModel = class SomeReadModel {
                constructor(id, aStringProp) {
                    this.id = id;
                    this.aStringProp = aStringProp;
                }
            };
            SomeReadModel = tslib_1.__decorate([
                (0, src_1.ReadModel)({
                    authorize: [Admin],
                })
            ], SomeReadModel);
            (0, expect_1.expect)(src_1.Booster.config.readModels['SomeReadModel'].class).to.be.equal(SomeReadModel);
            const authorizerFunction = (_a = src_1.Booster.config.readModels['SomeReadModel']) === null || _a === void 0 ? void 0 : _a.authorizer;
            console.log('-----------------------------------');
            console.log(authorizerFunction);
            console.log('-----------------------------------');
            (0, expect_1.expect)(authorizerFunction).not.to.be.undefined;
            const fakeUser = {
                roles: ['User'],
            };
            await (0, expect_1.expect)(authorizerFunction(fakeUser)).not.to.be.eventually.fulfilled;
            const fakeAdmin = {
                roles: ['Admin'],
            };
            await (0, expect_1.expect)(authorizerFunction(fakeAdmin)).to.be.eventually.fulfilled;
        });
    });
    context('when the `authorize` parameter is set to a function', () => {
        it('registers the read model in Booster configuration and allows access when the authorizer function is fulfilled', async () => {
            let RockingData = class RockingData {
                constructor(id, aStringProp) {
                    this.id = id;
                    this.aStringProp = aStringProp;
                }
            };
            RockingData = tslib_1.__decorate([
                (0, src_1.ReadModel)({
                    authorize: async (currentUser) => {
                        var _a;
                        const permissions = (_a = currentUser === null || currentUser === void 0 ? void 0 : currentUser.claims) === null || _a === void 0 ? void 0 : _a.permissions;
                        if (permissions && permissions.includes('Rock')) {
                            return Promise.resolve();
                        }
                        return Promise.reject('This is not for you!');
                    },
                })
            ], RockingData);
            (0, expect_1.expect)(src_1.Booster.config.readModels['RockingData'].class).to.be.equal(RockingData);
            const fakeUser = {
                claims: {
                    permissions: ['Rock'],
                },
            };
            await (0, expect_1.expect)(src_1.Booster.config.readModels['RockingData'].authorizer(fakeUser)).to.be.eventually.fulfilled;
            const fakeUser2 = {
                claims: {
                    permissions: ['Reaggeton'],
                },
            };
            await (0, expect_1.expect)(src_1.Booster.config.readModels['RockingData'].authorizer(fakeUser2)).not.to.be.eventually.fulfilled;
        });
    });
});
(0, mocha_1.describe)('the `Projects` decorator', () => {
    afterEach(() => {
        src_1.Booster.configure('test', (config) => {
            for (const propName in config.readModels) {
                delete config.readModels[propName];
            }
            for (const propName in config.projections) {
                delete config.projections[propName];
            }
        });
    });
    it('registers a read model method as an entity projection in Booster configuration', () => {
        let SomeEntity = class SomeEntity {
            constructor(id) {
                this.id = id;
            }
        };
        SomeEntity = tslib_1.__decorate([
            src_1.Entity
        ], SomeEntity);
        let SomeReadModel = class SomeReadModel {
            constructor(id) {
                this.id = id;
            }
            static observeSomeEntity(entity) {
                throw new Error(`not implemented for ${entity}`);
            }
        };
        tslib_1.__decorate([
            (0, src_1.Projects)(SomeEntity, 'id')
        ], SomeReadModel, "observeSomeEntity", null);
        SomeReadModel = tslib_1.__decorate([
            (0, src_1.ReadModel)({
                authorize: 'all',
            })
        ], SomeReadModel);
        const someEntityObservers = src_1.Booster.config.projections['SomeEntity'];
        (0, expect_1.expect)(src_1.Booster.config.readModels).to.contain(SomeReadModel);
        (0, expect_1.expect)(someEntityObservers).to.be.an('Array');
        (0, expect_1.expect)(someEntityObservers).to.deep.include({
            class: SomeReadModel,
            methodName: 'observeSomeEntity',
            joinKey: 'id',
        });
    });
    (0, mocha_1.describe)('the `sequencedBy` decorator', () => {
        afterEach(() => {
            src_1.Booster.configure('test', (config) => {
                for (const propName in config.readModels) {
                    delete config.readModels[propName];
                }
                for (const propName in config.projections) {
                    delete config.projections[propName];
                }
            });
        });
        it('registers a sequence key in the read model', () => {
            let SequencedReadModel = class SequencedReadModel {
                constructor(id, timestamp) {
                    this.id = id;
                    this.timestamp = timestamp;
                }
            };
            SequencedReadModel = tslib_1.__decorate([
                (0, src_1.ReadModel)({
                    authorize: 'all',
                }),
                tslib_1.__param(1, src_1.sequencedBy)
            ], SequencedReadModel);
            (0, expect_1.expect)(src_1.Booster.config.readModelSequenceKeys).not.to.be.null;
            (0, expect_1.expect)(src_1.Booster.config.readModelSequenceKeys[SequencedReadModel.name]).to.be.a('String');
            (0, expect_1.expect)(src_1.Booster.config.readModelSequenceKeys[SequencedReadModel.name]).to.be.equal('timestamp');
        });
    });
});
