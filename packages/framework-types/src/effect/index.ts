/**
 * effect-ts reexports
 * ===================
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
 */

/*************************************************
 *                                               *
 *             INTRODUCTION                      *
 *                                               *
 *************************************************/

/* What is effect-ts for?
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

/*************************************************
 *                                               *
 *             THE EFFECT TYPE                   *
 *                                               *
 *************************************************/

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
 * Takeaway: `Effect` is like a `Promise` but with dependencies, error handling, and better concurrency management.
 */
export { Effect } from '@effect-ts/core'

/*************************************************
 *                                               *
 *             USING/CREATING EFFECTS            *
 *                                               *
 *************************************************/

/**
 * Function: gen
 * =============
 *
 * In order to be able to write code in a familiar way, like the async/await syntax that
 * the promises API provides, Effect-ts provides a function that emulates this syntax.
 *
 * Instead of writing an async function, you call the `gen` function, and then you
 * write a generator function that receives a single parameter, called adapter
 * (usually named `$`), and returns an effect.
 *
 * In order to "await" effects, instead of using the `await` keyword, you use the generator
 * function syntax, together with the adapter parameter.
 *
 * As an example, consider this code written using promises:
 *
 * // Here bar and baz are promises
 * const foo = async function() {
 *   const a = await bar()
 *   const b = await baz()
 *   return a + b
 * }
 *
 * The equivalent using the `gen` function would be:
 *
 * // Here bar and baz are effects
 * const foo = gen(function* ($) {
 *  const a = yield* $(bar())
 *  const b = yield* $(baz())
 *  return a + b
 * })
 *
 * It can look like an inconvenience to have to use the generator function syntax, but it
 * is a trade off that allows us to write code in a familiar way, and also allows us to
 * propagate errors, benefit from dependency injection, and all the other features that
 * effect-ts provides. E.g. if `bar` requires some dependency, `foo` will already have
 * it added to it's type signature, so the compiler will tell you if you forgot to add
 * it to the environment.
 *
 * Still, in the new versions of effect-ts, the API has greatly improved, and there are
 * already features that simplify the code even more. Yet, we will wait until the new
 * version is stable before we start using it.
 *
 * Takeaway: Use `gen` instead of `async` and use `yield* $(...)` instead of `await`.
 */
export { gen } from '@effect-ts/core/Effect'

/**
 * Function: succeedWith
 * =====================
 *
 * In order to create an effect that returns a value ran from a side effect (e.g.
 * by calling console.log, which runs a side effect, but doesn't return a promise).
 *
 * NOTE: The function being wrapped MUST NEVER THROW. If it does, the error will be
 * swallowed and the effect will never fail. If the function can throw, use `tryCatch`
 * instead (see next).
 *
 * You pass a function that runs what you need to run, and succeedWith will return
 * an effect that will run the function, and then return the result.
 *
 * Example:
 *
 * const log = succeedWith(() => console.log('hello world'))
 *
 * In this example, the effect `log` will run the side effect of calling console.log,
 * and then return the result of the call, which is undefined.
 *
 * Takeaway: Use `succeedWith` to wrap effectful code that doesn't return a promise.
 */
export { succeedWith } from '@effect-ts/core/Effect'

/**
 * Function: tryCatch
 * ==================
 *
 * If a synchronous, effectful, function can throw, you can use `tryCatch` to wrap it
 * in an effect. It is similar to `succeedWith`, but it will catch any errors thrown
 * by the function, and return them as an error in the effect.
 *
 * Example:
 *
 * const readFile = tryCatch(() => fs.readFileSync('foo.txt', 'utf8'), (e) => new Error(e))
 *
 * In this example, the effect `readFile` will run the side effect of reading the file
 * foo.txt, and then return the result of the call, which is a string. If the file
 * doesn't exist, or there is a permission error, the effect will fail with an error
 * of type `Error`, meaning that the return type is `Effect<unknown, Error, string>`,
 * as we have returned `Error` as the error type, and the return type of the function
 * is `string`.
 *
 * Takeaway: Use `tryCatch` to wrap effectful code that can throw.
 */
export { tryCatch } from '@effect-ts/core/Effect'

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
