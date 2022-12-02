/**
 * effect-ts reexports
 * ===================
 *
 * Module to work with the layered architecture of some Booster parts.
 *
 * This module reexports the minimum types and functions from the `effect-ts` library to
 * be able to work effectively throughout the codebase.
 *
 * Keep reading these comments to get a grasp on what all the stuff means and how to
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
 * It is used in production and has proven to reduce complexity in many projects. Currently, we're using the
 * stable version of it, which lacks features, but has been very well tested (packages under `@effect-ts`).
 *
 * It is an alternative runtime to Promise, fixing many issues found in any enterprise-grade project in a
 * stateless and well-tested way (doesn't reinvent the wheel, but rather uses well-proven abstractions to
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
export type { Effect } from '@effect-ts/core/Effect'

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
 * Function: succeed
 * =================
 *
 * If you want to return a pure value from an effect, you can use the `succeed` function.
 * It is equivalent to `Promise.resolve`.
 *
 * NOTE: You should never use `succeed` to perform side effects. If you want to perform
 * side effects, use the `succeedWith` or `tryCatch` functions (see next).
 *
 * This function is useful when you want to return a value from an effect, but you don't
 * want to do any computation.
 *
 * Example:
 *
 * const foo = succeed(1)
 *
 * Here, `foo` is an effect that returns the value 1.
 *
 * Takeaway: Use `succeed` to return a pure value from an effect.
 */
export { succeed } from '@effect-ts/core/Effect'

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
 * const readFile = tryCatch(() => fs.readFileSync('foo.txt'), (e) => new Error(e))
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

/**
 * Function: tryPromise
 * =================
 *
 * If you have a promise, and you want to convert it to an effect, you can use the
 * `tryPromise` function. Any kind of error thrown by the promise will be converted to
 * an unknown error in the effect that you will have to handle. This is not very useful
 * as you want to provide as much information as possible, and the best place to do so
 * is where the error gets generated.
 *
 * Still, there are times where you want to ignore the error and do something else, and
 * in those cases, this function is useful.
 *
 * This function is analogous to `succeed`, but for promises. Ideally, don't use it to
 * wrap promises that could fail, because you will lose the ability to add info about
 * the error. Instead use `tryCatchPromise` (see next).
 *
 * Example:
 *
 * const readFile = tryPromise(() => fs.readFile('foo.txt'))
 *
 * In this example, the effect `readFile` will run the side effect of reading the file
 * foo.txt, and then return the result of the call, which is a string. If the file
 * doesn't exist, or there is a permission error, the effect will fail with an unknown
 * error.
 *
 * Takeaway: Avoid using `tryPromise`, but if you do, use it to wrap promises where
 * you want to ignore the error.
 */
export { tryPromise } from '@effect-ts/core/Effect'

/**
 * Function: tryCatchPromise
 * =========================
 *
 * If you have a promise, and you want to convert it to an effect, you can use the
 * `tryCatchPromise` function. Any kind of error thrown by the promise will caught
 * by the function in the second argument, and converted to an error in the effect.
 *
 * This function is analogous to `tryCatch`, but for promises. Ideally, use it to
 * wrap promises that could fail, because you will be able to add info about the error.
 *
 * Example:
 *
 * const readFile = tryCatchPromise(
 *  () => fs.readFile('foo.txt'),
 *  (e) => new Error('Error reading file: ' + e)
 * )
 *
 * In this example, the effect `readFile` will run the side effect of reading the file
 * foo.txt, and then return the result of the call, which is a string. If the file
 * doesn't exist, or there is a permission error, the effect will fail with an error
 * of type `Error`, meaning that the return type is `Effect<unknown, Error, string>`.
 *
 * Takeaway: Use `tryCatchPromise` to wrap promises, as it forces you to handle errors.
 */
export { tryCatchPromise } from '@effect-ts/core/Effect'

/*************************************************
 *                                               *
 *             EFFECT-TS AS A STDLIB             *
 *                                               *
 *************************************************/

/*
 * Effect-TS comes also with a set of helper functions that are useful to build
 * any kind of application. They are not specific to any domain, and they are
 * not specific to any framework. They are just useful functions that are usually
 * provided by libraries like lodash.
 *
 * To avoid overwhelming you with too many functions, we provide the minimum to work
 * with functions, and a couple of collections like Array, Tuple, and Dictionary.
 */

