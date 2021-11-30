import { Construct } from 'constructs'
import { TerraformStack } from 'cdktf'
import { ApplicationSynth } from './synth/application-synth'

export class AzureStack extends TerraformStack {
  constructor(scope: Construct, name: string) {
    super(scope, name)

    const applicationSynth = new ApplicationSynth()
    void applicationSynth.synth(this)
  }
}
