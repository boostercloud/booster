type SubscribeFn = (question: string, chunk: string, finished: boolean) => void

enum ApiEndpoint {
  Answer = 'https://asktoai.boosterframework.com/api/answer',
  ReactAnswer = 'https://asktoai.boosterframework.com/api/reactanswer',
}

export enum AnswerReaction {
  Upvoted = 'Upvoted',
  Downvoted = 'Downvoted'
}

export class ChatService {
  static async reactToAnswer(questionId: string, reaction: AnswerReaction) {
    await fetch(ApiEndpoint.ReactAnswer), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        questionId,
        reaction
      })
    }
  }

  static async answerBoosterQuestion(
    question: string,
    callback: SubscribeFn,
    abortSignal?: AbortSignal
  ): Promise<string> {
    const response = await fetch(ApiEndpoint.Answer, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        question,
      }),
      signal: abortSignal,
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

      if (abortSignal && abortSignal.aborted) {
        reader.cancel()
        return
      }

      done = doneReading
      const chunkValue = decoder.decode(value)
      callback(question, chunkValue, doneReading)
    }
  }
}
