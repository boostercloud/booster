import { BoosterConfig } from '@boostercloud/framework-types'
import { ApplicationStackBuilder } from './stacks/application-stack'
import { App } from '@aws-cdk/core'
import { CloudAssembly, Environment } from '@aws-cdk/cx-api'
import { AppStacks } from 'aws-cdk/lib/api/cxapp/stacks'
import { Configuration } from 'aws-cdk/lib/settings'
import { bootstrapEnvironment, DeployStackResult, SDK } from 'aws-cdk'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { CloudFormationDeploymentTarget } from 'aws-cdk/lib/api/deployment-target'
import { Observable, Subscriber } from 'rxjs'
import { RequireApproval } from 'aws-cdk/lib/diff'

interface StackServiceConfiguration {
  aws: SDK
  appStacks: AppStacks
  cdkToolkit: CdkToolkit
}

async function getEnvironment(aws: SDK): Promise<Environment> {
  const account = await aws.defaultAccount()
  const region = await aws.defaultRegion()

  if (account == undefined || region == undefined) {
    throw new Error('Unable to determine default account and/or region')
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
    stackNames: (await appStacks.listStacks()).map((s): string => s.name),
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
async function nukeApp(observer: Subscriber<string>, config: BoosterConfig): Promise<void> {
  const { aws, appStacks, cdkToolkit } = await getStackServiceConfiguration(config)
  return cdkToolkit.destroy({
    stackNames: (await appStacks.listStacks()).map((s): string => s.name),
    exclusively: false,
    force: true,
    sdk: aws,
    fromDeploy: true,
  })
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
