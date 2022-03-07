import {
  EventEnvelope,
  EventParametersFilterByEntity,
  EventParametersFilterByType,
  EventSearchResponse,
  FilterFor,
  UUID,
} from '@boostercloud/framework-types'

interface QueryFields {
  createdAt: string
  entityTypeName_entityID_kind: string
  entityTypeName: string
  typeName: string
}

export function buildFiltersForByTime(fromValue?: string, toValue?: string): FilterFor<QueryFields> {
  if (fromValue && toValue) {
    return {
      createdAt: { gte: fromValue, lte: toValue },
    }
  } else if (fromValue) {
    return {
      createdAt: { gte: fromValue },
    }
  } else if (toValue) {
    return {
      createdAt: { lte: toValue },
    }
  }
  return {}
}

export function buildFiltersForByFilters(
  parameters: EventParametersFilterByEntity | EventParametersFilterByType
): FilterFor<QueryFields> {
  if ('entity' in parameters) {
    if (parameters.entityID) {
      return buildByEntityAndID(parameters.entity, parameters.entityID)
    }
    return buildByEntity(parameters.entity)
  } else if ('type' in parameters) {
    return buildByType(parameters.type)
  } else {
    throw new Error('Invalid search event query. It is neither an search by "entity" nor a search by "type"')
  }
}

export function resultToEventSearchResponse(result: any[]): Array<EventSearchResponse> {
  if (!result || result.length === 0) return []
  const eventSearchResult = result.map((item) => {
    return {
      type: item.typeName,
      entity: item.entityTypeName,
      entityID: item.entityID,
      requestID: item.requestID,
      user: item.currentUser,
      createdAt: item.createdAt,
      value: item.value,
    } as EventSearchResponse
  })
  return eventSearchResult ?? []
}

function buildByEntityAndID(entityValue: string, entityIdValue: UUID): FilterFor<QueryFields> {
  const value = partitionKeyForEvent(entityValue, entityIdValue)
  return {
    entityTypeName_entityID_kind: { eq: value },
  }
}

function buildByEntity(entityValue: string): FilterFor<QueryFields> {
  return {
    entityTypeName: { eq: entityValue },
  }
}

function buildByType(typeValue: string): FilterFor<QueryFields> {
  return {
    typeName: { eq: typeValue },
  }
}

function partitionKeyForEvent(entityTypeName: string, entityID: UUID, kind: EventEnvelope['kind'] = 'event'): string {
  return `${entityTypeName}-${entityID}-${kind}`
}
