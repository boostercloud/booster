import { BoosterConfig } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import * as fs from 'fs'
import archiver from 'archiver'
import * as os from 'os'
import FormData from 'form-data'
import { IncomingMessage } from 'http'
import * as path from 'path'

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
export async function createProjectZipFile(config: BoosterConfig): Promise<string> {
  const logger = getLogger(config, 'utils#createProjectZipFile')
  logger.debug('Creating zip archive')
  const output = fs.createWriteStream(path.join(os.tmpdir(), 'boosterCode.zip'))
  const archive = archiver('zip', { zlib: { level: 9 } })
  logger.debug('Putting contents into zip file')
  archive.pipe(output)
  archive.directory('.deploy', false)
  await archive.finalize()
  return new Promise((resolve, reject) => {
    output.on('close', () => {
      logger.debug('Closed file')
      resolve(output.path.toString())
    })

    output.on('end', () => {
      logger.debug('Ended file')
      resolve(output.path.toString())
    })

    archive.on('warning', (err: Error & { code: string }) => {
      logger.debug('Warning', err.code)
      err.code === 'ENOENT' ? resolve(output.path.toString()) : reject(err)
    })

    archive.on('error', (err: Error) => {
      logger.debug('ERROR', err)
      reject(err)
    })
  })
}

/**
 * upload file into the cluster using the uploader file service
 */
export async function uploadFile(
  config: BoosterConfig,
  serviceIp: string | undefined,
  filepath: string
): Promise<IncomingMessage> {
  const logger = getLogger(config, 'utils#uploadFile')
  if (!serviceIp) {
    logger.debug('serviceIp not provided, throwing')
    throw new Error('Undefined upload service IP, please check the uploadService in your cluster for more information')
  }
  return new Promise((resolve) => {
    logger.debug('Creating form data')
    const formData = new FormData()
    logger.debug('Appending file stream')
    formData.append('myfile', fs.createReadStream(filepath))
    logger.debug('Submitting form')
    formData.submit(`http://${serviceIp}/uploadFile`, (err, res) => {
      if (err) {
        logger.debug('Error when submitting, throwing', err)
        throw err
      }
      logger.debug('Submission successful')
      resolve(res)
    })
  })
}
