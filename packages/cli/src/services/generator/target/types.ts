export interface HasName {
  name: string
}

export interface HasEvent {
  event: string
}

export interface HasFields {
  fields: Array<Field>
}

export interface HasProjections {
  projections: Array<Projection>
}

export interface Projection {
  entityName: string
  entityId: string
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

export interface HasImports {
  imports: Array<ImportDeclaration>
}

export interface ImportDeclaration {
  packagePath: string
  commaSeparatedComponents: string
}
