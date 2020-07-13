import { start } from '../../../src/deploy'
import { ChildProcess } from 'child_process'
import { sleep } from '../helpers'

let serverProcess: ChildProcess

before(async () => {
  console.log('starting local server')
  serverProcess = start()
  await sleep(10000) // TODO: We need some time for the server to start, but maybe we can do this faster using the `waitForIt` method developed for waiting for AWS resources.
})

after(async () => {
  console.log('stopping local server')
  serverProcess.kill('SIGINT')
})
