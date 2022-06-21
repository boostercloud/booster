/* eslint-disable @typescript-eslint/explicit-function-return-type */
import * as fc from 'fast-check'
import { EventEnvelope, UUID } from '@boostercloud/framework-types'
import { EventStore } from '../../src/services/event-store'
import * as assert from 'assert'

// ---- Model

type SnapshotStore = {
  [entityName: string]: {
    [entityId: string]: Array<EventEnvelope>
  }
}

export class Model {
  events: Array<EventEnvelope> = []
  snapshots: SnapshotStore = {}
}

export type Command = fc.AsyncCommand<Model, EventStore>

// ---- Helper functions

const getLatestSnapshot = (snapshots: Array<EventEnvelope>): EventEnvelope | undefined =>
  snapshots.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0]

// ---- Commands

class FetchEntitySnapshot implements Command {
  constructor(readonly entityName: string, readonly entityID: UUID, readonly pendingEnvelopes?: Array<EventEnvelope>) {}

  // Can only be fetched if a snapshot was stored for a specific entity and entity ID
  check = (m: Model) => this.entityName in m.snapshots && this.entityID.toString() in m.snapshots[this.entityName]

  async run(m: Model, system: EventStore) {
    const simulationSnapshot = getLatestSnapshot(m.snapshots[this.entityName][this.entityID.toString()])
    const realSnapshot = await system.fetchEntitySnapshot(this.entityName, this.entityID, this.pendingEnvelopes)
    assert(simulationSnapshot === realSnapshot)
  }

  toString = () => `fetchEntitySnapshot(${this.entityName}, ${this.entityID}, ${this.pendingEnvelopes})`
}

class StoreSnapshotCommand implements Command {
  constructor(readonly snapshot: EventEnvelope) {}

  // A snapshot can always be stored with no effects
  check = () => true

  async run(m: Model, system: EventStore) {
    const { entityTypeName, entityID } = this.snapshot
    m.snapshots[entityTypeName][entityID.toString()].push(this.snapshot)
    await system.storeSnapshot(this.snapshot)
  }

  toString = (): string => `storeSnapshot(${this.snapshot})`
}
