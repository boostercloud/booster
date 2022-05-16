import { Construct } from 'constructs'
import { TerraformStack } from 'cdktf'
import { ApplicationSynth } from './synth/application-synth'
import { ApplicationSynthStack } from './types/application-synth-stack'

export class AzureStack extends TerraformStack {
  readonly applicationStack: ApplicationSynthStack

  constructor(scope: Construct, name: string) {
    super(scope, name)

    const applicationSynth = new ApplicationSynth(this)
    this.applicationStack = applicationSynth.synth()
  }
}