/**
 * Function: pipe
 * ==============
 *
 * The pipe function is a way to compose functions in a readable way. It is similar
 * to the pipe operator in F#, Elixir, Hack, Elm, and others. It is a way of avoiding
 * nesting functions, usually called the pyramid of doom.
 *
 * You can think of it as the pipe operator in Unix, where you can pipe the output
 * of one command to the input of another command. In this case, the output of one
 * function is the input of another function.
 *
 * Example:
 *
 * const addOne = (n: number) => n + 1
 * const multiplyByTwo = (n: number) => n * 2
 *
 * const result = pipe(
 *   1,
 *   addOne,
 *   multiplyByTwo
 * )
 *
 * In this example, the result will be 4, because we are adding 1 to 1, and then
 * multiplying the result by 2. The functions are executed in order, from left to
 * right (or in this case from top to bottom due to line breaks), which is more
 * consistent with the way we read code.
 *
 * The only limitation is that the functions must be unary, meaning that they must
 * take only one argument. If you need to pass more than one argument, you can make
 * the function return a function that takes the next argument, and so on. This is
 * called currying:
 *
 * Instead of:
 *
 * const add = (a: number, b: number) => a + b
 *
 * You can do:
 *
 * const add = (a: number) => (b: number) => a + b
 *
 * The advantage of `pipe` over fluent APIs (when you compose functions by calling
 * methods on an object) is that you can use it with any function, not just methods
 * on an object.
 *
 * NOTE: Most of the helper functions provided by Effect-TS are made to be used with
 * `pipe`, so you will get the most out of them if you use `pipe` to compose them.
 *
 * Takeaway: Use `pipe` to write data flows in a more readable way.
 */
export { pipe } from '@effect-ts/core/Function'

/**
 * Function: flow
 * ==============
 *
 * There are times where you want to compose functions, but you don't want to pass
 * the result of one function to the next.
 *
 * For example, you might want to compose a function that takes a string and returns
 * a number, and another function that takes a number and returns a boolean, but you
 * don't have the input string, and you don't want to create an argument for it.
 * In this case, you can use `flow`.
 *
 * Example:
 *
 * const isEven = (n: number) => n % 2 === 0
 * const length = (s: string) => s.length
 *
 * const isEvenLength = flow(
 *   length,
 *   isEven
 * )
 *
 * In this example, `flow` is returning a function that takes a string, and returns
 * a boolean, making it possible to avoid specifying the input string as an explicit
 * parameter. Still, this usually makes it less readable, so use it with caution.
 *
 * Takeaway: Use `flow` to compose functions.
 */
export { flow } from '@effect-ts/core/Function'

/**
 * Module: Array
 * =============
 *
 * The Array module provides a set of functions to work with arrays. It provides the
 * typical ones like `map`, `filter`, and `reduce`, but also some more convenient ones
 * like `flatten`, `zip`, and `flatMap`.
 *
 * By convention, the functions are curried, so you can use them with `pipe` or `flow`.
 * If you need the uncurried version, there is an alternative function with the `_`
 * suffix, meaning that it takes the array as the first argument. E.g.:
 * `Array.map_(array, fn)` is the same as `Array.map(fn)(array)`.
 *
 * Takeaway: Use the Array module to work with arrays, as many of the functions are
 * optimized and more type-safe than the ones provided by the standard library.
 */
export * as Array from '@effect-ts/core/Collections/Immutable/Array'

/**
 * Module: Dictionary
 * ==================
 *
 * The Dictionary module provides a set of functions to work with the `Dictionary` type,
 * which is defined like:
 *
 * type Dictionary<A> = Record<string, A>
 *
 * This module provides many helper functions that help you working with dictionaries,
 * so you can transform them, filter them, and so on.
 *
 * In the same way as the Array module, the functions are curried, so you can use them
 * with `pipe` or `flow`, and there's a data-first alternative suffixed with `_`.
 */
export * as Dictionary from '@effect-ts/core/Collections/Immutable/Dictionary'

/**
 * Module: Tuple
 * =============
 *
 * The Tuple module provides a set of functions to work with the `Tuple` type, which is
 * a wrapper over what Effect-TS calls "native tuples", which are just arrays with a
 * fixed length. The `Tuple` type has the benefit of having additional functions that
 * allow you to update specific elements, removing, or adding elements, and so on, without
 * having to destructure the tuple and then reconstruct it.
 *
 * Again, it uses the same convention as the Array module, where the functions are
 * curried, and there's a data-first alternative suffixed with `_`.
 */
