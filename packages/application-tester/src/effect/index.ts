import { Effect, Has, Layer, ShapeFn, succeedWith, Tag } from '@boostercloud/framework-types/src/effect'
import { SinonSpy } from 'sinon'

/*
 * This module exposed testing utilities for working with Effect services in tests.
 *
 * If you don't even know what Effect is, you probably should start by reading the docs
 * in the `@boostercloud/framework-types/src/effect` module.
 *
 * The key idea is that you can create a mock service that can be used in tests
 * instead of the real service. This allows you to test your code without
 * depending on the real service, nor having to replace functions of libraries with mocks.
 *
 * When testing services, we don't need to ensure that the service is actually calling
 * the real implementation. We just need to ensure that the service is calling the
 * right functions with the right arguments. That is, unless we're testing the live
 * implementation, which is the only case where it is acceptable to replace the
 * library functions with a mock.
 */

/**
 * Main entry point of the Effect testing helper module.
 * You pass the tag for your service, and a record of mocks for the functions. This function will
 * return a helper object that contains a `Layer` that you can use to replace the service in your
 * tests, the fakes record that you passed, and a `reset` function that will reset the history
 * of all the fakes, so you can use them in multiple tests.
 *
 * @param tag - The tag of the service to fake. E.g. `ProcessService`.
 * @param fakes - A record of the methods to fake. E.g. `{ cwd: fake.returns(''), exec: fake.returns('') }`.
 * @return {FakeServiceUtils} - An object with the layer to use in the dependency graph, and the fakes to assert the service was called with the right parameters.
 */
export const fakeService = <T extends ShapeFn<T>>(tag: Tag<T>, fakes: Fakes<T>): FakeServiceUtils<T> => {
  // Assemble the fakes into a service that returns Effects in its functions
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const fakeService = {} as any
  for (const [k, v] of Object.entries(fakes)) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const fakeFunction = v as SinonSpy<any[], any>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    fakeService[k] = (...args: any[]) => succeedWith(() => fakeFunction(...args))
  }

  // Create a layer with that service as the only dependency
  const layer = Layer.fromValue(tag)(fakeService)

  // Create a reset function to reset all the fakes
  const reset = () => {
    for (const f of Object.values(fakes)) {
      const fake = f as EffectSpy<T, keyof T>
      fake.resetHistory()
    }
  }

  // Return the layer, fakes, and reset function
  return { layer, fakes, reset }
}

/**
 * Utils to mock an entire service, without having to wire up the whole dependency graph.
 * This is useful for unit testing, but not for integration testing.
 *
 * @typedef {Object} FakeServiceUtils
 * @property {Layer} layer - The layer that can be used to replace the service in the dependency graph
 * @property {Record<string, SinonSpy>} fakes - The fakes that can be used to assert the service was called with the right parameters
 * @property {() => void} reset - A function to reset all the fakes
 */
export type FakeServiceUtils<T extends ShapeFn<T>> = {
  layer: Layer.Layer<unknown, never, Has<T>>
  fakes: Fakes<T>
  reset: () => void
}

/**
 * Gets the result type from an Effect
 */
type EffectResult<T> =
  // Disabling `any` warning because we won't be exposing this type
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  T extends Effect<any, any, infer A> ? A : never

/**
 * Type to pass fakes on the creation of a fake service.
 */
type Fakes<T extends ShapeFn<T>> = {
  [key in keyof T]: EffectSpy<T, key>
}

/**
 * Allows overriding fakes in test service generators
 */
export type FakeOverrides<T extends ShapeFn<T>> = Partial<Fakes<T>>

/**
 * Spy for a specific service function
 */
type EffectSpy<T extends ShapeFn<T>, key extends keyof T> =
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  SinonSpy<any[], EffectResult<ReturnType<T[key]>>>
