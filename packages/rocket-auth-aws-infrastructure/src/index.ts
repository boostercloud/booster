import { InfrastructureRocket } from '@boostercloud/framework-provider-aws-infrastructure'
import { AuthStack, AWSAuthRocketParams } from './auth-stack'

const AWSAuth = (params: AWSAuthRocketParams): InfrastructureRocket => ({
  mountStack: AuthStack.mountStack.bind(null, params),
  unmountStack: AuthStack.unmountStack?.bind(null, params),
})

export default AWSAuth
