import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import * as fs from 'fs'
import * as archiver from 'archiver'
import * as os from 'os'
import * as FormData from 'form-data'
import { IncomingMessage } from 'http'
import * as path from 'path'
import { scopeLogger } from '../helpers/logger'

/**
 * get cluster namespace from Booster configuration
 */
export function getProjectNamespaceName(configuration: BoosterConfig): string {
  return `booster-${configuration.appName}-${configuration.environmentName}`
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function sleep(ms: number): Promise<any> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * wait for a resource to be ready or reject if not ready after a timeout
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
      throw new Error(errorMessage)
    }
    const nextExecutionDelay = (timeoutMs - elapsed) % tryEveryMs
    await sleep(nextExecutionDelay)
    return doWaitFor()
  }
}

/**
 * create a zip file with the project content
 */
export async function createProjectZipFile(logger: Logger): Promise<string> {
  const l = scopeLogger('createProjectZipFile', logger)
  l.debug('Creating zip archive')
  const output = fs.createWriteStream(path.join(os.tmpdir(), 'boosterCode.zip'))
  const archive = archiver('zip', { zlib: { level: 9 } })
  l.debug('Putting contents into zip file')
  archive.pipe(output)
  archive.glob('**/*')
  await archive.finalize()
  return new Promise((resolve, reject) => {
    output.on('close', () => {
      l.debug('Closed file')
      resolve(output.path.toString())
    })

    output.on('end', () => {
      l.debug('Ended file')
      resolve(output.path.toString())
    })

    archive.on('warning', (err: any) => {
      l.debug('Warning', err.code)
      err.code === 'ENOENT' ? resolve(output.path.toString()) : reject(err)
    })

    archive.on('error', (err: any) => {
      l.debug('ERROR', err)
      reject(err)
    })
  })
}

/**
 * upload file into the cluster using the uploader file service
 */
export async function uploadFile(
  logger: Logger,
  serviceIp: string | undefined,
  filepath: string
): Promise<IncomingMessage> {
  const l = scopeLogger('uploadFile', logger)
  if (!serviceIp) {
    l.debug('serviceIp not provided, throwing')
    throw new Error('Undefined upload service IP, please check the uploadService in your cluster for more information')
  }
  return new Promise((resolve) => {
    l.debug('Creating form data')
    const formData = new FormData()
    l.debug('Appending file stream')
    formData.append('myfile', fs.createReadStream(filepath))
    l.debug('Submitting form')
    formData.submit(`http://${serviceIp}/uploadFile`, (err, res) => {
      if (err) {
        l.debug('Error when submitting, throwing', err)
        throw err
      }
      l.debug('Submission successful')
      resolve(res)
    })
  })
}
