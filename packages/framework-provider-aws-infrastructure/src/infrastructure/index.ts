import { BoosterConfig } from '@boostercloud/framework-types'
import { ApplicationStackBuilder } from './stacks/application-stack'
import { App } from '@aws-cdk/core'
import { CloudAssembly, Environment } from '@aws-cdk/cx-api'
import { AppStacks } from 'aws-cdk/lib/api/cxapp/stacks'
import { Configuration } from 'aws-cdk/lib/settings'
import { bootstrapEnvironment, DeployStackResult, Mode, SDK } from 'aws-cdk'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { CloudFormationDeploymentTarget } from 'aws-cdk/lib/api/deployment-target'
import { Observable, Subscriber } from 'rxjs'
import { RequireApproval } from 'aws-cdk/lib/diff'
import * as colors from 'colors'
import { emptyS3Bucket } from './s3utils'

interface StackServiceConfiguration {
  aws: SDK
  appStacks: AppStacks
  cdkToolkit: CdkToolkit
}

async function getEnvironment(aws: SDK): Promise<Environment> {
  const account = await aws.defaultAccount()
  const region = await aws.defaultRegion()

  if (!account) {
    throw new Error(
      'Unable to load default AWS account. Check that you have properly set your AWS credentials in `~/.aws/credentials` file or the corresponding environment variables. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-credentials-node.html'
    )
  }
  if (!region) {
    throw new Error(
      'Unable to determine default region. Check that you have set it in your `~/.aws/config` file or AWS_REGION environment variable. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-region.html#setting-region-order-of-precedence'
    )
  }

  return {
    name: 'Default environment',
    account,
    region,
  }
}

function bootstrapResultToMessage(result: DeployStackResult): string {
  return `Environment ${result.stackArn} bootstrapped${result.noOp ? ' (no changes).' : '.'}`
}

async function bootstrap(
  observer: Subscriber<string>,
  config: BoosterConfig,
  appStacks: AppStacks,
  aws: SDK
): Promise<string> {
  const toolkitStackName: string = config.appName + '-toolkit'

  const env: Environment = await getEnvironment(aws)
  observer.next('Bootstraping the following environment: ' + JSON.stringify(env))
  const result = await bootstrapEnvironment(env, aws, toolkitStackName, undefined, {
    bucketName: config.appName + '-toolkit-bucket',
  })
  observer.next(bootstrapResultToMessage(result))
  return toolkitStackName
}

// Exported for testing
export function assemble(config: BoosterConfig): CloudAssembly {
  const boosterApp = new App()
  new ApplicationStackBuilder(config).buildOn(boosterApp)
  // Here we could add other optional stacks like one with a lot of dashboards for analytics, etc.

  return boosterApp.synth()
}

/**
 * Deploys the application using the credentials located in ~/.aws
 */
async function deployApp(observer: Subscriber<string>, config: BoosterConfig): Promise<void> {
  const { aws, appStacks, cdkToolkit } = await getStackServiceConfiguration(config)

  const toolkitStackName = await bootstrap(observer, config, appStacks, aws)

  return cdkToolkit.deploy({
    toolkitStackName: toolkitStackName,
    stackNames: (await appStacks.listStacks()).map((s): string => s.stackName),
    requireApproval: RequireApproval.Never,
    sdk: aws,
  })
}

// Configure the SDK and the "AppStacks" that contains all the information
// about the application we want to deploy
async function getStackServiceConfiguration(config: BoosterConfig): Promise<StackServiceConfiguration> {
  const aws = new SDK()
  const configuration = await new Configuration().load()
  const appStacks = new AppStacks({
    configuration,
    aws,
    synthesizer: (): Promise<CloudAssembly> => Promise.resolve(assemble(config)),
  })
  const cdkToolkit = new CdkToolkit({
    appStacks,
    provisioner: new CloudFormationDeploymentTarget({ aws }),
  })
  return {
    aws,
    appStacks,
    cdkToolkit,
  }
}

/**
 * Nuke all the resources used in the "AppStacks"
 */
async function nukeApp(observer: Subscriber<string>, config: BoosterConfig): Promise<[ void, void ]> {
  const {aws, appStacks, cdkToolkit} = await getStackServiceConfiguration(config)
  const toolkit = nukeToolkit(observer, config, aws)
  const app = cdkToolkit.destroy({
    stackNames: (await appStacks.listStacks()).map((s): string => s.stackName),
    exclusively: false,
    force: true,
    sdk: aws,
  })
  return Promise.all([ toolkit, app ])
}

/**
 * Nuke all the resources used in the "Toolkit Stack"
 */
async function nukeToolkit(observer: Subscriber<string>, config: BoosterConfig, aws: SDK): Promise<void> {
  const stackName = config.appName + '-toolkit'
  observer.next(colors.blue(stackName) + colors.yellow(': destroying...'))
  await emptyS3Bucket(observer, config.appName + '-toolkit-bucket', aws)
  await emptyS3Bucket(observer, config.resourceNames.staticWebsite, aws)
  const cloudFormation = await aws.cloudFormation(
    await aws.defaultAccount(),
    await aws.defaultRegion(),
    Mode.ForWriting
  )
  await cloudFormation.deleteStack({StackName: stackName}).promise()
  observer.next('✅  ' + colors.blue(stackName) + colors.red(': DESTROYED'))
}

export function deploy(configuration: BoosterConfig): Observable<string> {
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    deployApp(observer, configuration)
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}

export function nuke(configuration: BoosterConfig): Observable<string> {
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    nukeApp(observer, configuration)
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}
