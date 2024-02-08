import * as GenerateConfig from '@boostercloud/framework-core/dist/nexus/generate-config'
import { Nexus } from '@boostercloud/framework-types/dist/components'
import { NodeContext, Runtime } from '@effect/platform-node'

export default {
  commands: [GenerateConfig.command],
  runMain: Runtime.runMain,
  contextProvider: NodeContext.layer,
} as Nexus
