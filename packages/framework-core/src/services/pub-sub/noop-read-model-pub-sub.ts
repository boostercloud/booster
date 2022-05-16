import { ReadModelRequestEnvelope, ReadModelInterface } from '@boostercloud/framework-types'
import { createAsyncIterator } from 'iterall'
import { ReadModelPubSub } from './read-model-pub-sub'

export class NoopReadModelPubSub implements ReadModelPubSub<ReadModelInterface> {
  public async asyncIterator(
    _: ReadModelRequestEnvelope<ReadModelInterface>
  ): Promise<AsyncIterator<ReadModelInterface>> {
    return createAsyncIterator([])
  }
}
