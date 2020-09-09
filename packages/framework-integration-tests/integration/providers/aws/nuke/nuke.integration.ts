import * as chai from 'chai'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

export const expect = chai.expect

import { appStack, appStackName } from '../utils'

describe('After nuke', () => {
  describe('the stack', () => {
    it('is deleted successfully', async () => {
      await expect(appStack()).to.be.eventually.rejectedWith(`Stack with id ${appStackName()} does not exist`)
    })
  })
})
