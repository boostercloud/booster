import { ErrorBase } from '../services/error-handler'
import Brand from './brand'

export type CliErrorName =
  | 'FileSystemError'
  | 'ProcessError'
  | 'GeneratorError'
  | 'PackageManagerError'
  | 'ProjectConfigurationError'
  | 'SandboxCreationError'
  | 'NoEnvironmentSet'
  | 'NoMatchingEnvironment'
  | 'CloudProviderError'

export class CliError extends ErrorBase<CliErrorName> {}

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
