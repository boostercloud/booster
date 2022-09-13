import { httpStatusCodeFor, toClassTitle } from '@boostercloud/framework-types'
import * as express from 'express'

export enum HttpCodes {
  Ok = 200,
  BadRequest = 400,
  NotAuthorized = 403,
  InternalError = 500,
}

export async function requestFailed(error: Error, response: express.Response): Promise<void> {
  const statusCode = httpStatusCodeFor(error)
  response.status(statusCode).json({
    title: toClassTitle(error),
    reason: error.message,
  })
}
