import { GlobalErrorContainer } from './global-error-container'
import { QueryEnvelope } from '../envelope'

export class QueryHandlerGlobalError extends GlobalErrorContainer {
  constructor(readonly query: QueryEnvelope, originalError: Error) {
    super(originalError)
  }
}
