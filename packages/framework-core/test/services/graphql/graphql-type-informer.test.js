"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const graphql_type_informer_1 = require("../../../src/services/graphql/graphql-type-informer");
const expect_1 = require("../../expect");
const graphql_1 = require("graphql");
const graphql_scalars_1 = require("graphql-scalars");
const faker_1 = require("faker");
const common_1 = require("../../../src/services/graphql/common");
describe('GraphQLTypeInformer', () => {
    const nonExposed = ['ignoredParameter'];
    let sut;
    const logger = {
        debug() { },
        info() { },
        error() { },
        warn() { },
    };
    beforeEach(() => {
        sut = new graphql_type_informer_1.GraphQLTypeInformer(logger);
    });
    describe('generateGraphQLTypeForClass', () => {
        class TestClass {
            constructor(someProperty, someParameters, otherParameter, somePromiseParameter, somePromiseParameter2, somePromiseParameter3, ignoredParameter) {
                this.someProperty = someProperty;
                this.someParameters = someParameters;
                this.otherParameter = otherParameter;
                this.somePromiseParameter = somePromiseParameter;
                this.somePromiseParameter2 = somePromiseParameter2;
                this.somePromiseParameter3 = somePromiseParameter3;
            }
        }
        it('should return expected GraphQL Type', () => {
            const result = sut.generateGraphQLTypeForClass(TestClass, nonExposed);
            (0, expect_1.expect)(result.toString()).to.be.deep.equal('TestClass');
        });
        it('should process complex ReadonlyArray', () => {
            const result = sut.generateGraphQLTypeForClass(TestClass, nonExposed);
            const someParametersValue = result instanceof graphql_1.GraphQLObjectType ? result.getFields()['someParameters'].type : undefined;
            const otherParameterValue = result instanceof graphql_1.GraphQLObjectType ? result.getFields()['otherParameter'].type : undefined;
            (0, expect_1.expect)(someParametersValue).to.be.deep.equal(new graphql_1.GraphQLNonNull(new graphql_1.GraphQLList(new graphql_1.GraphQLNonNull(new graphql_1.GraphQLList(new graphql_1.GraphQLNonNull(graphql_scalars_1.GraphQLJSON))))));
            (0, expect_1.expect)(otherParameterValue).to.be.deep.equal(new graphql_1.GraphQLNonNull(new graphql_1.GraphQLList(new graphql_1.GraphQLNonNull(graphql_scalars_1.GraphQLJSON))));
        });
        it('should process Promises', () => {
            const result = sut.generateGraphQLTypeForClass(TestClass, nonExposed);
            const somePromiseParameter = result instanceof graphql_1.GraphQLObjectType ? result.getFields()['somePromiseParameter'].type : undefined;
            (0, expect_1.expect)(somePromiseParameter).to.be.deep.equal(new graphql_1.GraphQLNonNull(graphql_1.GraphQLString));
            const somePromiseParameter2 = result instanceof graphql_1.GraphQLObjectType ? result.getFields()['somePromiseParameter2'].type : undefined;
            (0, expect_1.expect)(somePromiseParameter2).to.be.deep.equal(new graphql_1.GraphQLNonNull(graphql_1.GraphQLFloat));
            const somePromiseParameter3 = result instanceof graphql_1.GraphQLObjectType ? result.getFields()['somePromiseParameter3'].type : undefined;
            (0, expect_1.expect)(somePromiseParameter3).to.be.deep.equal(graphql_1.GraphQLFloat);
        });
        it('should ignore nonExposed', () => {
            const result = sut.generateGraphQLTypeForClass(TestClass, nonExposed);
            const somePromiseParameter = result instanceof graphql_1.GraphQLObjectType ? result.getFields()['ignoredParameter'] : undefined;
            (0, expect_1.expect)(somePromiseParameter).to.be.undefined;
        });
        describe('Get or create GraphQLType', () => {
            beforeEach(() => {
                sut = new graphql_type_informer_1.GraphQLTypeInformer(logger);
            });
            it('should return GraphQLID!', () => {
                const result = sut.getOrCreateGraphQLType({
                    name: 'UUID', // UUID and Date types are by name
                });
                (0, expect_1.expect)(result).to.be.deep.equal(new graphql_1.GraphQLNonNull(graphql_1.GraphQLID));
            });
            it('should return Date!', () => {
                const result = sut.getOrCreateGraphQLType({
                    name: 'Date', // UUID and Date types are by name
                });
                (0, expect_1.expect)(result).to.be.deep.equal(new graphql_1.GraphQLNonNull(common_1.DateScalar));
            });
            it('should return GraphQLString', () => {
                const result = sut.getOrCreateGraphQLType({
                    name: 'string',
                    typeGroup: 'String', // by typeGroup
                });
                (0, expect_1.expect)(result).to.be.deep.equal(new graphql_1.GraphQLNonNull(graphql_1.GraphQLString));
            });
            it('should return GraphQLFloat', () => {
                const result = sut.getOrCreateGraphQLType({
                    name: 'number',
                    typeGroup: 'Number', // by typeGroup
                });
                (0, expect_1.expect)(result).to.be.deep.equal(new graphql_1.GraphQLNonNull(graphql_1.GraphQLFloat));
            });
            it('should return GraphQLBoolean', () => {
                const result = sut.getOrCreateGraphQLType({
                    name: 'boolean',
                    typeGroup: 'Boolean', // by typeGroup
                });
                (0, expect_1.expect)(result).to.be.deep.equal(new graphql_1.GraphQLNonNull(graphql_1.GraphQLBoolean));
            });
            it('should return GraphQLEnumType', () => {
                const result = sut.getOrCreateGraphQLType({
                    name: 'enum',
                    typeGroup: 'Enum',
                    parameters: [
                        {
                            name: 'key',
                            typeGroup: 'Boolean',
                            parameters: [],
                            isNullable: false,
                            isGetAccessor: false,
                        },
                    ],
                    isNullable: false,
                    isGetAccessor: false,
                });
                const expectedResult = new graphql_1.GraphQLNonNull(new graphql_1.GraphQLEnumType({
                    name: 'enum',
                    values: {
                        key: {
                            value: 'key',
                        },
                    },
                }));
                (0, expect_1.expect)(result).to.be.deep.equal(expectedResult);
            });
            it('should return GraphQLList of GraphQLBoolean', () => {
                const result = sut.getOrCreateGraphQLType({
                    name: 'MyArray[]',
                    typeGroup: 'Array',
                    parameters: [
                        {
                            name: 'boolean',
                            typeGroup: 'Boolean',
                            parameters: [],
                            isNullable: false,
                            isGetAccessor: false,
                        },
                    ],
                    isNullable: false,
                    isGetAccessor: false,
                });
                (0, expect_1.expect)(result).to.be.deep.equal(new graphql_1.GraphQLNonNull(new graphql_1.GraphQLList(new graphql_1.GraphQLNonNull(graphql_1.GraphQLBoolean))));
            });
            it('should return GraphQLJSON', () => {
                const result = sut.getOrCreateGraphQLType({
                    name: 'MyObject',
                    typeGroup: 'Object',
                });
                (0, expect_1.expect)(result).to.be.deep.equal(new graphql_1.GraphQLNonNull(graphql_scalars_1.GraphQLJSON));
            });
            describe('default', () => {
                let mockType;
                beforeEach(() => {
                    mockType = faker_1.random.arrayElement(['Float32Array', 'Float32Array', 'Uint8Array', 'Promise']);
                });
                it('should return GraphQLJSON', () => {
                    const result = sut.getOrCreateGraphQLType({
                        name: `MyObject${mockType}`,
                        typeGroup: mockType,
                        parameters: [],
                        isNullable: false,
                        isGetAccessor: false,
                    });
                    (0, expect_1.expect)(result).to.be.deep.equal(new graphql_1.GraphQLNonNull(graphql_scalars_1.GraphQLJSON));
                });
            });
        });
    });
});
