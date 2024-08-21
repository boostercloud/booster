import { Construct } from 'constructs'
import { Fn, TerraformStack } from 'cdktf'
import { ApplicationSynth } from './synth/application-synth'
import { ApplicationSynthStack } from './types/application-synth-stack'
import { InfrastructureRocket } from './rockets/infrastructure-rocket'
import { environmentVarNames } from '@boostercloud/framework-provider-azure'

export class AzureStack extends TerraformStack {
  readonly applicationStack: ApplicationSynthStack
  readonly defaultApplicationSettings: { [key: string]: string }

  constructor(scope: Construct, name: string, zipFile?: string) {
    super(scope, name)

    const applicationSynth = new ApplicationSynth(this)
    this.applicationStack = applicationSynth.synth(zipFile)
    this.defaultApplicationSettings = applicationSynth.buildDefaultAppSettings(
      this.applicationStack,
      this.applicationStack.storageAccount!,
      'func'
    )
  }

  public addAppSettingsToFunctionApp(rockets?: InfrastructureRocket[]): void {
    if (!this.applicationStack.functionApp) {
      throw new Error('Function app not defined')
    }

    const functionAppNames = rockets
      ? rockets
          .map((rocket: InfrastructureRocket) =>
            rocket.getFunctionAppName ? rocket.getFunctionAppName(this.applicationStack) : ''
          )
          .join(',')
      : ''

    this.applicationStack.functionApp.appSettings = Fn.merge([
      this.defaultApplicationSettings,
      { [environmentVarNames.rocketFunctionAppNames]: functionAppNames },
    ])
  }
}
