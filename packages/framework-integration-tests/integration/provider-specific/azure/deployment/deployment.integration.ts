/* eslint-disable @typescript-eslint/no-explicit-any */
import { AzureTestHelper } from '@boostercloud/framework-provider-azure-infrastructure'
import { expect } from '../../../helper/expect'
import { applicationName, checkAndGetCurrentEnv, getProviderTestHelper } from '../../../helper/app-helper'
import { internet, random, commerce, finance } from 'faker'
import { waitForIt } from '../../../helper/sleep'
import { ApplicationTester } from '@boostercloud/application-tester'
import { gql } from '@apollo/client'

describe('After deployment', () => {
  describe('the ARM template', () => {
    it('has been created successfully', async () => {
      // The project must have been deployed by the cliHelper hook in setup.ts
      // that scripts uses the cli to do the deployment, so we just check here
      // that the resource group exists
      const environmentName = checkAndGetCurrentEnv()
      const applicationUnderTest = new ApplicationTester(await getProviderTestHelper())
      let resourceGroup: any = null
      let error = false
      try {
        console.log('Checking resource group...')
        resourceGroup = await AzureTestHelper.checkResourceGroup(applicationName(), environmentName)
      } catch (err) {
        console.error(err)
        error = true
      }
      expect(error).to.be.false
      expect(resourceGroup).to.exist

      const adminEmail = internet.email()
      const authToken = applicationUnderTest.token.forUser(adminEmail, 'Admin')
      console.log('Creating a new product...')
      const mutationResult = await waitForIt(
        async () => {
          try {
            console.log('Performing mutation')
            const client = applicationUnderTest.graphql.client(authToken)
            return await client.mutate({
              variables: {
                sku: random.uuid(),
                displayName: commerce.product(),
                description: commerce.productDescription(),
                priceInCents: Math.floor(Math.random() * 100 + 1),
                currency: finance.currencyName(),
              },
              mutation: gql`
                mutation CreateProduct(
                  $sku: String!
                  $displayName: String!
                  $description: String!
                  $priceInCents: Float!
                  $currency: String!
                ) {
                  CreateProduct(
                    input: {
                      sku: $sku
                      displayName: $displayName
                      description: $description
                      priceInCents: $priceInCents
                      currency: $currency
                    }
                  )
                }
              `,
            })
          } catch (err) {
            console.error(err)
            return null
          }
        },
        (result) => result != null,
        30000,
        3600000
      )
      expect(mutationResult?.data).to.exist
    })
  })
})
