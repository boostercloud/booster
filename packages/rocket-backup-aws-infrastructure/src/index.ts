import { BackupStack } from './backup-stack'
import { InfrastructureRocket } from '@boostercloud/framework-provider-aws-infrastructure'
import { BackupStackParams } from './utils/types'
import { BoosterConfig } from '@boostercloud/framework-types'

const AWSBackup = (params: BackupStackParams, config: BoosterConfig): InfrastructureRocket => ({
  mountStack: BackupStack.mountStack.bind(null, params, config),
  unmountStack: BackupStack.unmountStack.bind(null, params),
})

export default AWSBackup
