import { Injectable } from '@boostercloud/framework-core'
import { NodeContext, Runtime } from '@effect/platform-node'
import { Console } from 'effect'

const testCommand = Injectable.command('test', {}, () => Console.log('Hello, world'))

export default {
  commands: [testCommand],
  runMain: Runtime.runMain,
  contextProvider: NodeContext.layer,
} as Injectable.Injectable
