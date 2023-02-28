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
