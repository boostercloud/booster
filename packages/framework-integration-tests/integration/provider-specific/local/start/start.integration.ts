import { LocalTestHelper } from '@boostercloud/framework-provider-local-infrastructure'
import { applicationName } from '../../../helper/app-helper'
import { expect } from '../../../helper/expect'

describe('After start', () => {
  describe('the local provider', () => {
    it('has been started successfully', async () => {
      await expect(LocalTestHelper.build(applicationName())).to.be.eventually.fulfilled
    })
  })
})
