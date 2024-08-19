import { EntityInterface, EventInterface } from './concepts'

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
export class BoosterTokenExpiredError extends BoosterError {}
export class BoosterTokenNotBeforeError extends BoosterError {}
export class NotFoundError extends BoosterError {}
export class InvalidVersionError extends BoosterError {}
export class OptimisticConcurrencyUnexpectedVersionError extends BoosterError {}

export class InvalidEventError extends BoosterError {}
export class InvalidReducerError extends BoosterError {
  readonly eventInstance: EventInterface
  readonly snapshotInstance: EntityInterface | null

  constructor(message: string, eventInstance: EventInterface, snapshotInstance: EntityInterface | null) {
    super(message)
    this.eventInstance = eventInstance
    this.snapshotInstance = snapshotInstance
  }
}

export function httpStatusCodeFor(error: Error): number {
  const errorToHTTPCode: Record<string, number> = {
    [InvalidParameterError.name]: 400,
    [InvalidProtocolError.name]: 400,
    [NotAuthorizedError.name]: 401,
    [BoosterTokenExpiredError.name]: 401,
    [BoosterTokenNotBeforeError.name]: 401,
    [NotFoundError.name]: 404,
    [InvalidVersionError.name]: 422,
  }

  return errorToHTTPCode[error.constructor.name] ?? 500
}
