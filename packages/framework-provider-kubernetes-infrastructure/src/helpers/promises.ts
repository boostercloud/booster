export class Promises {
  /**
   * Waits until all the passed promise-like values are settled, no matter if they were fulfilled or rejected.
   * If some rejected were found, an array with all the rejected promises is thrown.
   * If all were fulfilled, an array of PromiseFulfilledResult is returned
   * @param values Array of promise-like values to be wait for
   * @throws an array of PromiseRejectedResult with all the rejected promises, if any
   *
   * Comparison with other similar Promise methods:
   * - `Promise.all`: This has an "all-or-nothing" behavior. As long as one of the promises is rejected, the result is
   * rejected. More importantly, **it does not wait for al the promises to finish**, which could lead to undesired behaviors
   * - `Promise.allSettled`: This method waits for all the promises to finish and then returns an array of results. Some
   * of them will be fulfilled and some rejected. More importantly, **it never throws an error**, which could lead to
   * unexpected consequences. For example if you do "await Promise.allSettle(...)" expecting it to throw if some of them
   * failed, you won't get that.
   *
   * In brief, `Promises.allSettledAndFulfilled` behaves exactly the same way as `Promise.allSettle` but it throws with
   * an array of the failed promises, only if there are any.
   */
  public static async allSettledAndFulfilled<TValue>(
    values: Iterable<TValue>
  ): ReturnType<PromiseConstructor['allSettled']> {
    const results = await Promise.allSettled(values) // Promise.allSettled never throws

    // Get all the failed promises
    const failed: Array<PromiseRejectedResult> = results
      .filter((res) => res.status == 'rejected')
      .map((res) => res as PromiseRejectedResult)

    // Throw if we found any failed ones
    if (failed.length > 0) {
      throw new PromisesError(failed)
    }

    return results
  }
}

export class PromisesError extends Error {
  public readonly failedReasons: Array<unknown>
  constructor(rejectedResults: Array<PromiseRejectedResult>) {
    const reasons = rejectedResults.map((res) => res.reason)
    super(reasons.join('. '))
    this.failedReasons = reasons
  }
}
