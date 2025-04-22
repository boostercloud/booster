import { GlobalErrorContainer } from './global-error-container'
import { EntityInterface, ProjectionMetadata, ReadModelInterface } from '../concepts'
import { EntitySnapshotEnvelope } from '../envelope'

export class ProjectionGlobalError extends GlobalErrorContainer {
  constructor(
    readonly entityEnvelope: EntitySnapshotEnvelope,
    readonly entity: EntityInterface,
    readonly readModel: ReadModelInterface | undefined,
    readonly projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    originalError: Error
  ) {
    super(originalError)
  }
}
