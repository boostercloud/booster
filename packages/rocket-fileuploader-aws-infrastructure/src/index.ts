import { AWSS3EventsLambdaParams, S3NotificationEventStoreStack } from './s3-notification-event-store'
import { InfrastructureRocket } from '@boostercloud/framework-provider-aws-infrastructure'

const AWSS3EventsLambda = (params: AWSS3EventsLambdaParams): InfrastructureRocket => ({
  mountStack: S3NotificationEventStoreStack.mountStack.bind(null, params),
  unmountStack: S3NotificationEventStoreStack.unmountStack.bind(null, params),
})

export default AWSS3EventsLambda
