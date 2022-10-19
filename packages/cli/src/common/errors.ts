import Brand from './brand'
import { orDieWith } from '@boostercloud/framework-types/src/effect'

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

// eslint-disable-next-line @typescript-eslint/explicit-function-return-type
export const guardError = (prefix: string) =>
  orDieWith((reason: unknown) => new Error(Brand.dangerize(prefix) + '\n' + JSON.stringify(reason)))