export * as Tuple from '@effect-ts/core/Collections/Immutable/Tuple'

/**
 * Module: Ref
 * ===========
 *
 * The Ref module provides a set of functions to work with the `Ref` type, which is
 * a mutable reference to a value. It is similar to how you would use a variable in
 * JavaScript, but it is immutable, so you can't reassign it. Instead, you can update
 * it using the `update` function, which takes a function that receives the current
 * value and returns the new value.
 *
 * This additional ceremony looks like a burden, but it is actually a good thing, as
 * it forces you to think about the state of your program, and how you can update it
 * in a safe way. It also makes it easier to reason about your program, as you can
 * see all the places where the state is updated.
 *
 * On top of this, the `Ref` type makes easier to store shared state in a concurrent
 * environment, as it provides a way to atomically update the value, so you don't have
 * to worry about race conditions.
 *
 * It also uses the same convention as the Array module, where the functions are
 * curried, and there's a data-first alternative suffixed with `_`.
 */
export * as Ref from '@effect-ts/core/Effect/Ref'
/*************************************************
 *                                               *
 *             WORKING WITH ERRORS               *
 *                                               *
 *************************************************/

/**
 * Function: orDie
 * ===============
 *
 * This function takes any possible error being tracked at the type level, and converts
 * it to an unknown error. This is useful when you want to ignore the error, and just
 * terminate the program if it fails.
 *
 * Example:
 *
 * const foo = pipe(
 *   readFile('foo.txt'),
 *   orDie
 * )
 *
 * In this example, the effect `foo` will run the side effect of reading the file
 * foo.txt, and then return the result of the call, which is a string. If the file
 * doesn't exist, or there is a permission error, the effect will fail with an unknown
 * error, and the program will terminate.
 *
 * Takeaway: Use `orDie` to ignore errors and terminate the program if they occur.
 */
export { orDie } from '@effect-ts/core/Effect'

/**
 * Function: orDieWith
 * ===================
 *
 * This function takes any possible error being tracked at the type level, and lets the
 * user decide what to do with it. It takes a function that receives the error, and
 * returns a value defined by the user.
 *
 * Example:
 *
 * const foo = pipe(
 *   readFile('foo.txt'),
 *   orDieWith(err => new Error(`Failed to read file: ${err}`))
 * )
 *
 * In this example, the effect `foo` will run the side effect of reading the file
 * foo.txt, and then return the result of the call, which is a string. If the file
 * doesn't exist, or there is a permission error, the effect will fail with the error
 * provided by the user, which is an instance of the `Error` class.
 *
 * Takeaway: Use `orDieWith` to ignore errors and terminate the program if they occur,
 * but let the user decide what to do with them.
 */
export { orDieWith } from '@effect-ts/core/Effect'

/**
 * Function: dieMessage
 * ====================
 *
 * This function takes a string, and returns an effect that will terminate the program
 * with an error containing the string. It is useful for when you've done some checks
 * and you're sure that the program should terminate, but you don't want to provide
 * a custom error type.
 */
export { dieMessage } from '@effect-ts/core/Effect'

/*************************************************
 *                                               *
 *             DEPENDENCY INJECTION              *
 *                                               *
 *************************************************/

