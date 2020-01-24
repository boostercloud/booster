export interface HasName {
  name: string
}

export interface HasFields {
  fields: Array<Field>
}

export interface Field {
  name: string
  type: string
}

export interface HasReaction {
  events: Array<ReactionEvent>
}

export interface ReactionEvent {
  eventName: string
}
