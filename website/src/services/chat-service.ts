import { gql } from '@apollo/client'
import { ApolloService } from './apollo-service'

const BOOSTER_HTTP_URI = 'https://7zhwv1f1d2.execute-api.us-east-1.amazonaws.com/production/graphql'
const BOOSTER_WS_URI = 'wss://gg4mj79dn4.execute-api.us-east-1.amazonaws.com/production/'

type SubscribeFn = (chunk: string) => void

export class ChatSerivce {
  private static ApolloClient = ApolloService.initClient(BOOSTER_HTTP_URI, BOOSTER_WS_URI)

  static async answerBoosterQuestion(question: string, subscribeFn: SubscribeFn): Promise<void> {
    try {
      const res = await this.sendBoosterQuestion(question)
      return this.subscribeToBoosterAnswer(res.data.Answer, subscribeFn)
    } catch (error) {
      console.error(error)
    }
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
