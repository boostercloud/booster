import { SDK as stackServiceConfigurationModule } from 'aws-cdk'
import { AppStacks } from 'aws-cdk/lib/api/cxapp/stacks'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { BoosterConfig } from '@boostercloud/framework-types'
import { App } from '@aws-cdk/core'
import { ApplicationStackBuilder } from './stacks/application-stack'
import { InfrastructurePlugin } from '../../src/infrastructure-plugin'
import { CloudAssembly } from '@aws-cdk/cx-api'
import { Configuration } from 'aws-cdk/lib/settings'
import { CloudFormationDeploymentTarget } from 'aws-cdk/lib/api/deployment-target'

interface StackServiceConfiguration {
  aws: stackServiceConfigurationModule
  appStacks: AppStacks
  cdkToolkit: CdkToolkit
}

function assemble(config: BoosterConfig, plugins?: InfrastructurePlugin[]): CloudAssembly {
  const boosterApp = new App()
  new ApplicationStackBuilder(config).buildOn(boosterApp, plugins)
  // Here we could add other optional stacks like one with a lot of dashboards for analytics, etc.

  return boosterApp.synth()
}

// Configure the SDK and the "AppStacks" that contains all the information
// about the application we want to deploy
export async function getStackServiceConfiguration(
  config: BoosterConfig,
  plugins?: InfrastructurePlugin[]
): Promise<StackServiceConfiguration> {
  const aws = new stackServiceConfigurationModule()
  const configuration = await new Configuration().load()
  const appStacks = new AppStacks({
    configuration,
    aws,
    synthesizer: async (): Promise<CloudAssembly> => assemble(config, plugins),
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
