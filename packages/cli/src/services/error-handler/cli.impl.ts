import { Logger } from '@boostercloud/framework-types'
import { Component } from '../../common/component'
import { ErrorHandler } from '.'
import { CliError, CliErrorName } from '../../common/errors'
import Brand from '../../common/brand'
import { match } from 'ts-pattern'

@Component({ throws: Error })
export class CliErrorHandler implements ErrorHandler<CliErrorName> {
  constructor(readonly logger: Logger) {}

  async catch(e: unknown): Promise<Error> {
    return new Error('An unknown error occurred: ' + JSON.stringify(e))
  }

  async handleError(error: CliError): Promise<void> {
    // We use `match` instead of `switch` because it will error if we don't handle all the cases
    match(error)
      .with({ name: 'CloudProviderError' }, (e) => {
        this.logger.error(Brand.dangerize('Error while deploying to the cloud provider:'))
        this.logger.error(e.message)
      })
      .with({ name: 'FileSystemError' }, (e) => {
        this.logger.error(Brand.dangerize('Error while accessing the file system:'))
        this.logger.error(e.message)
      })
      .with({ name: 'GeneratorError' }, (e) => {
        this.logger.error(Brand.dangerize('Error while generating resources:'))
        this.logger.error(e.message)
      })
      .with({ name: 'NoEnvironmentSet' }, (e) => {
        this.logger.error(Brand.dangerize('No environment has been set!'))
        this.logger.error(e.message)
      })
      .with({ name: 'ProcessError' }, (e) => {
        this.logger.error(Brand.dangerize('Error while running a process:'))
        this.logger.error(e.message)
      })
      .with({ name: 'PackageManagerError' }, (e) => {
        this.logger.error(Brand.dangerize('Error while running the package manager:'))
        this.logger.error(e.message)
      })
      .with({ name: 'ProjectConfigurationError' }, (e) => {
        this.logger.error(Brand.dangerize('Error with the project configuration:'))
        this.logger.error(e.message)
      })
      .with({ name: 'SandboxCreationError' }, (e) => {
        this.logger.error(Brand.dangerize('Error while creating the sandbox:'))
        this.logger.error(e.message)
      })
      .with({ name: 'NoMatchingEnvironment' }, (e) => {
        this.logger.error(Brand.dangerize('No matching environment found:'))
        this.logger.error(e.message)
      })
      .with({ name: 'DynamicImportError' }, (e) => {
        this.logger.error(Brand.dangerize('Error while importing a module:'))
        this.logger.error(e.message)
      })
      .with({ name: 'UserInputError' }, (e) => {
        this.logger.error(Brand.dangerize('Error while importing a module:'))
        this.logger.error(e.message)
      })
      .exhaustive()
  }
}
