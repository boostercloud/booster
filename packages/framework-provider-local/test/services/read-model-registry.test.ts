/* eslint-disable @typescript-eslint/no-explicit-any */
import { ReadModelEnvelope } from '@boostercloud/framework-types'
import { expect } from '../expect'
import * as faker from 'faker'
import { stub, restore } from 'sinon'
import { ReadModelRegistry } from '../../src/services'
import {
  assertOrderByAgeAndIdDesc,
  assertOrderByAgeDesc,
  createMockReadModelEnvelope,
} from '../helpers/read-model-helper'
import { random } from 'faker'

describe('the read model registry', () => {
  let initialReadModelsCount: number
  let mockReadModel: ReadModelEnvelope

  let readModelRegistry: ReadModelRegistry

  beforeEach(async () => {
    initialReadModelsCount = random.number({ min: 2, max: 10 })
    readModelRegistry = new ReadModelRegistry()

    // Clear all read models
    readModelRegistry.readModels.remove({}, { multi: true })
  })

  afterEach(() => {
    restore()
  })

  describe('query', () => {
    beforeEach(async () => {
      const publishPromises: Array<Promise<any>> = []

      for (let i = 0; i < initialReadModelsCount; i++) {
        publishPromises.push(readModelRegistry.store(createMockReadModelEnvelope()))
      }

      await Promise.all(publishPromises)

      mockReadModel = createMockReadModelEnvelope()
      await readModelRegistry.store(mockReadModel)
    })

    it('should return expected read model', async () => {
      const result = await readModelRegistry.query({
        value: mockReadModel.value,
        typeName: mockReadModel.typeName,
      })

      expect(result.length).to.be.equal(1)
      expect(result[0]).to.deep.include(mockReadModel)
    })

    it('should return expected read model by id', async () => {
      const result = await readModelRegistry.query({
        'value.id': mockReadModel.value.id,
        typeName: mockReadModel.typeName,
      })

      expect(result.length).to.be.equal(1)
      expect(result[0]).to.deep.include(mockReadModel)
    })

    it('should return no results when id do not match', async () => {
      const result = await readModelRegistry.query({
        'value.id': random.uuid(),
        typeName: mockReadModel.typeName,
      })

      expect(result.length).to.be.equal(0)
    })

    it('should return no results when typeName do not match', async () => {
      const result = await readModelRegistry.query({
        'value.id': mockReadModel.value.id,
        typeName: random.words(),
      })

      expect(result.length).to.be.equal(0)
    })

    it('should return no results when age is greater than max age', async () => {
      const result = await readModelRegistry.query({
        'value.age': { $gt: 40 },
      })

      expect(result.length).to.be.equal(0)
    })

    it('should return all results when age is less than or equal than max age', async () => {
      const result = await readModelRegistry.query({
        'value.age': { $lte: 40 },
      })

      expect(result.length).to.be.equal(initialReadModelsCount + 1)
    })

    it('should return all results sorted by Age', async () => {
      const result = await readModelRegistry.query(
        {},
        {
          value: {
            age: 'DESC',
          },
        }
      )

      expect(result.length).to.be.equal(initialReadModelsCount + 1)
      assertOrderByAgeDesc(result)
    })

    it('should return all results sorted by Age and ID', async () => {
      const result = await readModelRegistry.query(
        {},
        {
          value: {
            age: 'DESC',
            id: 'DESC',
          },
        }
      )

      expect(result.length).to.be.equal(initialReadModelsCount + 1)
      assertOrderByAgeAndIdDesc(result)
    })

    it('should return 1 result when age is less than or equal than max age', async () => {
      const result = await readModelRegistry.query({
        'value.age': { $lte: 40 },
        typeName: mockReadModel.typeName,
      })

      expect(result.length).to.be.equal(1)
    })

    it('should return some results when age is between a range with an and', async () => {
      const result = await readModelRegistry.query({
        $and: [{ 'value.age': { $lte: 40 } }, { 'value.age': { $gte: 1 } }],
      })

      expect(result.length).to.be.greaterThan(1)
      expect(result.length).to.be.lte(initialReadModelsCount + 1)
    })

    it('should return 1 result when you search with string', async () => {
      const result = await readModelRegistry.query({
        'value.foo': mockReadModel.value.foo,
        typeName: mockReadModel.typeName,
      })

      expect(result.length).to.be.equal(1)
      expect(result[0]).to.deep.include(mockReadModel)
    })

    it('should return 1 result when you search with a RegExp', async () => {
      const result = await readModelRegistry.query({
        'value.foo': new RegExp(mockReadModel.value.foo.substring(0, 4)),
        typeName: mockReadModel.typeName,
      })

      expect(result.length).to.be.equal(1)
      expect(result[0]).to.deep.include(mockReadModel)
    })

    it('should return n-1 results when you search with string and not operator', async () => {
      const result = await readModelRegistry.query({
        $not: { 'value.foo': mockReadModel.value.foo },
      })

      expect(result.length).to.be.equal(initialReadModelsCount)
      expect(result[0]).to.not.deep.include(mockReadModel)
    })
  })

  xdescribe('delete by id', () => {
    it('should delete read models by id', async () => {
      const mockEvent: ReadModelEnvelope = createMockReadModelEnvelope()
      const id = '1'
      mockEvent.value.id = id
      await readModelRegistry.store(mockEvent)
      const numberOfDeletedEvents = await readModelRegistry.deleteById(id)
      expect(numberOfDeletedEvents).to.be.equal(1)
      expect(await readModelRegistry.query({})).to.be.deep.equal([])
    })
  })

  describe('the store method', () => {
    it('should upsert read models into the read models database', async () => {
      const readModel: ReadModelEnvelope = createMockReadModelEnvelope()
      const expectedQuery = { typeName: readModel.typeName, 'value.id': readModel.value.id }

      readModelRegistry.readModels.update = stub().yields(null, readModel)

      await readModelRegistry.store(readModel)
      expect(readModelRegistry.readModels.update).to.have.been.calledWith(expectedQuery, readModel, {
        upsert: true,
      })
    })

    it('should throw if the database `insert` fails', async () => {
      const readModel: ReadModelEnvelope = {
        value: {
          id: faker.random.uuid(),
        },
        typeName: faker.random.word(),
      }

      const error = new Error(faker.random.words())

      readModelRegistry.readModels.update = stub().yields(error, null)

      void expect(readModelRegistry.store(readModel)).to.be.rejectedWith(error)
    })
  })
})
