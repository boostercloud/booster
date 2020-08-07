/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { createStubInstance, fake, match, SinonStubbedInstance, stub } from 'sinon'
import { fetchReadModel, storeReadModel } from '../../src/library/read-model-adapter'
import { BoosterConfig, Logger, ReadModelInterface, UUID } from '@boostercloud/framework-types'
import { CosmosClient } from '@azure/cosmos'
import { random } from 'faker'
import { createMockReadModel } from '../helpers/read-model-helper'

describe('Read Model adapter', () => {
  let mockLogger: Logger
  let mockConfig: BoosterConfig
  let mockReadModel: ReadModelInterface

  let mockReadModelName: string
  let mockReadModelId: UUID

  let mockCosmosDbClient: SinonStubbedInstance<CosmosClient>

  beforeEach(() => {
    mockCosmosDbClient = createStubInstance(CosmosClient, {
      database: stub().returns({
        container: stub().returns({
          items: {
            query: stub().returns({
              fetchAll: fake.resolves({ resources: [] }) as any,
            }),
            upsert: stub().returns(fake.resolves({})),
          },
          item: stub().returns({
            read: stub().returns(fake.resolves({})),
          }),
        }),
      }) as any,
    })
    mockLogger = {
      info: fake(),
      error: fake(),
      debug: fake(),
    }
    mockConfig = new BoosterConfig('test')
    mockReadModelName = random.word()
    mockReadModelId = random.uuid()
    mockReadModel = createMockReadModel()
  })

  describe('The "fetchReadModel" method', () => {
    it('Responds with a read model when it exists', async () => {
      const result = await fetchReadModel(
        mockCosmosDbClient as any,
        mockConfig,
        mockLogger,
        mockReadModelName,
        mockReadModelId
      )

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`)
      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).item
      ).to.have.been.calledWithExactly(mockReadModelId, mockReadModelId)
      expect(result).not.to.be.null
    })
  })

  describe('The "storeReadModel" method', () => {
    it('Saves a read model', async () => {
      const something = await storeReadModel(
        mockCosmosDbClient as any,
        mockConfig,
        mockLogger,
        mockReadModelName,
        mockReadModel as any
      )

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`)
      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).items.upsert
      ).to.have.been.calledWithExactly(match(mockReadModel))
      expect(something).not.to.be.null
    })
  })
})
