import { ReadModelRequestEnvelope, ReadModelInterface } from '..'

export interface ReadModelPubSub {
  asyncIterator(readModelRequestEnvelope: ReadModelRequestEnvelope): AsyncIterator<ReadModelInterface>
}
