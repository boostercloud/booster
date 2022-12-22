import Brand from './brand'
import { orDieWith } from '@boostercloud/framework-types/dist/effect'

/**
 * Builds an error extracting its message from the "stdout" and "stderr" properties if present
 * @param e
 * @param prefix
 */
export function wrapExecError(e: Error, prefix: string): Error {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const { stdout, stderr } = e as any
  return new Error(Brand.dangerize(prefix) + '\n' + stdout + stderr)
}

export const guardError = (prefix: string) =>
  orDieWith((err: Error) => {
    return new Error(Brand.dangerize(`[${err.name}] ${prefix}:`) + '\n' + err.message)
  })

/**
 * Converts an unknown value to an Error. If it's not an error already, it will be stringified.
 */
export const unknownToError = (reason: unknown) =>
  reason instanceof Error ? reason : new Error(JSON.stringify(reason))
