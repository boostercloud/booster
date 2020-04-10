import { CloudFormation } from 'aws-sdk'
import { expect } from 'chai'

function appStackName(): string {
  return `my-store-${process.env.BOOSTER_APP_SUFFIX}-application-stack`
}

describe('AWS CLI Integration', () => {
  describe('the cli binary', async () => {
    it('deploys the example project successfully', async () => {
      // The project must have been deployed by the deploy hook in setup.ts
      // that scripts uses the cli to do the deployment, so we just check here
      // that the Cloudformation was run by AWS successfully.
      const cloudformation = new CloudFormation()
      const response = await cloudformation
        .describeStacks({
          StackName: appStackName(),
        })
        .promise()

      const firstStack = response?.Stacks?.[0]
      expect(firstStack).not.to.be.undefined
      expect(firstStack?.StackStatus).to.be.equal('CREATE_COMPLETE')
    })
  })
})
