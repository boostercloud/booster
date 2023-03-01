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
    super(message)
    this.name = 'ImpossibleError'
    this.message = this.makeMessage(message)
  }

  private makeMessage(message?: string) {
    const nodeVersion = process.version
    const os = process.platform
    const architecture = process.arch
    const boosterVersion = require(__dirname + '../../package.json').version
    const stackTrace = this.stack
    return `✨ Congratulations, you have found a bug in Booster! ✨

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
}
