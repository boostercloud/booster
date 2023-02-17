type SubscribeFn = (chunk: string) => void

export class ChatSerivce {
  private static VercelEndpoint = 'https://booster-bot.vercel.app/api/answer'

  static async answerBoosterQuestion(question: string, callback: SubscribeFn): Promise<string> {
    const response = await fetch(this.VercelEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        question,
      }),
    })

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
      done = doneReading
      const chunkValue = decoder.decode(value)
      callback(chunkValue)
    }
  }

  static async _answerBoosterQuestion(question: string, subscribeFn: SubscribeFn): Promise<void> {
    return new Promise(async (resolve, reject) => {
      setTimeout(() => {
        subscribeFn('Response')
        resolve()
      }, 2000)
    })
  }
}
