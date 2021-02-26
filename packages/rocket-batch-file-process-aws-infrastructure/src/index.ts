import { InfrastructureRocket } from '@boostercloud/framework-provider-aws-infrastructure'
import { BatchFileProcessingStack, AWSBatchProcessingFilesParams } from './batch-file-processing'

const AWSBatchProcessingFiles = (params: AWSBatchProcessingFilesParams): InfrastructureRocket => ({
  mountStack: BatchFileProcessingStack.mountStack.bind(null, params),
  unmountStack: BatchFileProcessingStack.unmountStack.bind(null, params),
})

export default AWSBatchProcessingFiles
