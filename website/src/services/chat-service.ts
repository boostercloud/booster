import { gql } from '@apollo/client'
import { ApolloService } from './apollo-service'

const BOOSTER_HTTP_URI = 'https://7zhwv1f1d2.execute-api.us-east-1.amazonaws.com/production/graphql'
const BOOSTER_WS_URI = 'wss://gg4mj79dn4.execute-api.us-east-1.amazonaws.com/production/'

type SubscribeFn = (chunk: string) => void

export class ChatSerivce {
  private static ApolloClient = ApolloService.initClient(BOOSTER_HTTP_URI, BOOSTER_WS_URI)
  private static VercelEndpoint = 'https://booster-bot.vercel.app/api/answer'

  static async answerBoosterQuestionWithVercel(question: string, callback: SubscribeFn): Promise<string> {
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

  static async answerBoosterQuestion(question: string, subscribeFn: SubscribeFn): Promise<void> {
    try {
      const res = await this.sendBoosterQuestion(question)
      return this.subscribeToBoosterAnswer(res.data.Answer, subscribeFn)
    } catch (error) {
      console.error(error)
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

  private static sendBoosterQuestion(question: string) {
    return this.ApolloClient.mutate({
      mutation: gql`
        mutation ($question: String!) {
          Answer(input: { question: $question })
        }
      `,
      variables: {
        question,
      },
    })
  }

  private static subscribeToBoosterAnswer(answerId: string, subscribeFn: SubscribeFn): Promise<void> {
    return new Promise(async (resolve, reject) => {
      const subscription = this.ApolloClient.subscribe({
        query: gql`
          subscription ($answerId: ID!) {
            AnswerReadModel(id: $answerId) {
              answer
              status
            }
          }
        `,
        variables: {
          answerId,
        },
      }).subscribe(
        (res) => {
          console.log({ res })

          const { answer, status } = res.data.AnswerReadModel
          subscribeFn(answer)

          if (status === 'completed') {
            subscription.unsubscribe()
            return Promise.resolve()
          }

          if (status === 'failed') {
            subscription.unsubscribe()
            return Promise.reject(new Error('\nSomething wrong happened. Please try again later.'))
          }
        },
        (error) => reject(error),
        () => resolve()
      )
    })
  }
}
