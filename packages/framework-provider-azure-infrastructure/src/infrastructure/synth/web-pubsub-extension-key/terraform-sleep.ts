import { TerraformStack } from 'cdktf'
import { toTerraformName } from '../../helper/utils'
import { sleep } from '@cdktf/provider-time'
import { TimeProvider } from '@cdktf/provider-time/lib/provider'
import { ITerraformDependable } from 'cdktf/lib/terraform-dependable'

export class TerraformSleep {
  static build(
    terraformStackResource: TerraformStack,
    appPrefix: string,
    dependsOn: Array<ITerraformDependable>
  ): sleep.Sleep {
    const timeProvider = new TimeProvider(terraformStackResource, 'sleepFeature', {})

    const id = toTerraformName(appPrefix, 'sl')
    return new sleep.Sleep(terraformStackResource, id, {
      createDuration: '5m',
      dependsOn: dependsOn,
      provider: timeProvider,
    })
  }
}
