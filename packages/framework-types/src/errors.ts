export class InvalidParameterError extends Error {}
export class InvalidTransportError extends Error {}
export class NotAuthorizedError extends Error {}
export class NotFoundError extends Error {}
export class InvalidVersionError extends Error {}

export function httpStatusCodeFor(error: Error): number {
  const errorToHTTPCode: Record<string, number> = {
    [InvalidParameterError.name]: 400,
    [InvalidTransportError.name]: 400,
    [NotAuthorizedError.name]: 401,
    [NotFoundError.name]: 404,
    [InvalidVersionError.name]: 422,
  }

  return errorToHTTPCode[error.constructor.name] ?? 500
}
