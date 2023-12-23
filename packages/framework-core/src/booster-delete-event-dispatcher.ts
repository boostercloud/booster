import { BoosterConfig, EventDeleteParameters } from '@boostercloud/framework-types'
import { ReadModelStore } from './services/read-model-store'

export class BoosterDeleteEventDispatcher {
  public static async deleteEvent(config: BoosterConfig, parameters: EventDeleteParameters): Promise<boolean> {
    const readModelStore = new ReadModelStore(config)
    const events = await config.provider.events.findDeletableEvent(config, parameters)
    if (!events || events.length === 0) {
      return false
    }
    for (const event of events) {
      const snapshots = await config.provider.events.findDeletableSnapshot(config, event)
      for (const snapshot of snapshots) {
        await readModelStore.project(snapshot, true)
      }
      await config.provider.events.deleteSnapshot(config, snapshots)
    }
    await config.provider.events.deleteEvent(config, events)
    return true
  }
}
