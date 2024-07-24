"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
const faker = require("faker");
const faker_1 = require("faker");
const sinon_1 = require("sinon");
const services_1 = require("../../src/services");
const read_model_helper_1 = require("../helpers/read-model-helper");
describe('the read model registry', () => {
    let initialReadModelsCount;
    let mockReadModel;
    let readModelRegistry;
    beforeEach(async () => {
        initialReadModelsCount = faker_1.random.number({ min: 2, max: 10 });
        readModelRegistry = new services_1.ReadModelRegistry();
        // Clear all read models
        readModelRegistry.readModels.remove({}, { multi: true });
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('query', () => {
        beforeEach(async () => {
            const publishPromises = [];
            for (let i = 0; i < initialReadModelsCount; i++) {
                publishPromises.push(readModelRegistry.store((0, read_model_helper_1.createMockReadModelEnvelope)(), 0));
            }
            await Promise.all(publishPromises);
            mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
            await readModelRegistry.store(mockReadModel, 1);
        });
        it('should return expected read model', async () => {
            const result = await readModelRegistry.query({
                value: mockReadModel.value,
                typeName: mockReadModel.typeName,
            });
            (0, expect_1.expect)(result.length).to.be.equal(1);
            (0, expect_1.expect)(result[0]).to.deep.include(mockReadModel);
        });
        it('should return expected read model by id', async () => {
            const result = await readModelRegistry.query({
                'value.id': mockReadModel.value.id,
                typeName: mockReadModel.typeName,
            });
            (0, expect_1.expect)(result.length).to.be.equal(1);
            (0, expect_1.expect)(result[0]).to.deep.include(mockReadModel);
        });
        it('should return expected read model when field does not exist', async () => {
            const result = await readModelRegistry.query({
                'value.id': mockReadModel.value.id,
                'value.other': { $exists: false },
                typeName: mockReadModel.typeName,
            });
            (0, expect_1.expect)(result.length).to.be.equal(1);
            (0, expect_1.expect)(result[0]).to.deep.include(mockReadModel);
        });
        it('should return no results when id do not match', async () => {
            const result = await readModelRegistry.query({
                'value.id': faker_1.random.uuid(),
                typeName: mockReadModel.typeName,
            });
            (0, expect_1.expect)(result.length).to.be.equal(0);
        });
        it('should return no results when typeName do not match', async () => {
            const result = await readModelRegistry.query({
                'value.id': mockReadModel.value.id,
                typeName: faker_1.random.words(),
            });
            (0, expect_1.expect)(result.length).to.be.equal(0);
        });
        it('should return no results when age is greater than max age', async () => {
            const result = await readModelRegistry.query({
                'value.age': { $gt: 40 },
            });
            (0, expect_1.expect)(result.length).to.be.equal(0);
        });
        it('should return all results when age is less than or equal than max age', async () => {
            const result = await readModelRegistry.query({
                'value.age': { $lte: 40 },
            });
            (0, expect_1.expect)(result.length).to.be.equal(initialReadModelsCount + 1);
        });
        it('should return all results sorted by Age', async () => {
            const result = await readModelRegistry.query({}, {
                age: 'DESC',
            });
            (0, expect_1.expect)(result.length).to.be.equal(initialReadModelsCount + 1);
            (0, read_model_helper_1.assertOrderByAgeDesc)(result);
        });
        it('should return all results sorted by Age and ID', async () => {
            const result = await readModelRegistry.query({}, {
                age: 'DESC',
                id: 'DESC',
            });
            (0, expect_1.expect)(result.length).to.be.equal(initialReadModelsCount + 1);
            (0, read_model_helper_1.assertOrderByAgeAndIdDesc)(result);
        });
        it('should return 1 result when age is less than or equal than max age', async () => {
            const result = await readModelRegistry.query({
                'value.age': { $lte: 40 },
                typeName: mockReadModel.typeName,
            });
            (0, expect_1.expect)(result.length).to.be.equal(1);
        });
        it('should return some results when age is between a range with an and', async () => {
            const result = await readModelRegistry.query({
                $and: [{ 'value.age': { $lte: 40 } }, { 'value.age': { $gte: 1 } }],
            });
            (0, expect_1.expect)(result.length).to.be.greaterThan(1);
            (0, expect_1.expect)(result.length).to.be.lte(initialReadModelsCount + 1);
        });
        it('should return 1 result when you search with string', async () => {
            const result = await readModelRegistry.query({
                'value.foo': mockReadModel.value.foo,
                typeName: mockReadModel.typeName,
            });
            (0, expect_1.expect)(result.length).to.be.equal(1);
            (0, expect_1.expect)(result[0]).to.deep.include(mockReadModel);
        });
        it('should return 1 result when you search with a RegExp', async () => {
            const result = await readModelRegistry.query({
                'value.foo': new RegExp(mockReadModel.value.foo.substring(0, 4)),
                typeName: mockReadModel.typeName,
            });
            (0, expect_1.expect)(result.length).to.be.equal(1);
            (0, expect_1.expect)(result[0]).to.deep.include(mockReadModel);
        });
        it('should return n-1 results when you search with string and not operator', async () => {
            const result = await readModelRegistry.query({
                $not: { 'value.foo': mockReadModel.value.foo },
            });
            (0, expect_1.expect)(result.length).to.be.equal(initialReadModelsCount);
            (0, expect_1.expect)(result[0]).to.not.deep.include(mockReadModel);
        });
        it('should return only projected fields', async () => {
            const result = await readModelRegistry.query({
                value: mockReadModel.value,
                typeName: mockReadModel.typeName,
            }, undefined, undefined, undefined, ['id', 'age']);
            (0, expect_1.expect)(result.length).to.be.equal(1);
            const expectedReadModel = {
                value: {
                    id: mockReadModel.value.id,
                    age: mockReadModel.value.age,
                },
            };
            (0, expect_1.expect)(result[0]).to.deep.include(expectedReadModel);
        });
        it('should return only projected fields with array fields', async () => {
            const result = await readModelRegistry.query({
                value: mockReadModel.value,
                typeName: mockReadModel.typeName,
            }, undefined, undefined, undefined, ['id', 'age', 'arr[].id']);
            (0, expect_1.expect)(result.length).to.be.equal(1);
            const expectedReadModel = {
                value: {
                    id: mockReadModel.value.id,
                    age: mockReadModel.value.age,
                    arr: mockReadModel.value.arr.map((item) => ({ id: item.id })),
                },
            };
            (0, expect_1.expect)(result[0]).to.deep.include(expectedReadModel);
        });
    });
    describe('delete by id', () => {
        it('should delete read models by id', async () => {
            const mockReadModelEnvelope = (0, read_model_helper_1.createMockReadModelEnvelope)();
            const id = '1';
            mockReadModelEnvelope.value.id = id;
            readModelRegistry.readModels.removeAsync = (0, sinon_1.stub)().returns(mockReadModelEnvelope);
            await readModelRegistry.store(mockReadModelEnvelope, 1);
            await readModelRegistry.deleteById(id, mockReadModelEnvelope.typeName);
            (0, expect_1.expect)(readModelRegistry.readModels.removeAsync).to.have.been.calledWith({ typeName: mockReadModelEnvelope.typeName, 'value.id': id }, { multi: false });
        });
    });
    describe('the store method', () => {
        it('should upsert read models into the read models database', async () => {
            const readModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
            readModel.value.boosterMetadata.version = 2;
            const expectedQuery = {
                typeName: readModel.typeName,
                'value.id': readModel.value.id,
                'value.boosterMetadata.version': 2,
            };
            readModelRegistry.readModels.updateAsync = (0, sinon_1.stub)().returns(readModel);
            await readModelRegistry.store(readModel, 2);
            (0, expect_1.expect)(readModelRegistry.readModels.updateAsync).to.have.been.calledWith(expectedQuery, readModel, {
                upsert: false,
                returnUpdatedDocs: true,
            });
        });
        it('should throw if the database `insert` fails', async () => {
            const readModel = {
                value: {
                    id: faker.random.uuid(),
                },
                typeName: faker.random.word(),
            };
            const error = new Error(faker.random.words());
            readModelRegistry.readModels.update = (0, sinon_1.stub)().yields(error, null);
            void (0, expect_1.expect)(readModelRegistry.store(readModel, 1)).to.be.rejectedWith(error);
        });
    });
});
