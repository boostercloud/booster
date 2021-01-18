import { createAsyncIterator } from 'iterall'
import { ReadModelRequestEnvelope, ReadModelInterface } from '..'
import { ReadModelPubSub } from './read-model-pub-sub'

export class NoopReadModelPubSub implements ReadModelPubSub {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  public asyncIterator(_: ReadModelRequestEnvelope): AsyncIterator<ReadModelInterface> {
    return createAsyncIterator([])
  }
}
