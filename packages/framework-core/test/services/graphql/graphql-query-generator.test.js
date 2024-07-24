"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/ban-ts-comment */
const graphql_query_generator_1 = require("../../../src/services/graphql/graphql-query-generator");
const sinon_1 = require("sinon");
const expect_1 = require("../../expect");
const graphql_type_informer_1 = require("../../../src/services/graphql/graphql-type-informer");
const sinon = require("sinon");
const graphql_1 = require("graphql");
const faker_1 = require("faker");
const graphql_scalars_1 = require("graphql-scalars");
const framework_types_1 = require("@boostercloud/framework-types");
const metadata = require("../../../src/decorators/metadata");
describe('GraphQLQueryGenerator', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('the `generate` public method', () => {
        const simpleConfig = new framework_types_1.BoosterConfig('test');
        context('black box tests', () => {
            let mockGraphQLType;
            let mockTypeInformer;
            let mockByIdResolverBuilder;
            let mockQueryResolverBuilder;
            let mockFilterResolverBuilder;
            let mockEventsResolver;
            const queryHandlers = {};
            let getGraphQLTypeForStub;
            beforeEach(() => {
                mockGraphQLType = faker_1.random.arrayElement([
                    graphql_1.GraphQLBoolean,
                    graphql_1.GraphQLID,
                    graphql_1.GraphQLString,
                    graphql_1.GraphQLFloat,
                    graphql_1.GraphQLInt,
                    graphql_scalars_1.GraphQLJSON,
                ]);
                mockTypeInformer = sinon.createStubInstance(graphql_type_informer_1.GraphQLTypeInformer);
                mockByIdResolverBuilder = (0, sinon_1.stub)();
                mockQueryResolverBuilder = (0, sinon_1.stub)();
                mockFilterResolverBuilder = (0, sinon_1.stub)();
                mockEventsResolver = (0, sinon_1.stub)();
                getGraphQLTypeForStub = (0, sinon_1.stub)().returns(mockGraphQLType);
                (0, sinon_1.replace)(mockTypeInformer, 'getOrCreateGraphQLType', getGraphQLTypeForStub);
                (0, sinon_1.replace)(mockTypeInformer, 'generateGraphQLTypeForClass', getGraphQLTypeForStub);
            });
            context('with target types', () => {
                context('1 target type', () => {
                    let mockTargetTypeClass;
                    let mockTargetTypeName;
                    let graphQLQueryGenerator;
                    let getClassMetadataStub;
                    beforeEach(() => {
                        mockTargetTypeClass = faker_1.random.arrayElement([Boolean, String, Number, Array]);
                        mockTargetTypeName = mockTargetTypeClass.name;
                        const mockedClassMetadata = {
                            name: mockTargetTypeClass.name,
                            type: mockTargetTypeClass,
                            fields: [],
                            methods: [],
                        };
                        graphQLQueryGenerator = new graphql_query_generator_1.GraphQLQueryGenerator(simpleConfig, [mockTargetTypeClass], queryHandlers, mockTypeInformer, mockByIdResolverBuilder, mockQueryResolverBuilder, mockFilterResolverBuilder, mockEventsResolver);
                        getClassMetadataStub = sinon
                            .stub(metadata, 'getClassMetadata')
                            .withArgs(mockTargetTypeClass)
                            .returns(mockedClassMetadata);
                    });
                    it('should call typeInformer.getGraphQLTypeFor thrice', () => {
                        graphQLQueryGenerator.generate();
                        (0, expect_1.expect)(getGraphQLTypeForStub).calledThrice.and.calledWith(mockTargetTypeClass);
                    });
                    it('should call filterResolverBuilder once with expected argument', () => {
                        graphQLQueryGenerator.generate();
                        (0, expect_1.expect)(mockByIdResolverBuilder).calledOnce.and.calledWith(mockTargetTypeClass);
                        // @ts-ignore
                        (0, expect_1.expect)(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub);
                    });
                    it('should return expected result', () => {
                        const result = graphQLQueryGenerator.generate();
                        (0, expect_1.expect)(result.name).to.be.equal('Query');
                        (0, expect_1.expect)(result.description).to.be.undefined;
                        (0, expect_1.expect)(result.extensions).to.be.empty;
                        (0, expect_1.expect)(result.astNode).to.be.undefined;
                        (0, expect_1.expect)(result.extensionASTNodes).to.be.empty;
                        const config = result.toConfig();
                        (0, expect_1.expect)(config.fields[mockTargetTypeName].description).to.be.undefined;
                        (0, expect_1.expect)(config.fields[mockTargetTypeName].type).to.be.equal(mockGraphQLType);
                        (0, expect_1.expect)(config.fields[mockTargetTypeName].resolve).to.be.undefined;
                        (0, expect_1.expect)(config.fields[mockTargetTypeName].subscribe).to.be.undefined;
                        (0, expect_1.expect)(config.fields[mockTargetTypeName].deprecationReason).to.be.undefined;
                        (0, expect_1.expect)(config.fields[mockTargetTypeName].extensions).to.be.empty;
                        (0, expect_1.expect)(config.fields[mockTargetTypeName].astNode).to.be.undefined;
                        (0, expect_1.expect)(config.fields[`${mockTargetTypeName}s`].description).to.be.undefined;
                        (0, expect_1.expect)(config.fields[`${mockTargetTypeName}s`].type.toString()).to.be.equal(`[${mockGraphQLType}!]!`);
                        (0, expect_1.expect)(config.fields[`${mockTargetTypeName}s`].resolve).to.be.undefined;
                        (0, expect_1.expect)(config.fields[`${mockTargetTypeName}s`].subscribe).to.be.undefined;
                        (0, expect_1.expect)(config.fields[`${mockTargetTypeName}s`].deprecationReason).to.be.equal('Method is deprecated. Use List* methods');
                        (0, expect_1.expect)(config.fields[`${mockTargetTypeName}s`].extensions).to.be.empty;
                        (0, expect_1.expect)(config.fields[`${mockTargetTypeName}s`].astNode).to.be.undefined;
                    });
                    describe('Array with properties', () => {
                        let mockPropertyName;
                        let mockTargetType;
                        let mockPropertyType;
                        beforeEach(() => {
                            // Provision target types
                            mockPropertyName = '_a' + faker_1.random.alphaNumeric(10);
                            mockTargetType = Array;
                            mockTargetTypeName = mockTargetType.name;
                            mockPropertyType = Boolean;
                            const mockedClassMetadata = {
                                name: mockTargetTypeName,
                                type: mockTargetType,
                                fields: [
                                    {
                                        name: mockPropertyName,
                                        typeInfo: {
                                            name: mockPropertyType.name,
                                            typeGroup: 'Boolean',
                                            type: mockPropertyType,
                                            typeName: 'Boolean',
                                            parameters: [],
                                            isNullable: false,
                                            isGetAccessor: false,
                                        },
                                    },
                                ],
                                methods: [
                                    {
                                        name: '',
                                        typeInfo: {
                                            name: mockPropertyType.name,
                                            typeGroup: 'String',
                                            type: mockPropertyType,
                                            parameters: [],
                                            typeName: 'String',
                                            isNullable: false,
                                            isGetAccessor: false,
                                        },
                                    },
                                ],
                            };
                            getClassMetadataStub.withArgs(mockTargetType).returns(mockedClassMetadata);
                        });
                        context('Property GraphQL Type is scalar', () => {
                            beforeEach(() => {
                                graphQLQueryGenerator = new graphql_query_generator_1.GraphQLQueryGenerator(simpleConfig, [mockTargetType], queryHandlers, mockTypeInformer, mockByIdResolverBuilder, mockQueryResolverBuilder, mockFilterResolverBuilder, mockEventsResolver);
                            });
                            it('should call typeInformer.getGraphQLTypeFor 3 times', () => {
                                graphQLQueryGenerator.generate();
                                (0, expect_1.expect)(getGraphQLTypeForStub).callCount(3).and.calledWith(mockTargetType);
                            });
                            it('should call filterResolverBuilder once with expected arguments', () => {
                                graphQLQueryGenerator.generate();
                                (0, expect_1.expect)(mockByIdResolverBuilder).to.be.calledOnce.and.calledWith(mockTargetType);
                                // @ts-ignore
                                (0, expect_1.expect)(mockByIdResolverBuilder).to.be.calledAfter(getGraphQLTypeForStub);
                            });
                            context('should have expected args', () => {
                                it('When Boolean', () => {
                                    const result = graphQLQueryGenerator.generate();
                                    const config = result.toConfig();
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].type.toString()).to.be.equal('ID!');
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].astNode).to.be.undefined;
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].defaultValue).to.be.undefined;
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].description).to.be.undefined;
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].extensions).to.be.empty;
                                    const booleansTypeFilterConfig = config.fields[`${mockTargetTypeName}s`].args.filter.type.getFields()[mockPropertyName];
                                    (0, expect_1.expect)(booleansTypeFilterConfig.type.toString()).to.be.equal('BooleanPropertyFilter');
                                    const fieldsKeys = Object.keys(booleansTypeFilterConfig.type.getFields());
                                    (0, expect_1.expect)(fieldsKeys).to.be.deep.equal(['eq', 'ne', 'isDefined']);
                                    (0, expect_1.expect)(fieldsKeys.length).to.equal(3);
                                    fieldsKeys.forEach((fieldKey) => {
                                        (0, expect_1.expect)(booleansTypeFilterConfig.type.getFields()[fieldKey].description).to.be.undefined;
                                        (0, expect_1.expect)(booleansTypeFilterConfig.type.getFields()[fieldKey].type.toString()).to.be.equal('Boolean');
                                        (0, expect_1.expect)(booleansTypeFilterConfig.type.getFields()[fieldKey].defaultValue).to.be.undefined;
                                        (0, expect_1.expect)(booleansTypeFilterConfig.type.getFields()[fieldKey].extensions).to.be.empty;
                                        (0, expect_1.expect)(booleansTypeFilterConfig.type.getFields()[fieldKey].astNode).to.be.undefined;
                                    });
                                    const sortBy = config.fields[`List${mockTargetTypeName}s`].args.sortBy;
                                    (0, expect_1.expect)(sortBy.description).to.be.undefined;
                                    (0, expect_1.expect)(sortBy.defaultValue).to.be.undefined;
                                    (0, expect_1.expect)(sortBy.deprecationReason).to.be.undefined;
                                    (0, expect_1.expect)(sortBy.extensions).to.be.empty;
                                    (0, expect_1.expect)(sortBy.astNode).to.be.undefined;
                                    const booleansTypeSortConfig = sortBy.type.getFields()[mockPropertyName];
                                    (0, expect_1.expect)(booleansTypeSortConfig.type.toString()).to.not.be.undefined;
                                    (0, expect_1.expect)(sortBy.type.name.toString()).to.be.eq(`${mockTargetTypeName}SortBy`);
                                    const sortKeys = Object.keys(sortBy.type.getFields());
                                    sortKeys.forEach((fieldKey) => {
                                        (0, expect_1.expect)(sortBy.type.getFields()[fieldKey].type.toString()).to.be.equal('orderProperty');
                                        const values = sortBy.type
                                            .getFields()[fieldKey].type.getValues()
                                            .map((value) => value.name);
                                        (0, expect_1.expect)(values).to.be.eql(['ASC', 'DESC']);
                                    });
                                });
                                it('When Number', () => {
                                    mockPropertyName = '_a' + faker_1.random.alphaNumeric(10);
                                    mockTargetType = Array;
                                    mockTargetTypeName = mockTargetType.name;
                                    mockPropertyType = Number;
                                    const mockedClassMetadata = {
                                        name: mockTargetTypeName,
                                        type: mockTargetType,
                                        fields: [
                                            {
                                                name: mockPropertyName,
                                                typeInfo: {
                                                    name: mockPropertyType.name,
                                                    typeGroup: 'Number',
                                                    type: mockPropertyType,
                                                    typeName: 'Number',
                                                    parameters: [],
                                                    isNullable: false,
                                                    isGetAccessor: false,
                                                },
                                            },
                                        ],
                                        methods: [],
                                    };
                                    getClassMetadataStub.withArgs(mockTargetType).returns(mockedClassMetadata);
                                    graphQLQueryGenerator = new graphql_query_generator_1.GraphQLQueryGenerator(simpleConfig, [mockTargetType], queryHandlers, mockTypeInformer, mockByIdResolverBuilder, mockQueryResolverBuilder, mockFilterResolverBuilder, mockEventsResolver);
                                    const result = graphQLQueryGenerator.generate();
                                    const config = result.toConfig();
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].type.toString()).to.be.equal('ID!');
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].astNode).to.be.undefined;
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].defaultValue).to.be.undefined;
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].description).to.be.undefined;
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].extensions).to.be.empty;
                                    const TypeFilterConfig = config.fields[`${mockTargetTypeName}s`].args.filter.type.getFields()[mockPropertyName];
                                    (0, expect_1.expect)(TypeFilterConfig.type.toString()).to.be.equal('NumberPropertyFilter');
                                    const fieldsKeys = Object.keys(TypeFilterConfig.type.getFields());
                                    (0, expect_1.expect)(fieldsKeys).to.be.deep.equal(['eq', 'ne', 'lte', 'lt', 'gte', 'gt', 'in', 'isDefined']);
                                    (0, expect_1.expect)(fieldsKeys.length).to.equal(8);
                                    fieldsKeys.forEach((fieldKey) => {
                                        (0, expect_1.expect)(TypeFilterConfig.type.getFields()[fieldKey].description).to.be.undefined;
                                        let type;
                                        if (fieldKey === 'in') {
                                            type = '[Float]';
                                        }
                                        else if (fieldKey === 'isDefined') {
                                            type = 'Boolean';
                                        }
                                        else {
                                            type = 'Float';
                                        } // The in filter expects an array of the element
                                        (0, expect_1.expect)(TypeFilterConfig.type.getFields()[fieldKey].type.toString()).to.be.equal(type);
                                        (0, expect_1.expect)(TypeFilterConfig.type.getFields()[fieldKey].defaultValue).to.be.undefined;
                                        (0, expect_1.expect)(TypeFilterConfig.type.getFields()[fieldKey].extensions).to.be.empty;
                                        (0, expect_1.expect)(TypeFilterConfig.type.getFields()[fieldKey].astNode).to.be.undefined;
                                    });
                                    const sortBy = config.fields[`List${mockTargetTypeName}s`].args.sortBy;
                                    (0, expect_1.expect)(sortBy.description).to.be.undefined;
                                    (0, expect_1.expect)(sortBy.defaultValue).to.be.undefined;
                                    (0, expect_1.expect)(sortBy.deprecationReason).to.be.undefined;
                                    (0, expect_1.expect)(sortBy.extensions).to.be.empty;
                                    (0, expect_1.expect)(sortBy.astNode).to.be.undefined;
                                    const booleansTypeSortConfig = sortBy.type.getFields()[mockPropertyName];
                                    (0, expect_1.expect)(booleansTypeSortConfig.type.toString()).to.not.be.undefined;
                                    (0, expect_1.expect)(sortBy.type.name.toString()).to.be.eq(`${mockTargetTypeName}SortBy`);
                                    const sortKeys = Object.keys(sortBy.type.getFields());
                                    sortKeys.forEach((fieldKey) => {
                                        (0, expect_1.expect)(sortBy.type.getFields()[fieldKey].type.toString()).to.be.equal('orderProperty');
                                        const values = sortBy.type
                                            .getFields()[fieldKey].type.getValues()
                                            .map((value) => value.name);
                                        (0, expect_1.expect)(values).to.be.eql(['ASC', 'DESC']);
                                    });
                                });
                                it('When String', () => {
                                    mockPropertyName = '_a' + faker_1.random.alphaNumeric(10);
                                    mockTargetType = Array;
                                    mockTargetTypeName = mockTargetType.name;
                                    mockPropertyType = String;
                                    const mockedClassMetadata = {
                                        name: mockTargetTypeName,
                                        type: mockTargetType,
                                        fields: [
                                            {
                                                name: mockPropertyName,
                                                typeInfo: {
                                                    name: mockPropertyType.name,
                                                    typeGroup: 'String',
                                                    type: mockPropertyType,
                                                    typeName: 'String',
                                                    parameters: [],
                                                    isNullable: false,
                                                    isGetAccessor: false,
                                                },
                                            },
                                        ],
                                        methods: [],
                                    };
                                    getClassMetadataStub.withArgs(mockTargetType).returns(mockedClassMetadata);
                                    graphQLQueryGenerator = new graphql_query_generator_1.GraphQLQueryGenerator(simpleConfig, [mockTargetType], queryHandlers, mockTypeInformer, mockByIdResolverBuilder, mockQueryResolverBuilder, mockFilterResolverBuilder, mockEventsResolver);
                                    const result = graphQLQueryGenerator.generate();
                                    const config = result.toConfig();
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].type.toString()).to.be.equal('ID!');
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].astNode).to.be.undefined;
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].defaultValue).to.be.undefined;
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].description).to.be.undefined;
                                    (0, expect_1.expect)(config.fields[mockTargetTypeName].args['id'].extensions).to.be.empty;
                                    const TypeFilterConfig = config.fields[`${mockTargetTypeName}s`].args.filter.type.getFields()[mockPropertyName];
                                    (0, expect_1.expect)(TypeFilterConfig.type.toString()).to.be.equal('StringPropertyFilter');
                                    const fieldsKeys = Object.keys(TypeFilterConfig.type.getFields());
                                    (0, expect_1.expect)(fieldsKeys).to.be.deep.equal([
                                        'eq',
                                        'ne',
                                        'lte',
                                        'lt',
                                        'gte',
                                        'gt',
                                        'in',
                                        'beginsWith',
                                        'contains',
                                        'regex',
                                        'iRegex',
                                        'isDefined',
                                    ]);
                                    (0, expect_1.expect)(fieldsKeys.length).to.equal(12);
                                    fieldsKeys.forEach((fieldKey) => {
                                        (0, expect_1.expect)(TypeFilterConfig.type.getFields()[fieldKey].description).to.be.undefined;
                                        let type;
                                        if (fieldKey === 'in') {
                                            type = '[String]';
                                        }
                                        else if (fieldKey === 'isDefined') {
                                            type = 'Boolean';
                                        }
                                        else {
                                            type = 'String';
                                        } // The in filter expects an array of the element
                                        (0, expect_1.expect)(TypeFilterConfig.type.getFields()[fieldKey].type.toString()).to.be.equal(type);
                                        (0, expect_1.expect)(TypeFilterConfig.type.getFields()[fieldKey].defaultValue).to.be.undefined;
                                        (0, expect_1.expect)(TypeFilterConfig.type.getFields()[fieldKey].extensions).to.be.empty;
                                        (0, expect_1.expect)(TypeFilterConfig.type.getFields()[fieldKey].astNode).to.be.undefined;
                                    });
                                    const sortBy = config.fields[`List${mockTargetTypeName}s`].args.sortBy;
                                    (0, expect_1.expect)(sortBy.description).to.be.undefined;
                                    (0, expect_1.expect)(sortBy.defaultValue).to.be.undefined;
                                    (0, expect_1.expect)(sortBy.deprecationReason).to.be.undefined;
                                    (0, expect_1.expect)(sortBy.extensions).to.be.empty;
                                    (0, expect_1.expect)(sortBy.astNode).to.be.undefined;
                                    const booleansTypeSortConfig = sortBy.type.getFields()[mockPropertyName];
                                    (0, expect_1.expect)(booleansTypeSortConfig.type.toString()).to.not.be.undefined;
                                    (0, expect_1.expect)(sortBy.type.name.toString()).to.be.eq(`${mockTargetTypeName}SortBy`);
                                    const sortKeys = Object.keys(sortBy.type.getFields());
                                    sortKeys.forEach((fieldKey) => {
                                        (0, expect_1.expect)(sortBy.type.getFields()[fieldKey].type.toString()).to.be.equal('orderProperty');
                                        const values = sortBy.type
                                            .getFields()[fieldKey].type.getValues()
                                            .map((value) => value.name);
                                        (0, expect_1.expect)(values).to.be.eql(['ASC', 'DESC']);
                                    });
                                });
                            });
                        });
                    });
                });
            });
            context('with a config with events and entities', () => {
                let graphQLQueryGenerator;
                const entityNames = ['TestEntity1', 'TestEntity2', 'TestEntity3'];
                const eventTypeNames = ['EventType1', 'EventType2', 'EventType3'];
                let mockTargetTypeClass;
                beforeEach(() => {
                    const configWithEventsAndEntities = new framework_types_1.BoosterConfig('test');
                    for (const entityName of entityNames) {
                        configWithEventsAndEntities.entities[entityName] = {};
                    }
                    for (const eventTypeName of eventTypeNames) {
                        configWithEventsAndEntities.reducers[eventTypeName] = {};
                    }
                    mockTargetTypeClass = Boolean;
                    const mockedClassMetadata = {
                        name: mockTargetTypeClass.name,
                        type: mockTargetTypeClass,
                        fields: [],
                        methods: [],
                    };
                    sinon
                        .stub(metadata, 'getClassMetadata')
                        .withArgs(mockTargetTypeClass)
                        .returns(mockedClassMetadata);
                    graphQLQueryGenerator = new graphql_query_generator_1.GraphQLQueryGenerator(configWithEventsAndEntities, [mockTargetTypeClass], queryHandlers, mockTypeInformer, mockByIdResolverBuilder, mockQueryResolverBuilder, mockFilterResolverBuilder, mockEventsResolver);
                });
                it('should return expected result', () => {
                    const result = graphQLQueryGenerator.generate().toConfig();
                    (0, expect_1.expect)(result.name).to.be.equal('Query');
                    (0, expect_1.expect)(new Set(Object.keys(result.fields))).to.be.deep.equal(new Set(['Boolean', 'Booleans', 'ListBooleans', 'eventsByEntity', 'eventsByType']));
                    const eventsByEntityField = result.fields['eventsByEntity'];
                    (0, expect_1.expect)(new Set(Object.keys(eventsByEntityField.args))).to.be.deep.equal(new Set(['entity', 'entityID', 'from', 'to', 'limit']));
                    const entityEnumType = eventsByEntityField.args['entity'].type.ofType;
                    (0, expect_1.expect)(new Set(entityEnumType.getValues().map((v) => v.value))).to.be.deep.equal(new Set(entityNames));
                    assertEventSearchQueryReturnType(eventsByEntityField.type);
                    const eventsByType = result.fields['eventsByType'];
                    (0, expect_1.expect)(new Set(Object.keys(eventsByType.args))).to.be.deep.equal(new Set(['type', 'from', 'to', 'limit']));
                    const eventTypeEnumType = eventsByType.args['type'].type.ofType;
                    (0, expect_1.expect)(new Set(eventTypeEnumType.getValues().map((v) => v.value))).to.be.deep.equal(new Set(eventTypeNames));
                    assertEventSearchQueryReturnType(eventsByType.type);
                });
                function assertEventSearchQueryReturnType(queryReturnType) {
                    (0, expect_1.expect)(queryReturnType).to.be.instanceOf(graphql_1.GraphQLList);
                    const returnElementType = queryReturnType.ofType;
                    (0, expect_1.expect)(returnElementType.name).to.be.equal('EventQueryResponse');
                    (0, expect_1.expect)(new Set(Object.keys(returnElementType.getFields()))).to.be.deep.equal(new Set(['type', 'entity', 'requestID', 'entityID', 'user', 'createdAt', 'value']));
                    const userType = returnElementType.getFields()['user'].type;
                    (0, expect_1.expect)(new Set(Object.keys(userType.getFields()))).to.be.deep.equal(new Set(['id', 'username', 'roles']));
                }
            });
            context('without target types', () => {
                let graphQLQueryGenerator;
                beforeEach(() => {
                    graphQLQueryGenerator = new graphql_query_generator_1.GraphQLQueryGenerator(simpleConfig, [], queryHandlers, mockTypeInformer, mockByIdResolverBuilder, mockQueryResolverBuilder, mockFilterResolverBuilder, mockEventsResolver);
                });
                it('should not call typeInformer.getGraphQLTypeFor', () => {
                    graphQLQueryGenerator.generate();
                    (0, expect_1.expect)(getGraphQLTypeForStub).to.not.be.called;
                });
                it('should return only the event queries', () => {
                    const result = graphQLQueryGenerator.generate();
                    (0, expect_1.expect)(result.name).to.be.equal('Query');
                    const config = result.toConfig();
                    (0, expect_1.expect)(Object.keys(config.fields).length).to.eq(2);
                    (0, expect_1.expect)(config.fields).to.include.keys('eventsByEntity', 'eventsByType');
                });
            });
        });
    });
    describe('the `generateFilterQueries` private method', () => {
        it('generates a query named after the plural version of the read model name that responds with a list of read model instances'); // TODO
    });
    describe('the `generateListedQueries` private method', () => {
        it('generates a query named List<PluralizedNameOfReadModel> that responds with a paginated list of read model instances'); // TODO
    });
    describe('the `generateEventsQueries` private method', () => {
        it('generates event queries for specific entities'); // TODO
    });
});
