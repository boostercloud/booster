"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const framework_provider_azure_infrastructure_1 = require("@boostercloud/framework-provider-azure-infrastructure");
const expect_1 = require("../../../helper/expect");
const app_helper_1 = require("../../../helper/app-helper");
const faker_1 = require("faker");
const sleep_1 = require("../../../helper/sleep");
const application_tester_1 = require("@boostercloud/application-tester");
const client_1 = require("@apollo/client");
describe('After deployment', () => {
    describe('the ARM template', () => {
        it('has been created successfully', async () => {
            // The project must have been deployed by the cliHelper hook in setup.ts
            // that scripts uses the cli to do the deployment, so we just check here
            // that the resource group exists
            const environmentName = (0, app_helper_1.checkAndGetCurrentEnv)();
            const applicationUnderTest = new application_tester_1.ApplicationTester(await (0, app_helper_1.getProviderTestHelper)());
            let resourceGroup = null;
            let error = false;
            try {
                console.log('Checking resource group...');
                resourceGroup = await framework_provider_azure_infrastructure_1.AzureTestHelper.checkResourceGroup((0, app_helper_1.applicationName)(), environmentName);
            }
            catch (err) {
                console.error(err);
                error = true;
            }
            (0, expect_1.expect)(error).to.be.false;
            (0, expect_1.expect)(resourceGroup).to.exist;
            const adminEmail = faker_1.internet.email();
            const authToken = applicationUnderTest.token.forUser(adminEmail, 'Admin');
            console.log('Creating a new product...');
            const mutationResult = await (0, sleep_1.waitForIt)(async () => {
                try {
                    console.log('Performing mutation');
                    const client = applicationUnderTest.graphql.client(authToken);
                    return await client.mutate({
                        variables: {
                            sku: faker_1.random.uuid(),
                            displayName: faker_1.commerce.product(),
                            description: faker_1.commerce.productDescription(),
                            priceInCents: Math.floor(Math.random() * 100 + 1),
                            currency: faker_1.finance.currencyName(),
                        },
                        mutation: (0, client_1.gql) `
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
                    });
                }
                catch (err) {
                    console.error(err);
                    return null;
                }
            }, (result) => result != null, 30000, 7200000);
            (0, expect_1.expect)(mutationResult === null || mutationResult === void 0 ? void 0 : mutationResult.data).to.exist;
        });
    });
});
