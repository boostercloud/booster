import { httpStatusCodeFor, toClassTitle } from '@boostercloud/framework-types'
import * as express from 'express'

export enum HttpCodes {
  Ok = 200,
  BadRequest = 400,
  NotAuthorized = 403,
  InternalError = 500,
}

export async function requestFailed(error: Error, res: express.Response): Promise<void> {
  const statusCode = httpStatusCodeFor(error)
  res.status(statusCode).json({
    title: toClassTitle(error),
    reason: error.message,
  })
}
