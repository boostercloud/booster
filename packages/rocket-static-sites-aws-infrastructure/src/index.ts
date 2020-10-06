import { AWSStaticSiteParams, StaticWebsiteStack } from './static-website-stack'
import { InfrastructurePlugin } from '@boostercloud/framework-provider-aws-infrastructure'

const AWSStaticSite = (params: AWSStaticSiteParams): InfrastructurePlugin => ({
  mountStack: StaticWebsiteStack.mountStack.bind(null, params),
})

export default AWSStaticSite
