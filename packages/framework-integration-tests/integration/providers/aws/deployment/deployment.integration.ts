import { expect } from 'chai'
import { appStack } from '../utils'

describe('After deployment', () => {
  describe('the stack', () => {
    it('has been created successfully', async () => {
      // The project must have been deployed by the deploy hook in setup.ts
      // that scripts uses the cli to do the deployment, so we just check here
      // that the Cloudformation was run by AWS successfully.
      const stack = await appStack()

      expect(stack).not.to.be.null
      expect(stack?.StackStatus).to.be.oneOf(['CREATE_COMPLETE', 'UPDATE_COMPLETE'])
    })
  })
})
