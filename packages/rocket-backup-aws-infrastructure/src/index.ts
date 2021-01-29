import { BackupStackParams, BackupStack } from './backup-stack'
import { InfrastructureRocket } from '@boostercloud/framework-provider-aws-infrastructure'

const AWSBackup = (params: BackupStackParams): InfrastructureRocket => ({
  mountStack: BackupStack.mountStack.bind(null, params),
  unmountStack: BackupStack.unmountStack.bind(null, params),
})

export default AWSBackup
