import { Construct } from 'constructs'
import { Fn, TerraformStack } from 'cdktf'
import { ApplicationSynth } from './synth/application-synth'
import { ApplicationSynthStack } from './types/application-synth-stack'
import { InfrastructureRocket } from './rockets/infrastructure-rocket'
import { environmentVarNames } from '@boostercloud/framework-provider-azure'
import { BoosterConfig } from '@boostercloud/framework-types'

export class AzureStack extends TerraformStack {
  readonly applicationStack: ApplicationSynthStack
  readonly defaultApplicationSettings: { [key: string]: string }
  private readonly config: BoosterConfig

  constructor(scope: Construct, name: string, config: BoosterConfig, zipFile?: string) {
    super(scope, name)
    this.config = config
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

    const rocketPackageMapping: { [key: string]: string } = {}
    if (this.config.rockets) {
      for (const rocket of this.config.rockets) {
        const params = rocket.parameters as { rocketProviderPackage: string }
        if (params?.rocketProviderPackage) {
          const basePackage = params.rocketProviderPackage
            .replace(/^@[^/]+\//, '') // Remove scope (@org/)
            .replace(/-[^-]+$/, '') // Remove last segment after dash (provider)
          const functionAppName =
            rockets?.find((r) => r.getFunctionAppName)?.getFunctionAppName?.(this.applicationStack) || ''
          if (functionAppName) {
            rocketPackageMapping[basePackage] = functionAppName
          }
        }
      }
    }

    // Convert mapping to string format: "package1:functionApp1,package2:functionApp2"
    const mappingString = Object.entries(rocketPackageMapping)
      .map(([pkg, func]) => `${pkg}:${func}`)
      .join(';')

    const functionAppNames = rockets
      ? rockets
          .map((rocket: InfrastructureRocket) =>
            rocket.getFunctionAppName ? rocket.getFunctionAppName(this.applicationStack) : ''
          )
          .join(',')
      : ''

    this.applicationStack.functionApp.appSettings = Fn.merge([
      this.defaultApplicationSettings,
      {
        [environmentVarNames.rocketFunctionAppNames]: functionAppNames,
        [environmentVarNames.rocketPackageMapping]: mappingString,
      },
    ])
  }
}
