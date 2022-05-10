import { AzureTestHelper } from '@boostercloud/framework-provider-azure-infrastructure'
import { applicationName, checkAndGetCurrentEnv } from '../../../helper/app-helper'
import { expect } from '../../../helper/expect'

describe('After nuke', () => {
  describe('the resource group', () => {
    it('is deleted successfully', async () => {
      const environmentName = checkAndGetCurrentEnv()
      await expect(
        AzureTestHelper.checkResourceGroup(applicationName(), environmentName)
      ).to.be.eventually.rejectedWith('ResourceGroupNotFound')
    })
  })
})
