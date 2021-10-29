import { Rocket } from '@boostercloud/rockets-base'
import { ProviderContext } from '@boostercloud/framework-types'

interface TestRocketParams {
  parameter: string
}

const TestRocket = (params: TestRocketParams): Rocket<ProviderContext> => ({
  mount: mountRocket.bind(null, params),
  unmount: unmountRocket.bind(null, params),
})

async function mountRocket(params: TestRocketParams, context: ProviderContext): Promise<void> {
  context.logger.info('mounting test rocket in context ' + context.name)
}

async function unmountRocket(params: TestRocketParams, context: ProviderContext): Promise<void> {
  context.logger.info('unmounting test rocket in context ' + context.name)
}

export default TestRocket
