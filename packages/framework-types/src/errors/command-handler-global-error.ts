import { GlobalErrorContainer } from './global-error-container'
import { CommandEnvelope } from '../envelope'

export class CommandHandlerGlobalError extends GlobalErrorContainer {
  constructor(readonly command: CommandEnvelope, originalError: Error) {
    super(originalError)
  }
}
