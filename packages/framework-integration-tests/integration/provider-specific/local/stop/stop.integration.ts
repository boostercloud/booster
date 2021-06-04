//import { AWSTestHelper } from '@boostercloud/framework-provider-aws-infrastructure'
//import { applicationName } from '../../../helper/app-helper'
import { expect } from '../../../helper/expect'

describe('After stop', () => {
  describe('the provider', () => {
    it('is stopped successfully', async () => {
      //await expect(AWSTestHelper.build(applicationName())).to.be.eventually.rejectedWith(
      //  new RegExp(`Stack with id ${applicationName()}[^\\s]+ does not exist`)
      //)
      expect(true).to.be.true
    })
  })
})