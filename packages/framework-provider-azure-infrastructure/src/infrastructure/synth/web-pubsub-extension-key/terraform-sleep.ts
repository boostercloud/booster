import { TerraformStack } from 'cdktn'
import { toTerraformName } from '../../helper/utils'
import { sleep } from '@cdktn/provider-time'
import { TimeProvider } from '@cdktn/provider-time/lib/provider'
import { ITerraformDependable } from 'cdktn/lib/terraform-dependable'

export class TerraformSleep {
  static build(
    terraformStackResource: TerraformStack,
    appPrefix: string,
    dependsOn: Array<ITerraformDependable>
  ): sleep.Sleep {
    const timeProvider = new TimeProvider(terraformStackResource, 'sleepFeature', {})

    const id = toTerraformName(appPrefix, 'sl')
    return new sleep.Sleep(terraformStackResource, id, {
      createDuration: '3m',
      dependsOn: dependsOn,
      provider: timeProvider,
    })
  }
}
