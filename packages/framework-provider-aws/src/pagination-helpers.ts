/**
 * Waits for `milliseconds`, calls `callback`, and returns the value
 * @param callback callback to return the value from
 * @param milliseconds milliseconds to wait before calling the callback
 */
export const waitAndReturn = <TResult>(callback: () => TResult, milliseconds: number): Promise<TResult> =>
  new Promise((resolve) => {
    setTimeout(() => resolve(callback()), milliseconds)
  })

/**
 * Splits an array in an array of chunks
 * @param chunkSize size of the chunks to split it
 * @param array array to split in chunks
 */
export function inChunksOf<TElement>(chunkSize: number, array: Array<TElement>): Array<Array<TElement>> {
  const result = []
  const arr = [...array]
  while (arr.length) {
    result.push(arr.splice(0, chunkSize))
  }
  return result
}
