import { BoosterConfig } from '@boostercloud/framework-types'
import fs = require('fs')
import archiver = require('archiver')
import os = require('os')
import FormData = require('form-data')
import { IncomingMessage } from 'http'
import { indexTemplate } from './templates/indexTemplate'
import path = require('path')
const util = require('util')
const writeFile = util.promisify(require('fs').writeFile)

export function getProjectNamespaceName(configuration: BoosterConfig): string {
  return `booster-${configuration.appName}-${configuration.environmentName}`
}

// eslint-disable-next-line @typescript-eslint/explicit-function-return-type
export function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function waitForIt<TResult>(
  tryFunction: () => Promise<TResult>,
  checkResult: (result: TResult) => boolean,
  errorMessage: string,
  timeoutMs = 180000,
  tryEveryMs = 1000
): Promise<TResult> {
  const start = Date.now()
  return doWaitFor()

  async function doWaitFor(): Promise<TResult> {
    const res = await tryFunction()
    const expectedResult = checkResult(res)
    if (expectedResult) {
      return res
    }
    const elapsed = Date.now() - start

    if (elapsed > timeoutMs) {
      return Promise.reject(errorMessage)
    }
    const nextExecutionDelay = (timeoutMs - elapsed) % tryEveryMs
    await sleep(nextExecutionDelay)
    return doWaitFor()
  }
}

export async function createIndexFile(): Promise<string> {
  const outFile = path.join(os.tmpdir(), 'index.js')
  writeFile(outFile, indexTemplate).catch(() => {
    return Promise.reject('Unable to create the index file for your app')
  })
  return outFile
}

export function createProjectZipFile(): Promise<string> {
  const output = fs.createWriteStream(path.join(os.tmpdir(), 'boosterCode.zip'))
  const archive = archiver('zip', { zlib: { level: 9 } })
  archive.pipe(output)
  archive.glob('**/*')
  // eslint-disable-next-line @typescript-eslint/no-floating-promises
  archive.finalize()
  return new Promise((resolve, reject) => {
    output.on('close', () => {
      resolve(output.path.toString())
    })

    output.on('end', () => {
      resolve()
    })

    archive.on('warning', (err: any) => {
      if (err.code === 'ENOENT') {
        resolve()
      } else {
        reject(err)
      }
    })

    archive.on('error', (err: any) => {
      reject(err)
    })
  })
}

export async function uploadFile(serviceIp: string, filepath: string): Promise<IncomingMessage> {
  return new Promise((resolve, reject) => {
    const formData = new FormData()
    formData.append('myfile', fs.createReadStream(filepath))
    formData.submit(`http://${serviceIp}/uploadFile`, (err, res) => {
      if (err) {
        reject(err)
      }
      resolve(res)
    })
  })
}
