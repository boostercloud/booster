export function isRejected(promiseResult: PromiseSettledResult<unknown>): promiseResult is PromiseRejectedResult {
  return promiseResult.status === 'rejected'
}

export function isFulfilled<T>(promiseResult: PromiseSettledResult<T>): promiseResult is PromiseFulfilledResult<T> {
  return promiseResult.status === 'fulfilled'
}
