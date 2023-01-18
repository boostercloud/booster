import { TerraformStack } from 'cdktf'
import { functionAppFunction } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../../helper/utils'
import { sleep } from '@cdktf/provider-time'
import { TimeProvider } from '@cdktf/provider-time/lib/provider'

export class TerraformSleep {
  static build(
    terraformStackResource: TerraformStack,
    appPrefix: string,
    dummyFunction: functionAppFunction.FunctionAppFunction
  ): sleep.Sleep {
    const timeProvider = new TimeProvider(terraformStackResource, 'sleepFeature', {})

    const id = toTerraformName(appPrefix, 'sl')
    return new sleep.Sleep(terraformStackResource, id, {
      createDuration: '5m',
      dependsOn: [dummyFunction],
      provider: timeProvider,
    })
  }
}
