import { ApolloClient, gql, NormalizedCacheObject } from '@apollo/client'
import { random } from 'faker'
import { expect } from '../../helper/expect'
import { applicationUnderTest } from './setup'
import 'mocha'
import { ReadModelInterface, UUID } from '@boostercloud/framework-types'
import { waitForIt } from '../../helper/sleep'

describe('Concurrency end-to-end tests', () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = applicationUnderTest.graphql.client()
  })

  context('ReadModels', () => {
    describe('With one projection', () => {
      it('insert and update generate one ReadModel with version 2', async () => {
        const entityId: UUID = random.uuid()

        const insertedReadModel = await addConcurrency(client, entityId, 1, 'ConcurrencyReadModel')
        expect(insertedReadModel.id).to.be.eq(entityId)
        expect(insertedReadModel.boosterMetadata?.version).to.be.eq(1)

        const updatedReadModel = await addConcurrency(client, entityId, 2, 'ConcurrencyReadModel')
        expect(updatedReadModel.id).to.be.eq(entityId)
        expect(updatedReadModel.boosterMetadata?.version).to.be.eq(2)
      })
    })

    describe('With two projections for the same ReadModel', () => {
      if (process.env.TESTED_PROVIDER === 'AWS') {
        console.log('AWS Provider is not working properly when inserting a ReadModel with two projections') // TODO: Fix AWS Provider
        return
      }
      it('insert and update generate one ReadModel with version 4', async () => {
        const entityId: UUID = random.uuid()

        const insertedReadModel = await addConcurrency(client, entityId, 2, 'OtherConcurrencyReadModel')
        expect(insertedReadModel.id).to.be.eq(entityId)
        expect(insertedReadModel.otherId).to.be.eq(entityId)
        expect(insertedReadModel.boosterMetadata?.version).to.be.eq(2)

        const updatedReadModel = await addConcurrency(client, entityId, 4, 'OtherConcurrencyReadModel')
        expect(updatedReadModel.id).to.be.eq(entityId)
        expect(updatedReadModel.otherId).to.be.eq(entityId)
        expect(updatedReadModel.boosterMetadata?.version).to.be.eq(4)
      })
    })
  })
})

async function addConcurrency(
  client: ApolloClient<NormalizedCacheObject>,
  entityId: UUID,
  expectedVersion: number,
  readModelName: string
): Promise<ReadModelInterface> {
  await client.mutate({
    variables: {
      id: entityId,
      otherId: entityId,
    },
    mutation: gql`
      mutation AddConcurrency($id: ID!, $otherId: ID!) {
        AddConcurrency(input: { id: $id, otherId: $otherId })
      }
    `,
  })

  const mutateResult = await waitForIt(
    () => {
      return client.mutate({
        variables: {
          id: entityId,
          readModelName: readModelName,
        },
        mutation: gql`
          mutation GetConcurrency($id: ID!, $readModelName: String!) {
            GetConcurrency(input: { id: $id, readModelName: $readModelName })
          }
        `,
      })
    },
    (result) =>
      result?.data?.GetConcurrency &&
      result?.data?.GetConcurrency.length > 0 &&
      result?.data?.GetConcurrency[0] &&
      (result?.data?.GetConcurrency as Array<ReadModelInterface>).find(
        (value: ReadModelInterface) => value.boosterMetadata?.version === expectedVersion
      ) !== undefined
  )

  const concurrency = (mutateResult?.data?.GetConcurrency as Array<ReadModelInterface>).find(
    (value: ReadModelInterface) => value.boosterMetadata?.version === expectedVersion
  )!
  expect(concurrency.id).to.be.eq(entityId)
  return concurrency
}
