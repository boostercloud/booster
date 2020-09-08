import { expect } from 'chai'
import { appStack } from '../utils'

describe('After nuke', () => {
  describe('the stack', () => {
    it('is deleted successfully', async () => {
      const stack = await appStack()

      expect(stack).not.to.be.null
      expect(stack?.StackStatus).to.be.equal('DELETE_COMPLETE')
    })
  })
})
