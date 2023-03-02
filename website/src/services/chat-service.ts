type SubscribeFn = (question: string, chunk: string, finished: boolean) => void

export class ChatService {
  private static VercelEndpoint = 'https://booster-bot.vercel.app/api/answer'

  static async answerBoosterQuestion(question: string, callback: SubscribeFn, abortSignal?: AbortSignal): Promise<string> {
    const response = await fetch(this.VercelEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        question,
      }),
      signal: abortSignal
    }, 
    )

    if (!response.ok) {
      throw new Error(response.statusText)
    }

    const data = response.body
    if (!data) {
      return
    }
    const reader = data.getReader()
    const decoder = new TextDecoder()
    let done = false

    while (!done) {
      const { value, done: doneReading } = await reader.read()

      if (abortSignal.aborted) {
        reader.cancel()
        return
      }

      done = doneReading
      const chunkValue = decoder.decode(value)
      callback(question, chunkValue, doneReading)
    }
  }

  static async _answerBoosterQuestion(question: string, subscribeFn: SubscribeFn): Promise<void> {
    return new Promise(async (resolve, reject) => {
      setTimeout(() => {
        subscribeFn(question, 'Response', true)
        resolve()
      }, 2000)
    })
  }
}
