import { AzureTestHelper } from '@boostercloud/framework-provider-azure-infrastructure'
import { applicationName } from '../../../helper/app-helper'
import { expect } from '../../../helper/expect'

describe('After nuke', () => {
  describe('the stack', () => {
    it('is deleted successfully', async () => {
      await expect(AzureTestHelper.build(applicationName())).to.be.eventually.rejectedWith(
        new RegExp(`Stack with id ${applicationName()}[^\\s]+ does not exist`)
      )
    })
  })
})
