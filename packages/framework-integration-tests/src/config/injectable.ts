import { Injectable } from '@boostercloud/framework-core'
import { Console } from 'effect'

const testCommand = Injectable.command('test', {}, () => Console.log('Hello, world'))

export default {
  commands: [testCommand],
} as Injectable.Injectable
