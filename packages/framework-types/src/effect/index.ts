import { pipe } from '@effect-ts/core'
import { Layer } from '@effect-ts/core/Effect/Layer'
import { Effect, provideSomeLayer } from '@effect-ts/core/Effect'
import { Has } from '@effect-ts/core/Has'
import { runMain } from '@effect-ts/node/Runtime'

export * from '@effect-ts/core/Effect'
export * as Layer from '@effect-ts/core/Effect/Layer'
export * as Ref from '@effect-ts/core/Effect/Ref'
export { Has, Tag, tag } from '@effect-ts/core/Has'
export { runMain } from '@effect-ts/node/Runtime'
export { pipe, flow } from '@effect-ts/core/Function'

type RunWithLayerOpts<R> = {
  readonly layer: Layer<unknown, never, Has<R>>
  readonly onError: (eff: Effect<Has<R>, unknown, unknown>) => Effect<Has<R>, never, unknown>
}

/**
 * Run an effect with a layer, and handle errors
 * @param effect The effect to run
 * @param opts.layer The layer to provide all the services
 * @param opts.onError The function to handle errors
 * @returns void
 */
export const runWithLayer = <R>(effect: Effect<Has<R>, unknown, void>, { layer, onError }: RunWithLayerOpts<R>): void =>
  pipe(effect, onError, provideSomeLayer(layer), runMain)
