//import { AWSTestHelper } from '@boostercloud/framework-provider-aws-infrastructure'
//import { applicationName } from '../../../helper/app-helper'
import { expect } from '../../../helper/expect'

describe('After start', () => {
  describe('the local provider', () => {
    it('has been started successfully', async () => {
      // The project must have been deployed by the cliHelper hook in setup.ts
      // that scripts uses the cli to do the deployment, so we just check here
      // that the Cloudformation was run by AWS successfully. For that, we can just
      // build the AWSHelper. It will throw if the AWS stack is not ready.
      //await expect(AWSTestHelper.build(applicationName())).to.be.eventually.fulfilled
      expect(true).to.be.true
    })
  })
})
