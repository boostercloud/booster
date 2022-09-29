// This file is an experiment to see how can we define arbitrary operations inside
// Booster, so we could run them in a cloud agnostic way.
import { pipe } from 'fp-ts/lib/function'
import { ReaderTaskEither, fromTaskEither } from 'fp-ts/lib/ReaderTaskEither'
import * as RTE from 'fp-ts/lib/ReaderTaskEither'
import * as Arr from 'fp-ts/lib/Array'
import * as TE from 'fp-ts/lib/TaskEither'
import { Lens } from 'monocle-ts'

/**
 * Helper function to wrap an async function that may throw an exception in a ReaderTaskEither
 */
const tryCatch = <Environment, Error, Result>(
  operation: () => Promise<Result>,
  onReject: (error: unknown) => Error
): ReaderTaskEither<Environment, Error, Result> => fromTaskEither(TE.tryCatch(operation, onReject))

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type ValidSDK<T> = { [key in keyof T]: (...arguments_: any[]) => Promise<any> }

type PromiseResultType<T> = T extends Promise<infer R> ? R : never

type BoosterModule<SDK extends ValidSDK<SDK>, ErrorType> = {
  [Key in keyof SDK]: (
    ...arguments_: Parameters<SDK[Key]>
  ) => ReaderTaskEither<SDK, ErrorType, PromiseResultType<SDK[Key]>>
}

export const withField =
  <T, K extends keyof T, V>(property: K, f: (value: T[K]) => V) =>
  (t: T): V =>
    pipe(t, Lens.fromProp<T>()(property).get, f)

export const trace =
  <A>(message: string) =>
  (a: A): RTE.ReaderTaskEither<unknown, never, A> =>
    RTE.fromIO(() => {
      console.log(message, a)
      return a
    })

const buildOperation =
  <SDK extends ValidSDK<SDK>, ErrorType, Result, K extends keyof SDK = keyof SDK>(
    property: K,
    onError: (error: unknown) => ErrorType
  ) =>
  (...parameters: Parameters<SDK[K]>): ReaderTaskEither<SDK, ErrorType, Result> =>
    pipe(
      RTE.asks<SDK, SDK[K]>(Lens.fromProp<SDK>()(property).get),
      RTE.chain((operation: SDK[K]) => tryCatch(() => operation(...parameters), onError))
    )

export const buildModule = <
  SDK extends ValidSDK<SDK>,
  ErrorType,
  Module = BoosterModule<SDK, ErrorType>,
  K extends keyof SDK = keyof SDK
>(
  properties: Array<K>,
  onError: (error: unknown) => ErrorType
): Module =>
  pipe(
    properties,
    Arr.reduce({} as Module, (accumulator, property) => ({
      ...accumulator,
      [property]: buildOperation<SDK, ErrorType, ReturnType<SDK[K]>>(property, onError),
    }))
  )
