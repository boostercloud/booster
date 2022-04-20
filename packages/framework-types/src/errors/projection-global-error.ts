import { GlobalErrorContainer } from './global-error-container'
import { EntityInterface, ReadModelInterface } from '../concepts'

export class ProjectionGlobalError extends GlobalErrorContainer {
  constructor(
    readonly entity: EntityInterface,
    readonly readModel: ReadModelInterface | undefined,
    originalError: Error
  ) {
    super(originalError)
  }
}
