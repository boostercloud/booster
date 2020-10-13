/* eslint-disable @typescript-eslint/no-explicit-any */
import { ReadModelEnvelope } from '@boostercloud/framework-types'
import { expect } from '../expect'
import * as faker from 'faker'
import { stub, restore } from 'sinon'
import { ReadModelRegistry } from '../../src/services'
import { createMockReadModelEnvelope } from '../helpers/read-model-helper'
import { random } from 'faker'

describe('the read model registry', () => {
  let initialReadModelsCount: number
  let mockReadModel: ReadModelEnvelope

  let readModelRegistry: ReadModelRegistry

  beforeEach(async () => {
    initialReadModelsCount = random.number({ min: 2, max: 10 })
    readModelRegistry = new ReadModelRegistry()

    // Clear all read models
    await readModelRegistry.deleteAll()
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
  })

  describe('delete all', () => {
    beforeEach(async () => {
      const mockEvent: ReadModelEnvelope = createMockReadModelEnvelope()
      await readModelRegistry.store(mockEvent)
    })

    it('should clear all read models', async () => {
      const numberOfDeletedEvents = await readModelRegistry.deleteAll()

      expect(numberOfDeletedEvents).to.be.equal(1)
      expect(await readModelRegistry.query({})).to.be.deep.equal([])
    })
  })

  describe('the store method', () => {
    it('should insert read models into the read models database', async () => {
      const mockEvent: ReadModelEnvelope = createMockReadModelEnvelope()

      readModelRegistry.readModels.insert = stub().yields(null, mockEvent)

      await readModelRegistry.store(mockEvent)
      return expect(readModelRegistry.readModels.insert).to.have.been.called
    })

    it('should throw if the database `insert` fails', async () => {
      const readModel: ReadModelEnvelope = {
        value: {
          id: faker.random.uuid(),
        },
        typeName: faker.random.word(),
      }

      const error = new Error(faker.random.words())

      readModelRegistry.readModels.insert = stub().yields(error, null)

      return expect(readModelRegistry.store(readModel)).to.be.rejectedWith(error)
    })
  })
})
