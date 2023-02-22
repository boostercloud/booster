import { Logger } from '@boostercloud/framework-types'
import { Component } from '../../common/component'
import { ErrorHandler } from '.'
import { CliError, CliErrorName } from '../../common/errors'

@Component
export class CliErrorHandler implements ErrorHandler<CliErrorName> {
  constructor(readonly logger: Logger) {}

  async handleError(error: CliError): Promise<void> {
    this.logger.error(error.toString())
  }
}
