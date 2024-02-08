import { Nexus } from '@boostercloud/framework-core'
import { NodeContext, Runtime } from '@effect/platform-node'
import { Console } from 'effect'

const testCommand = Nexus.command('test', {}, () => Console.log('Hello, world'))

export default {
  commands: [testCommand],
  runMain: Runtime.runMain,
  contextProvider: NodeContext.layer,
} as Nexus.Nexus
