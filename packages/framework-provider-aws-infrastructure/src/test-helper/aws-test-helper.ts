import { Stack } from 'aws-sdk/clients/cloudformation'
import { CloudFormation, config } from 'aws-sdk'
import { AWSCounters } from './aws-counters'
import { AWSQueries } from './aws-queries'

interface ApplicationOutputs {
  graphqlURL: string
  websocketURL: string
  healthURL: string
}

const cloudFormation = new CloudFormation()

export class AWSTestHelper {
  private constructor(
    readonly outputs: ApplicationOutputs,
    readonly counters: AWSCounters,
    readonly queries: AWSQueries
  ) {}

  public static async build(appName: string): Promise<AWSTestHelper> {
    const stackName = `${appName}-app`
    this.ensureAWSConfiguration()
    const stack = await this.appStack(stackName)
    this.ensureStackIsReady(stack)
    return new AWSTestHelper(
      {
        graphqlURL: await this.graphqlURL(stack),
        websocketURL: await this.websocketURL(stack),
        healthURL: '',
      },
      new AWSCounters(stackName),
      new AWSQueries(stackName)
    )
  }

  public static ensureAWSConfiguration(): void {
    console.log('Checking AWS configuration...')
    if (!config.credentials?.accessKeyId || !config.credentials?.secretAccessKey) {
      throw new Error(
        "AWS credentials were not properly loaded by the AWS SDK and are required to run the integration tests. Check that you've set them in your `~/.aws/credentials` file or environment variables. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-credentials-node.html"
      )
    }
    if (!config.region) {
      throw new Error(
        "AWS region was not properly loaded by the AWS SDK and is required to run the integration tests. Check that you've set it in your `~/.aws/config` file or AWS_REGION environment variable. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-region.html#setting-region-order-of-precedence"
      )
    } else {
      console.log('AWS Region set to ' + config.region)
    }
  }

  private static ensureStackIsReady(stack: Stack): void {
    const validStatuses = ['CREATE_COMPLETE', 'UPDATE_COMPLETE']
    if (!validStatuses.includes(stack?.StackStatus)) {
      throw new Error(`The stack '${stack.StackName}' is not ready. Its status is ${stack.StackStatus}`)
    }
  }

  private static async appStack(stackName: string): Promise<Stack> {
    const { Stacks } = await cloudFormation
      .describeStacks({
        StackName: stackName,
      })
      .promise()

    if (Stacks?.[0]) {
      return Stacks[0]
    } else {
      throw `No stack found with name "${stackName}". Try running 'integration/aws-deploy' first to make sure that the AWS environment is properly set.`
    }
  }

  private static async graphqlURL(stack: Stack): Promise<string> {
    const url = stack.Outputs?.find((output) => {
      return output.OutputKey === 'graphqlURL'
    })?.OutputValue

    if (!url) {
      throw 'Unable to get the Base HTTP URL from the current stack'
    }
    return url
  }

  private static async websocketURL(stack: Stack): Promise<string> {
    const url = stack.Outputs?.find((output) => {
      return output.OutputKey === 'websocketURL'
    })?.OutputValue

    return url || ''
  }
}
