import { GlobalErrorContainer } from './global-error-container'
import { CommandEnvelope } from '../envelope'
import { CommandMetadata } from '../concepts'

export class CommandHandlerGlobalError extends GlobalErrorContainer {
  constructor(
    readonly commandEnvelope: CommandEnvelope,
    readonly commandMetadata: CommandMetadata,
    originalError: Error
  ) {
    super(originalError)
  }
}
