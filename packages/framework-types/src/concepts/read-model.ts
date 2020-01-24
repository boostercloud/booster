import { UUID } from '.'
import { Class } from '../typelevel'

export interface ReadModelInterface {
  id: UUID
}

export interface ReadModelMetadata {
  readonly class: Class<ReadModelInterface>
}
