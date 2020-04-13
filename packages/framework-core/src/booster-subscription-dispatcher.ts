import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { GraphQLSchema } from 'graphql'
import { GraphQLGenerator } from './services/graphql/graphql-generator'
import { BoosterCommandDispatcher } from './booster-command-dispatcher'
import { BoosterReadModelDispatcher } from './booster-read-model-dispatcher'

export class BoosterSubscriptionDispatcher {
  private readonly graphQLSchema: GraphQLSchema

  public constructor(private config: BoosterConfig, private logger: Logger) {
    this.graphQLSchema = new GraphQLGenerator(
      config,
      new BoosterCommandDispatcher(config, logger),
      new BoosterReadModelDispatcher(config, logger)
    ).generateSchema()
  }

  public async dispatch(request: any): Promise<any> {
    try {
      const envelopes = await this.config.provider.rawReadModelEventsToEnvelope(request)
      this.logger.debug('[SubsciptionDispatcher] The following ReadModels were updated: ', envelopes)
      /**
       * 1.- Organize envelopes in a hash by readmodel name. Then for each key and value (which is an array)
       * 2.- Query the subscription using the readModel name as subscriptionName. Then for each readModel instance and subscription result
       * 3.- Filter those whose subscription.filter does not match the readModel.value (could this be done in Dynamo?)
       * 4.- Use GraphQL to get the final data to return.
       * 5.- Send that data using the websocket API
       */
      console.log(this.graphQLSchema)
    } catch (e) {
      this.logger.error(e)
      // return this.config.provider.handleSubscriptionError(e)
    }
  }
}
