export async function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function waitForIt<TResult>(
  tryFunction: () => Promise<TResult>,
  checkResult: (result: TResult) => boolean | string,
  trialDelayMs = 1000,
  timeoutMs = 60000
): Promise<TResult> {
  console.debug('[waitForIt] start')
  const start = Date.now()
  return doWaitFor()

  async function doWaitFor(): Promise<TResult> {
    console.debug('.')
    const res = await tryFunction()
    const checkResultValue = checkResult(res)
    const waitingResult = typeof checkResultValue === 'boolean' ? checkResultValue : false
    if (waitingResult) {
      console.debug('[waitForIt] match!')
      return res
    }
    const elapsed = Date.now() - start

    if (elapsed > timeoutMs) {
      if (typeof checkResultValue === 'boolean') {
        throw new Error('[waitForIt] Timeout reached')
      }
      const message = checkResultValue ? `. ${checkResultValue}` : ''
      throw new Error(`[waitForIt] Timeout reached${message}`)
    }

    await sleep(trialDelayMs)
    return doWaitFor()
  }
}
