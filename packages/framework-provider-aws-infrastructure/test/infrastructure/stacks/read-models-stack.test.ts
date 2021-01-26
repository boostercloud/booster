/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../../expect'
import { describe } from 'mocha'
import { Stack } from '@aws-cdk/core'
import { BoosterConfig, UUID } from '@boostercloud/framework-types'
import { App } from '@aws-cdk/core'
import { ReadModelsStack } from '../../../src/infrastructure/stacks/read-models-stack'
import { Table } from '@aws-cdk/aws-dynamodb'

describe('ReadModelsStack', () => {
  describe('the `build` method', () => {
    class SomeReadModel {
      public constructor(readonly id: UUID) {}
    }
    const config = new BoosterConfig('test')
    config.userProjectRootPath = '.'
    config.readModels['SomeReadModel'] = {
      class: SomeReadModel,
      authorizedRoles: 'all',
      properties: [],
    }

    const stack = new Stack(new App(), 'some-app')

    it('generates a DynamoDB table', () => {
      const readModelsStack = new ReadModelsStack(config, stack)

      const tables = readModelsStack.build()

      expect(tables.length).to.be.equal(1)
      expect(tables[0]).to.be.instanceOf(Table)
    })
  })
})
