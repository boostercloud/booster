type CallbackFn = (chunk: string) => void

export class ChatSerivce {
  private static ChatEndpoint = 'https://booster-bot.vercel.app/api/answer'

  static async answerBoosterQuestion(question: string, callback: CallbackFn): Promise<string> {
    const response = await fetch(this.ChatEndpoint, {
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

  static async _answerBoosterQuestion(question: string, callback: CallbackFn): Promise<string> {
    const random = (min, max): number => Math.floor(Math.random() * (max - min)) + min

    const RESPONSE = `Once upon a time, there was a small village that was facing a severe drought. The crops were failing and the wells were drying up, leaving the villagers with no source of water. The people were worried and afraid, as they did not know what to do.

  One day, a traveler came to the village and asked the villagers what was wrong. They told him about their situation, and he offered to help. The traveler was a wise and knowledgeable person, and he had with him a magic crystal. He said that if they took the crystal to the top of the highest mountain in the land and placed it in the sun, it would rain.

  The villagers were skeptical, but desperate for any solution, they decided to give it a try. A group of brave men set out on the journey to the top of the mountain, carrying the crystal with them. After several days of climbing, they finally reached the top and placed the crystal in the sun.

  To their amazement, it began to rain. The rain continued for three days and three nights, filling up the wells and nourishing the crops. The villagers were overjoyed and thanked the traveler for his help.

  From that day on, the village prospered and never suffered from drought again. The people remembered the traveler's kindness and told the story of the magic crystal for generations to come.

  The end.`

    const getResponse = (idx: number) => {
      setTimeout(() => {
        const nextToken = RESPONSE[idx]
        callback(nextToken)
        if (idx >= RESPONSE.length) {
          return ''
        }

        getResponse(idx + 1)
      }, random(1, 25) * 10)
    }

    getResponse(0)

    return Promise.resolve('')
  }
}
