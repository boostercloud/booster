export class BoosterError extends Error {
  readonly code: string
  constructor(message: string, code?: string, readonly data?: Record<string, unknown>) {
    super(message)
    this.code = code ?? this.constructor.name
  }
}

export class InvalidParameterError extends BoosterError {}
export class InvalidProtocolError extends BoosterError {}
export class NotAuthorizedError extends BoosterError {}
export class NotFoundError extends BoosterError {}
export class InvalidVersionError extends BoosterError {}
export class OptimisticConcurrencyUnexpectedVersionError extends BoosterError {}

export function httpStatusCodeFor(error: Error): number {
  const errorToHTTPCode: Record<string, number> = {
    [InvalidParameterError.name]: 400,
    [InvalidProtocolError.name]: 400,
    [NotAuthorizedError.name]: 401,
    [NotFoundError.name]: 404,
    [InvalidVersionError.name]: 422,
  }

  return errorToHTTPCode[error.constructor.name] ?? 500
}
