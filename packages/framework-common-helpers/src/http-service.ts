import * as https from 'https'
import { RequestOptions } from 'https'
import * as http from 'http'
import { IncomingMessage } from 'node:http'

export interface PostConfiguration {
  contentType?: string
  timeout?: number
  acceptedStatusCodes?: number[] // Additional status codes to accept as valid responses
}

export interface PostResult {
  status: number
  body: unknown
}

export async function request(
  url: string,
  method: 'GET' | 'POST' = 'GET',
  data = '',
  config: PostConfiguration = {}
): Promise<PostResult> {
  const { contentType = 'application/json', timeout = 10000, acceptedStatusCodes = [] } = config
  const options: RequestOptions = {
    method: method,
    headers: {
      'Content-Type': contentType,
    },
    timeout: timeout,
  }
  if (data) {
    options.headers!['Content-Length'] = data.length
  }

  return new Promise((resolve, reject) => {
    const method = url.startsWith('https') ? https.request : http.request
    const request = method(url, options, (res: IncomingMessage) => {
      const body: Array<any> = []
      res.on('data', (chunk) => body.push(chunk))
      res.on('end', () => {
        if (!res?.statusCode) {
          return reject(new Error('Unknown HTTP status code'))
        }
        // Accept 2xx codes or any explicitly accepted status codes
        if ((res.statusCode < 200 || res.statusCode > 299) && !acceptedStatusCodes.includes(res.statusCode)) {
          return reject(new Error(`HTTP status code ${res.statusCode}`))
        }

        const buffer = Buffer.concat(body).toString()
        resolve({
          status: res?.statusCode,
          body: buffer,
        })
      })
    })

    request.on('error', (err) => {
      reject(err)
    })

    request.on('timeout', () => {
      request.destroy()
      reject(new Error('Request time out'))
    })

    request.write(data)
    request.end()
  })
}
