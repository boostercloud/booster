/**
 * Splits an array in an array of chunks
 * @param chunkSize size of the chunks to split it
 * @param array array to split in chunks
 */
export function inChunksOf<TElement>(chunkSize: number, array: Array<TElement>): Array<Array<TElement>> {
  const result = []
  const array_ = [...array]
  while (array_.length > 0) {
    result.push(array_.splice(0, chunkSize))
  }
  return result
}
