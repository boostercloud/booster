import { BoosterConfig } from '@boostercloud/framework-types'
import * as fs from 'fs'
import * as archiver from 'archiver'
import * as os from 'os'
import * as FormData from 'form-data'
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
export async function createProjectZipFile(): Promise<string> {
  const output = fs.createWriteStream(path.join(os.tmpdir(), 'boosterCode.zip'))
  const archive = archiver('zip', { zlib: { level: 9 } })
  archive.pipe(output)
  archive.glob('**/*')
  await archive.finalize()
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
 */
export async function uploadFile(serviceIp: string | undefined, filepath: string): Promise<IncomingMessage> {
  if (!serviceIp) {
    throw new Error('Undefined upload service IP, please check the uploadService in your cluster for more information')
  }
  return new Promise((resolve) => {
    const formData = new FormData()
    formData.append('myfile', fs.createReadStream(filepath))
    formData.submit(`http://${serviceIp}/uploadFile`, (err, res) => {
      if (err) {
        throw err
      }
      resolve(res)
    })
  })
}
