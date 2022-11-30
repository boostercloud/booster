/**
 * effect-ts reexports
 * ================
 *
 * Module to work with the layered architecture of some Booster parts.
 *
 * This module reexports the minimum types and functions from the `effect-ts` library to
 * be able to work effectively throughout the codebase.
 *
 * Keep reading these comments to get a grasp on what does all the stuff mean and how to
 * work with these concepts.
 *
 *
 *
 * What is effect-ts for?
 * ----------------------
 *
 * `effect-ts` is a TypeScript port of the ZIO library, used for reactive programming in Scala.
 * It is used in production, and has proven to reduce complexity in many project. Currently we're using the
 * stable version of it, which lacks features, but has been very well tested (packages under `@effect-ts`).
 *
 * It is an alternative runtime to Promise, fixing many issues found in any enterprise grade project in a
 * stateless and well tested way (doesn't reinvent the wheel, but rather uses well proven abstractions to
 * build the runtime and utilities).
 *
 * It eases many different challenges, and introduces improvements like:
 *
 * - Better performance times than Promise
 * - Explicit error tracking
 * - Recovery mechanisms
 * - Dependency injection
 * - Better debugging
 * - Opens the possibility of remote debugging
 * - OpenTelemetry integration
 * - Better modularization of the project, opening the door to split the framework into rockets and allow users
 *   to write rockets without infra knowledge
 * - More testability, not having to rely on replacing functions in libraries
 * - Better and easier concurrency management
 *
 *
 *
 * Have we tried alternative approaches that do not require introducing something as complex as this library?
 * ----------------------------------------------------------------------------------------------------------
 *
 * Yes, and it rendered to be much more complex, where not only we started reinventing the wheel that has been
 * solved by existing abstractions used in reactive programming, but also it introduced complex "side-quests"
 * like having to track and maintaining a service graph, or creating our own workflow library for command line
 * commands.
 *
 * Many approaches not only have been tried and introduced, but also there are some remains in the codebase,
 * which make the code much more complex than we initially wanted.
 *
 *
 *
 * How can I start learning?
 * -------------------------
 *
 * First of all, keep reading! After you've read everything here, if you've got any more questions, please go to
 * the #development channel in our Discord server and ask there!
 *
 * Takeaway: `effect-ts` is a library that allows you to write code in a declarative way, opening the door to
 * innovations and improvements in the future.
 */

/**
 * Type: Either
 * ============
 *
 * This type is not reexported because it is not necessary for the usage of effect, but it is useful that you
 * know that it exists and how it works. It is defined like:
 *
 * type Either<TError, TResult> =
 *   | { _tag: 'Left', value: TError }
 *   | { _tag: 'Right', value: TResult}
 *
 * This means that an object of type Either could either be an object with the _tag field set to 'Left', and the value to be of type 'TError',
 * or an object with the _tag field set to 'Right', and the value to be of type 'TResult'.
 *
 * In some programming languages it is called Result. Effect-ts uses the name Either, because it is more generic and it is not tied to
 * represent a result of a computation.
 *
 * This is useful to represent when a function could return an error that we want to recover from, or a result that we want to use. Instead of
 * throwing an error, we can return an Either with the error, and then recover from it. This is useful because the recovery logic can be completely
 * handled by ourselves, and design the error recovery flow as we want, instead of catching and rethrowing errors.
 *
 * Takeaway: `Either` allows representing computations that can fail explicitly, ensuring that the caller of the function handles the error case.
 */

/**
 * Type: Lazy
 * ==========
 *
 * This type is not reexported because it is not necessary for the usage of effect, but it is useful that you
 * know that it exists and how it works. It is defined like:
 *
 * type Lazy<T> = () => T
 *
 * This means that an object of type Lazy is a function that returns a value of type T. It is useful to represent
 * a value that is not computed until it is needed, and it is useful to represent a value that is computed lazily.
 *
 * For example, if we have a function that returns a value of type T, but it is expensive to compute, we can
 * represent it as a Lazy<T>, and then only compute it when it is needed.
 *
 * Effect-ts uses Lazy under the hoods to ensure performance and allow for better concurrency management.
 *
 * Takeaway: `Lazy` allows representing a value that is not computed until it is needed.
 */

/**
 * Type: Effect
 * ============
 *
 * The `Effect` type is the foundation of the `effect-ts` library. It is a type that represents a computation that
 * has some kind of dependency, can fail, and that can be interrupted. It is defined like:
 *
 * type Effect<TEnv, TErr, TRes> = (dependencies: TEnv) => Lazy<Promise<Either<TErr, TRes>>>
 *
 * It essentially is a function that accepts an object of dependencies, and returns a function that returns a Promise
 * that can either return an error or a result. It is generic so you can define the dependencies, the error, and the result.
 *
 * Effect-ts calls dependencies "environment", and it is a way to represent the dependencies of a computation. It is useful
 * because it allows us to define the dependencies of a program in a single place, and then pass them at the top level of the
 * application. It is also useful for unit testing, because we can pass a mocked environment to the computation, and then
 * assert that the computation returns the expected result.
 *
 * Dependencies in Effect-ts are represented as a tree, ensuring that there are no duplicate service instances, and that
 * the dependencies are always resolved in the same order. This is useful because it allows us to have a single source of
 * truth for the dependencies of the application.
 *
 * Takeaway: `Effect` allows representing an asynchronous computation that can fail, and that has some dependencies.
 */
export { Effect } from '@effect-ts/core'

import { pipe } from '@effect-ts/core'
import { Layer } from '@effect-ts/core/Effect/Layer'
import { Effect, provideSomeLayer, runPromise } from '@effect-ts/core/Effect'
import { Has } from '@effect-ts/core/Has'

export * as Layer from '@effect-ts/core/Effect/Layer'
export * as Ref from '@effect-ts/core/Effect/Ref'
export * as Dictionary from '@effect-ts/core/Collections/Immutable/Dictionary'
export * as Array from '@effect-ts/core/Collections/Immutable/Array'
export * as Tuple from '@effect-ts/core/Collections/Immutable/Tuple'
export { Has, Tag, tag } from '@effect-ts/core/Has'
export { runMain } from '@effect-ts/node/Runtime'
export { pipe, flow } from '@effect-ts/core/Function'

type RunWithLayerOpts<R> = {
  readonly layer: Layer<unknown, never, Has<R>>
  readonly onError: (eff: Effect<Has<R>, unknown, void>) => Effect<Has<R>, never, void>
}

/**
 * Run an effect with a layer, and handle errors
 * @param effect The effect to run
 * @param opts.layer The layer to provide all the services
 * @param opts.onError The function to handle errors
 * @returns void
 */
export const unsafeRunEffect = <R>(
  effect: Effect<Has<R>, unknown, void>,
  { layer, onError }: RunWithLayerOpts<R>
): Promise<void> => {
  return pipe(effect, onError, provideSomeLayer(layer), runPromise)
}
