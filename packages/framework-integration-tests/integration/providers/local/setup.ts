import { start } from '../../../src/deploy'
import { ChildProcess } from 'child_process'
import { sleep } from '../helpers'

let serverProcess: ChildProcess

before(async () => {
  console.log('starting local server')
  serverProcess = start()
  await sleep(5000) // Give some time for the server to start
})

after(async () => {
  console.log('stopping local server')
  serverProcess.kill('SIGINT')
})
