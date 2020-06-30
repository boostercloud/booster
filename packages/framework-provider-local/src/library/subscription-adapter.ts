import { BoosterConfig } from '@boostercloud/framework-types'

export async function notifySubscription(
  config: BoosterConfig,
  connectionID: string,
  data: Record<string, any>
): Promise<void> {
  console.log('Notify subscription not implemented')
  return
}
