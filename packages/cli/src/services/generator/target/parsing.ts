import { HasName, HasFields, Field, HasReaction, ReactionEvent, Projection, HasProjections, HasEvent } from './types'

/* eslint-disable @typescript-eslint/no-non-null-assertion */
/* eslint-disable @typescript-eslint/generic-type-naming */

export const parseName = (name: string): Promise<HasName> => Promise.resolve({ name })

export const parseEvent = (event: string): Promise<HasEvent> => Promise.resolve({ event })

export const parseFields = (fields: Array<string>): Promise<HasFields> => {
  return Promise.all(fields.map(parseField)).then((fields) => {
    let usedFieldNames: string[] = []
    let duplicated: string | undefined = undefined
    fields.forEach((field) => {
      if (usedFieldNames.includes(field.name) && duplicated === undefined) {
        duplicated = field.name
      }
      usedFieldNames.push(field.name)
    })
    if (duplicated !== undefined) {
      return Promise.reject(fieldDuplicatedError(duplicated))
    }
    return { fields }
  })
}

function parseField(rawField: string): Promise<Field> {
  const splitInput = rawField.split(':')
  if (splitInput.length != 2 || splitInput[0].length === 0 || splitInput[1].length === 0) {
    return Promise.reject(fieldParsingError(rawField))
  } else {
    return Promise.resolve({
      name: splitInput[0],
      type: splitInput[1],
    })
  }
}

export const parseProjections = (fields: Array<string>): Promise<HasProjections> =>
  Promise.all(fields.map(parseProjection)).then((projections) => ({ projections }))

async function parseProjection(rawProjection: string): Promise<Projection> {
  const splitInput = rawProjection.split(':')
  if (splitInput.length != 2 || splitInput[0].length === 0 || splitInput[1].length === 0) {
    throw projectionParsingError(rawProjection)
  } else {
    return {
      entityName: splitInput[0],
      entityId: splitInput[1],
    }
  }
}

export const parseReaction = (rawEvents: Array<string>): Promise<HasReaction> =>
  Promise.all(rawEvents.map(parseReactionEvent)).then((events) => ({
    events,
  }))

const parseReactionEvent = (eventName: string): Promise<ReactionEvent> => Promise.resolve({ eventName })

const fieldParsingError = (field: string): Error =>
  new Error(`Error parsing field ${field}. Fields must be in the form of <field name>:<field type>`)

const fieldDuplicatedError = (field: string): Error =>
  new Error(`Error parsing field ${field}. Fields cannot be duplicated`)

const projectionParsingError = (projection: string): Error =>
  new Error(`Error parsing projection ${projection}. Projections must be in the form of <entity name>:<entity id>`)

/**
 * Joins parsers together used to generate target information for generators.
 *
 * @example
 * ```typescript
 * const myEntity: Promise<HasName & HasFields & HasReaction> =
 *   joinParsers(
 *     parseName(entityName),
 *     parseFields(rawFields),
 *     parseReaction(events)
 *   )
 * ```
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function joinParsers<T extends Promise<any>[]>(
  ...parsers: T
): Promise<TupleToIntersection<{ [K in keyof T]: T[K] extends Promise<infer P> ? P : never }>> {
  return parsers.reduce((promiseA, promiseB) => {
    return promiseA.then((a) => promiseB.then((b) => ({ ...a, ...b })))
  })
}

type TupleToUnion<T> = { [P in keyof T]: T[P] } extends { [K: number]: infer V } ? V : never
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type UnionToIntersection<T> = (T extends any ? (k: T) => void : never) extends (k: infer I) => void ? I : never
type TupleToIntersection<T> = UnionToIntersection<TupleToUnion<T>>
