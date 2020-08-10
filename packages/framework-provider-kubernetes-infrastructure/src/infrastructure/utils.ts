import { BoosterConfig } from '@boostercloud/framework-types'
import * as fs from 'fs'
import * as archiver from 'archiver'
import * as os from 'os'
import FormData = require('form-data')
import { IncomingMessage } from 'http'
import { indexTemplate } from './templates/indexTemplate'
import path = require('path')
import * as util from 'util'
const writeFile = util.promisify(fs.writeFile)

/**
 * get cluster namespace from Booster configuration
 *
 * @export
 * @param {BoosterConfig} configuration
 * @returns {string}
 */
export function getProjectNamespaceName(configuration: BoosterConfig): string {
  return `booster-${configuration.appName}-${configuration.environmentName}`
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function sleep(ms: number): Promise<any> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * wait for a resource to be ready or reject if not ready after a timeout
 *
 * @export
 * @template TResult
 * @param {() => Promise<TResult>} tryFunction
 * @param {(result: TResult) => boolean} checkResult
 * @param {string} errorMessage
 * @param {number} [timeoutMs=180000]
 * @param {number} [tryEveryMs=1000]
 * @returns {Promise<TResult>}
 */
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

/**
 * create index.js file based on template
 *
 * @export
 * @returns {Promise<string>}
 */
export async function createIndexFile(): Promise<string> {
  const outFile = path.join(os.tmpdir(), 'index.js')
  writeFile(outFile, indexTemplate).catch(() => {
    return Promise.reject('Unable to create the index file for your app')
  })
  return outFile
}

/**
 * create a zip file with the project content
 *
 * @export
 * @returns {Promise<string>}
 */
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
      err.code === 'ENOENT' ? resolve() : reject(err)
    })

    archive.on('error', (err: any) => {
      reject(err)
    })
  })
}

/**
 * upload file into the cluster using the uploader file service
 *
 * @export
 * @param {string} serviceIp
 * @param {string} filepath
 * @returns {Promise<IncomingMessage>}
 */
export async function uploadFile(serviceIp: string | undefined, filepath: string): Promise<IncomingMessage> {
  if (!serviceIp) {
    throw new Error('Undefined upload service IP, please check the uploadService in your cluster for more information')
  }
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
