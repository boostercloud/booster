import { CloudWatchLogs } from 'aws-sdk'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'

const logGroupLambdaPrefix = '/aws/lambda/'

export async function fetchAllLogs(
  cloudWatchLogs: CloudWatchLogs,
  config: BoosterConfig,
  logger: Logger
): Promise<unknown> {
  const logGroupNames = [
    logGroupLambdaPrefix + config.resourceNames.functions.eventsMain,
    logGroupLambdaPrefix + config.resourceNames.functions.graphqlHandler,
    logGroupLambdaPrefix + config.resourceNames.functions.subscriptionNotifier,
  ]

  // logGroupName (1) or logGroupsNames (array) ???
  return await cloudWatchLogs
    .startQuery({
      logGroupNames: logGroupNames,
      startTime: new Date().getTime(),
      endTime: Date.now(),
      queryString: '',
    })
    .promise()
}