/*
 * Effect-TS provides a way to inject dependencies into your program, so you can
 * mock them in tests, or provide different implementations depending on the
 * environment. It is based on simple argument passing to functions, as we saw in
 * the definition of the `Effect` type in the first section.
 *
 * This makes the usage of dependency injection much more predictable, as opposed
 * to other libraries that use a global state to store the dependencies, and manage
 * a dependency container that is shared across the whole program.
 *
 *
 *
 * TERMINOLOGY
 * ===========
 *
 * Effect-TS defines the following terminology when working with dependencies:
 *
 * - Service: An interface that defines behavior. It can contain functions, constants,
 *   or any other value.
 * - Implementation: A concrete implementation of a service. It is a record of the
 *   functions, and constants that the service provides.
 * - Constructor: A way of constructing a concrete implementation of a service. It
 *   can be a function, a value, or an effect.
 * - Layer: A way of composing constructors to produce multiple services which may have
 *   different constructors.
 * - Environment: A record of services that are available in the current context of the
 *   effect being executed.
 *
 *
 *
 * ORGANIZING SERVICES
 * ===================
 *
 * Usually, each service will be a folder with a `index.ts` file that contains the
 * interface, and a `live.impl.ts` file that contains the implementation. The `live.impl.ts`
 * file will export:
 * - A layer to construct ONLY the implementation of the service.
 * - Another layer, called Live<X>, that will construct the implementation of the
 *   service, and all the services that it depends on.
 *
 *
 *
 * TESTING
 * =======
 *
 * In the case of testing, you can create a `live.test.ts` file that will export a
 * layer that will construct a mocked implementation of the service. For this check
 * the `@boostercloud/application-tester/dist/effect` module for more information.
 *
 *
 *
 * DEFINING SERVICES
 * =================
 *
 * To begin defining services, usually we define three things in the `index.ts` file:
 *
 * - An error type for the service. This is useful to have a single error type for
 *   all the errors that can occur in the service.
 * - The interface of the service. This is the interface that will be used by the
 *   rest of the program to interact with the service.
 * - A tag for the service. This is a unique symbol that will be used to identify
 *   the service in the environment.
 *
 *
 *
 * Service error type
 * ------------------
 *
 * The error type is a class that has a `readonly _tag` property which identifies the
 * error in the execution graph handled by the `Effect` type, providing more information
 * to us for when the program fails.
 *
 * Apart from this field, you can store whatever information you want in the error, including
 * creating a custom constructor if you want to provide more information to the user.
 *
 * Example:
 *
 * export class FileSystemError {
 *   readonly _tag = 'FileSystemError'
 *   readonly error: Error
 *   public constructor(readonly reason: unknown) {
 *     this.error = reason instanceof Error ? reason : new Error(JSON.stringify(reason))
 *   }
 * }
 *
 *
 *
 * Service interface
 * -----------------
 *
 * The service interface is a record of the functions, and constants that the service
 * provides. For testing purposes, and simplification of the code, we decided that the
 * service interface should only contain functions, and not constants. There's an open
 * issue to extend this to constants as well, see #1255.
 *
 * Note that the functions in the service must always return an `Effect` type, and have
 * the environment type to be `unknown`.
 *
 * Example:
 *
 * export interface FileSystemService {
 *   readFile: (path: string) => Effect<unknown, FileSystemError, string>
 * }
 *
 *
 *
 * Service tag
 * -----------
 *
 * The service tag is a unique symbol that will be used to identify the service in the
 * dependencies graph of Effect-TS. By convention, we use the name of the service as the
 * tag, making it much easier to access from the program.
 *
 * Example:
 *
 * export const FileSystemService = tag<FileSystemService>()
 */
export { Tag, tag } from '@effect-ts/core/Has'

/**
 * DEFINING CONSTRUCTORS AND LAYERS
 * ================================
 *
 *
 *
 * Value constructors
 * ------------------
 *
 * In the case that the service is a simple value, you can define a constructor that
 * returns the value. This is useful for services that are not stateful, and don't
 * require any initialization.
 *
 * Example:
 *
 * const fileSystemService = () => ({
 *   readFile: (path: string) => tryCatch(() => fs.readFileSync(path, 'utf-8'), (e) => new FileSystemError(e))
 * })
 *
 * With this, we can define the layer that will construct the service:
 *
 * export const LiveFileSystem = Layer.fromValue(FileSystemService)(fileSystemService)
 *
 * We use the `fromValue` function to create a layer from a value. As the first argument,
 * we pass the tag of the service, and as the second argument, we pass the record of
 * functions that the service provides.
 *
 *
 *
 * Effect constructors
 * -------------------
 *
 * In the case that the service is stateful, or requires some initialization, you can
 * define a constructor that returns an effect. Remember, you can use the `gen` function
 * to code in a familiar way, similar to async/await.
 *
 * Example:
 *
 * const remoteFileSystemService = gen(function* ($) {
 *   const ssh = yield* $(SSHService)
 *   // ... construct the service and return it
 * })
 *
 * With this, we create the layer, but now using the `fromEffect` function. Given that it is
 * an effect, we can even handle the error in the constructor, and return a different error,
 * return a different service, or even shutdown the program completely:
 *
 * const RemoteFileSystem = Layer.fromEffect(FileSystemService)(remoteFileSystemService)
 *
 * The thing is that we can't use the `RemoteFileSystem` layer directly, as it will fail
 * if the `SSHService` is not available in the environment. To solve this, we can create
 * a new layer that depends on the `RemoteFileSystem` layer, and the `SSHService` layer:
 *
 * const liveDependencies = Layer.all(SSHService)
 * export const LiveRemoteFileSystem = Layer.using(liveDependencies)(RemoteFileSystem)
 *
 * The `all` function will create a layer that contains all the services that are passed
 * as arguments. The `using` function will create a layer that depends on the services
 * that are passed as the first argument, and will construct the service that is passed
 * as the second argument.
 *
 * We don't need to worry about duplicating the dependencies throughout the layers, as
 * Effect-TS will automatically remove the duplicated dependencies.
 */
