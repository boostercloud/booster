"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/ban-ts-comment */
const sinon_1 = require("sinon");
const expect_1 = require("../../../expect");
const graphql_type_informer_1 = require("../../../../src/services/graphql/graphql-type-informer");
const framework_types_1 = require("@boostercloud/framework-types");
const graphql_query_by_keys_generator_1 = require("../../../../src/services/graphql/query-generators/graphql-query-by-keys-generator");
const framework_common_helpers_1 = require("@boostercloud/framework-common-helpers");
const faker_1 = require("faker");
class AnotherReadModel {
    constructor(id, otherField) {
        this.id = id;
        this.otherField = otherField;
    }
}
class ASequencedReadModel {
    constructor(id, timestamp) {
        this.id = id;
        this.timestamp = timestamp;
    }
}
class ARegularReadModel {
    constructor() {
        this.id = '∫';
    }
}
class AnotherSequencedReadModel {
    constructor() {
        this.id = 'µ';
        this.timestamp = '™';
    }
}
describe('GraphQLQueryGenerator', () => {
    let mockEnvironmentName;
    let mockConfig;
    let mockLogger;
    beforeEach(() => {
        mockEnvironmentName = faker_1.random.alphaNumeric(10);
        mockConfig = new framework_types_1.BoosterConfig(mockEnvironmentName);
        mockConfig.logLevel = framework_types_1.Level.error;
        mockLogger = (0, framework_common_helpers_1.getLogger)(mockConfig);
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('the `generateByKeysQueries` private method', () => {
        let graphqlQueryByKeysGenerator;
        beforeEach(() => {
            const fakeReadModels = [AnotherReadModel, ASequencedReadModel];
            const typeInformer = new graphql_type_informer_1.GraphQLTypeInformer(mockLogger);
            mockConfig.readModelSequenceKeys['ASequencedReadModel'] = 'timestamp';
            graphqlQueryByKeysGenerator = new graphql_query_by_keys_generator_1.GraphqlQueryByKeysGenerator(mockConfig, fakeReadModels, typeInformer, () => (0, sinon_1.fake)()); // So we can see private methods
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('generates by ID and sequenced queries', () => {
            const fakeGenerateByIdQuery = (0, sinon_1.fake)();
            (0, sinon_1.replace)(graphqlQueryByKeysGenerator, 'generateByIdQuery', fakeGenerateByIdQuery);
            const fakeGenerateByIdAndSequenceKeyQuery = (0, sinon_1.fake)();
            (0, sinon_1.replace)(graphqlQueryByKeysGenerator, 'generateByIdAndSequenceKeyQuery', fakeGenerateByIdAndSequenceKeyQuery);
            graphqlQueryByKeysGenerator.generateByKeysQueries();
            (0, expect_1.expect)(fakeGenerateByIdQuery).to.have.been.calledOnceWith(AnotherReadModel);
            (0, expect_1.expect)(fakeGenerateByIdAndSequenceKeyQuery).to.have.been.calledOnceWith(ASequencedReadModel, 'timestamp');
        });
    });
    describe('the `generateByIdQuery` private method', () => {
        let graphQLQueryGenerator;
        beforeEach(() => {
            const fakeReadModels = [ARegularReadModel];
            const typeInformer = new graphql_type_informer_1.GraphQLTypeInformer(mockLogger);
            graphQLQueryGenerator = new graphql_query_by_keys_generator_1.GraphqlQueryByKeysGenerator(mockConfig, fakeReadModels, typeInformer, () => (0, sinon_1.fake)()); // So we can see private methods
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('generates a query named after the read model class that accepts a unique ID', () => {
            const fakeByIdResolverBuilder = sinon_1.fake.returns((0, sinon_1.fake)());
            (0, sinon_1.replace)(graphQLQueryGenerator, 'byIDResolverBuilder', fakeByIdResolverBuilder);
            const query = graphQLQueryGenerator.generateByIdQuery(ARegularReadModel);
            (0, expect_1.expect)(query.type).to.has.a.property('name', 'ARegularReadModel');
            (0, expect_1.expect)(query.args).to.have.a.property('id');
            (0, expect_1.expect)(query.resolve).to.be.a('Function');
            (0, expect_1.expect)(fakeByIdResolverBuilder).to.have.been.calledWith(ARegularReadModel);
        });
    });
    describe('the `generateByIdAndSequenceKeyQuery` private method', () => {
        let graphQLQueryGenerator;
        beforeEach(() => {
            const fakeReadModels = [AnotherSequencedReadModel];
            const typeInformer = new graphql_type_informer_1.GraphQLTypeInformer(mockLogger);
            graphQLQueryGenerator = new graphql_query_by_keys_generator_1.GraphqlQueryByKeysGenerator(mockConfig, fakeReadModels, typeInformer, () => (0, sinon_1.fake)()); // So we can see private methods
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('generates a query named after the read model class that accepts an ID and a sequence key', () => {
            const fakeByIdResolverBuilder = sinon_1.fake.returns((0, sinon_1.fake)());
            (0, sinon_1.replace)(graphQLQueryGenerator, 'byIDResolverBuilder', fakeByIdResolverBuilder);
            const query = graphQLQueryGenerator.generateByIdAndSequenceKeyQuery(AnotherSequencedReadModel, 'timestamp');
            (0, expect_1.expect)(query.type).to.be.a('GraphQLList');
            (0, expect_1.expect)(query.type.ofType).to.have.a.property('name', 'AnotherSequencedReadModel');
            (0, expect_1.expect)(query.args).to.have.a.property('id');
            (0, expect_1.expect)(query.args).to.have.a.property('timestamp');
            (0, expect_1.expect)(query.resolve).to.be.a('Function');
            (0, expect_1.expect)(fakeByIdResolverBuilder).to.have.been.calledWith(AnotherSequencedReadModel);
        });
    });
});
