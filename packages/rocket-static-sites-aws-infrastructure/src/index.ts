import { AWSStaticSiteParams, StaticWebsiteStack } from './static-website-stack'
import { InfrastructureRocket } from '@boostercloud/framework-provider-aws-infrastructure'

const AWSStaticSite = (params: AWSStaticSiteParams): InfrastructureRocket => ({
  mountStack: StaticWebsiteStack.mountStack.bind(null, params),
  unmountStack: StaticWebsiteStack.unmountStack.bind(null, params),
})

export default AWSStaticSite
