import { AzureTestHelper } from '@boostercloud/framework-provider-azure-infrastructure'
import { applicationName, checkAndGetCurrentEnvironment } from '../../../helper/app-helper'
import { expect } from '../../../helper/expect'

describe('After nuke', () => {
  describe('the resource group', () => {
    it('is deleted successfully', async () => {
      const environmentName = checkAndGetCurrentEnvironment()
      await expect(
        AzureTestHelper.checkResourceGroup(applicationName(), environmentName)
      ).to.be.eventually.rejectedWith('ResourceGroupNotFound')
    })
  })
})
