export class ErrorBase<TName extends string> extends Error {
  constructor(readonly name: TName, readonly message: string, readonly cause?: unknown) {
    super()
  }
}

export abstract class ErrorHandler<TReason extends string> {
  abstract handleError<TError extends ErrorBase<TReason>>(error: TError): Promise<void>
}
