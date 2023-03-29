import * as path from 'path'
import { ErrorBase } from '../services/error-handler'

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
  | 'DynamicImportError'
  | 'UserInputError'

export class CliError extends ErrorBase<CliErrorName> {}

export class ImpossibleError extends Error {
  constructor(message?: string) {
    super(makeMessage(message, new Error().stack))
  }
}

function makeMessage(message?: string, stack?: string) {
  const nodeVersion = process.version
  const os = process.platform
  const architecture = process.arch
  const boosterVersion = require(path.join(__dirname, '../../package.json')).version
  const stackTrace = stack
  return `

  ====================
  ERROR: ${message}
  ====================

  ✨ Congratulations, you have found a bug in Booster! ✨

  This shouldn't have happened! Please report this at the Booster GitHub repo 🙏❤️

  Here's a link to the issue creation page:

  https://github.com/boostercloud/booster/issues/new?assignees=&labels=&template=bug_report.md

  The more information you provide, the easier it will be for us to fix it.

  Here's some stuff you can copy paste into the issue:

  - Package manager: <REPLACE WITH THE PACKAGE MANAGER YOU'RE USING (npm, yarn, pnpm, etc)>
  - Message: ${message}
  - Booster version: ${boosterVersion}
  - Node version: ${nodeVersion}
  - OS: ${os}-${architecture}
  - Stack trace:

  ${stackTrace}
  `
}

export async function cliErrorCatch(name: CliErrorName, e: unknown): Promise<CliError> {
  const errorMessage = await toCliErrorMessage(e)
  return new CliError(name, errorMessage)
}

async function toCliErrorMessage(e: unknown): Promise<string> {
  if (e instanceof Promise) e = await e
  if (e instanceof Error) return e.message
  if (e instanceof CliError) {
    const cause = await toCliErrorMessage(e.cause)
    return `${e.message} > ${cause}`
  }
  return JSON.stringify(e)
}
