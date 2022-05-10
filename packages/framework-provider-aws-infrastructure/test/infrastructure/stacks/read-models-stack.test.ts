/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../../expect'
import { describe } from 'mocha'
import { Stack, App } from '@aws-cdk/core'
import { BoosterConfig, UUID } from '@boostercloud/framework-types'

import { ReadModelsStack } from '../../../src/infrastructure/stacks/read-models-stack'
import { Table } from '@aws-cdk/aws-dynamodb'

describe('ReadModelsStack', () => {
  describe('the `build` method', () => {
    context('When no sequence key has been defined', () => {
      class SomeReadModel {
        public constructor(readonly id: UUID) {}
      }
      const config = new BoosterConfig('test')
      config.userProjectRootPath = '.'
      config.readModels['SomeReadModel'] = {
        class: SomeReadModel,
        authorizedRoles: 'all',
        properties: [],
        before: [],
      }

      const stack = new Stack(new App(), 'some-app')

      it('generates a DynamoDB table with the field `id` as the partitionKey and no `sequenceKey`', () => {
        const readModelsStack = new ReadModelsStack(config, stack)

        const tables = readModelsStack.build()

        expect(tables.length).to.be.equal(1)
        expect(tables[0]).to.be.instanceOf(Table)

        const someReadModelTable = tables[0] as any
        expect(someReadModelTable?.tablePartitionKey['name']).to.equal('id')
        expect(someReadModelTable?.tablePartitionKey['type']).to.equal('S')
        expect(someReadModelTable.tableSortKey).to.be.undefined
      })
    })

    context('When a sequence key has been defined', () => {
      class SomeReadModel {
        public constructor(readonly id: UUID) {}
      }
      const config = new BoosterConfig('test')
      config.userProjectRootPath = '.'
      config.readModels['SomeReadModel'] = {
        class: SomeReadModel,
        authorizedRoles: 'all',
        properties: [],
        before: [],
      }
      config.readModelSequenceKeys['SomeReadModel'] = 'timestamp'

      const stack = new Stack(new App(), 'some-app')

      it('generates a DynamoDB table with the field `id` as the partitionKey and the defined field as the `sequenceKey`', () => {
        const readModelsStack = new ReadModelsStack(config, stack)

        const tables = readModelsStack.build()

        expect(tables.length).to.be.equal(1)
        expect(tables[0]).to.be.instanceOf(Table)

        const someReadModelTable = tables[0] as any
        expect(someReadModelTable?.tablePartitionKey?.['name']).to.equal('id')
        expect(someReadModelTable?.tablePartitionKey?.['type']).to.equal('S')
        expect(someReadModelTable?.tableSortKey?.['name']).to.equal('timestamp')
        expect(someReadModelTable?.tableSortKey?.['type']).to.equal('S')
      })
    })
  })
})
