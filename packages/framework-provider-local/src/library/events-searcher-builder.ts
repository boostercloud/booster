import {
  EventEnvelope,
  EventParametersFilterByEntity,
  EventParametersFilterByType,
  EventSearchResponse,
  UUID,
} from '@boostercloud/framework-types'

interface QueryFields {
  createdAt?: unknown
  entityTypeName?: string
  entityID?: UUID
  typeName?: string
}

export function buildFiltersForByTime(fromValue?: string, toValue?: string): QueryFields {
  if (fromValue && toValue) {
    return {
      createdAt: { $gte: fromValue, $lte: toValue },
    }
  } else if (fromValue) {
    return {
      createdAt: { $gte: fromValue },
    }
  } else if (toValue) {
    return {
      createdAt: { $lte: toValue },
    }
  }
  return {}
}

export function buildFiltersForByFilters(
  filters: EventParametersFilterByEntity | EventParametersFilterByType
): QueryFields {
  if ('entity' in filters) {
    if (filters.entityID) {
      return buildByEntityAndID(filters.entity, filters.entityID)
    }
    return buildByEntity(filters.entity)
  } else if ('type' in filters) {
    return buildByType(filters.type)
  } else {
    throw new Error('Invalid search event query. It is neither an search by "entity" nor a search by "type"')
  }
}

export function resultToEventSearchResponse(result: Array<EventEnvelope> | null): Array<EventSearchResponse> {
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

function buildByEntityAndID(entityValue: string, entityIdValue: UUID): QueryFields {
  return {
    entityTypeName: entityValue,
    entityID: entityIdValue,
  }
}

function buildByEntity(entityValue: string): QueryFields {
  return {
    entityTypeName: entityValue,
  }
}

function buildByType(typeValue: string): QueryFields {
  return {
    typeName: typeValue,
  }
}