export * as Layer from '@effect-ts/core/Effect/Layer'

/**
 * USING SERVICES IN THE PROGRAM
 * =============================
 *
 * As you saw quickly in the remoteFileSystemService example, you can use tag of the service
 * to access it in the environment. This will return the implementation of the service that
 * is available in the environment. If there's no implementation available, the code won't
 * compile, instead of failing at runtime.
 *
 * The convention to access services is usually to destructure the service into the functions
 * that you want to use, and then use them directly.
 *
 * Example:
 *
 * const program = gen(function* ($) {
 *   const { readFile } = yield* $(FileSystemService)
 *   const content = yield* $(readFile('file.txt'))
 *   return `The content of the file is: ${content}`
 * })
 *
 * The `program` variable will track automatically the dependencies of the program, including
 * the possible errors that the services can produce. Making sure that we provide the correct
 * implementation of the services in the environment, and that we handle the errors correctly.
 *
 * We usually don't specify the type of the programs, as it is inferred automatically by the
 * compiler. The inferred type for the program above is:
 *
 * Effect<Has<FileSystem>, FileSystemError, string>
 *
 * `Has` is a special type that is used to represent the dependencies of the program. When you
 * use multiple dependencies, the type will be a union of all the dependencies. For example:
 * `Has<FileSystem> | Has<SSH>`.
 */
export { Has } from '@effect-ts/core/Has'

/*
 * For a more complete example with Layers, check out the `PackageManager` service in the
 * `cli` package. It's a good example of how to use Layers to create a complex service
 * that depends on other services, and returns different implementations depending on
 * the runtime conditions.
 */

/*************************************************
 *                                               *
 *             RUNNING EFFECTS                   *
 *                                               *
 *************************************************/

/**
 * At some point, you will want to run your program. Effect-TS provides a few ways to do
 * this, depending on the use case. Still, we decided to simplify this as much as possible,
 * to be able to gradually introduce effects in place of Promises, without having to change
 * the entire codebase at once.
 *
 * For this, we provide the `unsafeRunEffect` function. As the name suggests, this function
 * is unsafe, and should only be used in the top-level of the program, or the top-most level
 * of the code that you're changing.
 *
 * As the first parameter you must pass the effect that you want to run, and as the second
 * one, you pass a record with the layer that contains the implementation of the services
 * required by the effect, and an onError function that will be called if the effect fails.
 *
 * NOTE: The onError function MUST remove the error from the effect type signature, either
 * recovering from it, or halting the program with `orDieWith` or similar.
 *
 * If everything was provided properly `unsafeRunEffect` will return a Promise with the
 * result of the effect.
 *
 * Example:
 *
 * const program = gen(function* ($) {
 *  const { readFile } = yield* $(FileSystemService)
 *  const { log } = yield* $(LoggerService)
 *  const content = yield* $(readFile('file.txt'))
 *  yield* $(log(`The content of the file is: ${content}`))
 * })
 *
 * export const main = async () => {
 *   await unsafeRunEffect(program, {
 *     layer: Layer.all(LiveFileSystem, LiveLogger),
 *     onError: (e) => dieMessage('Oops, something went wrong')
 *   })
 * }
 *
 */
import { pipe } from '@effect-ts/core'
import { Layer } from '@effect-ts/core/Effect/Layer'
import { Effect, provideSomeLayer, runPromise } from '@effect-ts/core/Effect'
import { Has } from '@effect-ts/core/Has'

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
