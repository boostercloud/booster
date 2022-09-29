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
const tryCatch = <Env, Error, Result>(
  operation: () => Promise<Result>,
  onReject: (error: unknown) => Error
): ReaderTaskEither<Env, Error, Result> => fromTaskEither(TE.tryCatch(operation, onReject))

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type ValidSDK<T> = { [key in keyof T]: (...args: any[]) => Promise<any> }

type PromiseResultType<T> = T extends Promise<infer R> ? R : never

type BoosterModule<SDK extends ValidSDK<SDK>, ErrorType> = {
  [Key in keyof SDK]: (...args: Parameters<SDK[Key]>) => ReaderTaskEither<SDK, ErrorType, PromiseResultType<SDK[Key]>>
}

export const withField =
  <T, K extends keyof T, V>(prop: K, f: (value: T[K]) => V) =>
  (t: T): V =>
    pipe(t, Lens.fromProp<T>()(prop).get, f)

export const trace =
  <A>(message: string) =>
  (a: A): RTE.ReaderTaskEither<unknown, never, A> =>
    RTE.fromIO(() => {
      console.log(message, a)
      return a
    })

const buildOperation =
  <SDK extends ValidSDK<SDK>, ErrorType, Result, K extends keyof SDK = keyof SDK>(
    prop: K,
    onError: (err: unknown) => ErrorType
  ) =>
  (...params: Parameters<SDK[K]>): ReaderTaskEither<SDK, ErrorType, Result> =>
    pipe(
      RTE.asks<SDK, SDK[K]>(Lens.fromProp<SDK>()(prop).get),
      RTE.chain((operation: SDK[K]) => tryCatch(() => operation(...params), onError))
    )

export const buildModule = <
  SDK extends ValidSDK<SDK>,
  ErrorType,
  Module = BoosterModule<SDK, ErrorType>,
  K extends keyof SDK = keyof SDK
>(
  props: Array<K>,
  onError: (err: unknown) => ErrorType
): Module =>
  pipe(
    props,
    Arr.reduce({} as Module, (acc, prop) => ({
      ...acc,
      [prop]: buildOperation<SDK, ErrorType, ReturnType<SDK[K]>>(prop, onError),
    }))
  )
