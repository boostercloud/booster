import { BoosterConfig, EventSearchParameters } from '@boostercloud/framework-types'
import { createStubInstance, replace, restore, SinonStub, SinonStubbedInstance, stub } from 'sinon'
import { EventRegistry } from '../../src'
import { searchEntitiesIds, searchEvents } from '../../dist/library/events-search-adapter'
import { expect } from '../expect'

describe('The searchEvents method', () => {
  let mockConfig: BoosterConfig
  let queryStub: SinonStub
  type StubbedClass<T> = SinonStubbedInstance<T> & T
  let mockEventRegistry: SinonStubbedInstance<EventRegistry>

  beforeEach(() => {
    mockConfig = new BoosterConfig('test')
    queryStub = stub()

    mockEventRegistry = createStubInstance(EventRegistry) as StubbedClass<EventRegistry>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    replace(mockEventRegistry, 'query', queryStub as any)
  })

  afterEach(() => {
    restore()
  })

  it('Generate query with EventSearchParameters as EventParametersFilterByEntity', async () => {
    const parameters: EventSearchParameters = {
      entity: 'entity',
      entityID: '1',
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters)

    expect(queryStub).to.have.been.calledWithExactly(
      { entityTypeName: 'entity', entityID: '1', kind: 'event' },
      -1,
      undefined
    )
  })

  it('Generate query with EventSearchParameters  as EventParametersFilterByEntity with time', async () => {
    const parameters: EventSearchParameters = {
      entity: 'entity',
      entityID: '1',
      from: 'from',
      to: 'to',
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters)

    expect(queryStub).to.have.been.calledWithExactly(
      { entityTypeName: 'entity', entityID: '1', createdAt: { $gte: 'from', $lte: 'to' }, kind: 'event' },
      -1,
      undefined
    )
  })

  it('Generate query with EventSearchParameters as EventParametersFilterByEntity with time and limit', async () => {
    const parameters: EventSearchParameters = {
      entity: 'entity',
      entityID: '1',
      from: 'from',
      to: 'to',
      limit: 2,
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters)

    expect(queryStub).to.have.been.calledWithExactly(
      { entityTypeName: 'entity', entityID: '1', createdAt: { $gte: 'from', $lte: 'to' }, kind: 'event' },
      -1,
      2
    )
  })

  it('Generate query with EventSearchParameters as EventParametersFilterByType', async () => {
    const parameters: EventSearchParameters = {
      type: 'type',
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters)

    expect(queryStub).to.have.been.calledWithExactly({ typeName: 'type', kind: 'event' }, -1, undefined)
  })

  it('Generate query with EventSearchParameters  as EventParametersFilterByType with time', async () => {
    const parameters: EventSearchParameters = {
      type: 'type',
      from: 'from',
      to: 'to',
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters)

    expect(queryStub).to.have.been.calledWithExactly(
      { typeName: 'type', createdAt: { $gte: 'from', $lte: 'to' }, kind: 'event' },
      -1,
      undefined
    )
  })

  it('Generate query with EventSearchParameters as EventParametersFilterByType with time and limit', async () => {
    const parameters: EventSearchParameters = {
      type: 'type',
      from: 'from',
      to: 'to',
      limit: 2,
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters)

    expect(queryStub).to.have.been.calledWithExactly(
      { typeName: 'type', createdAt: { $gte: 'from', $lte: 'to' }, kind: 'event' },
      -1,
      2
    )
  })

  it('Generate paginated query with PaginatedEventSearchParameters as EventParametersFilterByEntity', async () => {
    const parameters: EventSearchParameters = {
      entity: 'entity',
      entityID: '1',
      limit: 1,
      afterCursor: {
        id: '1',
      },
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters, true)

    expect(queryStub).to.have.been.calledWithExactly(
      { entityTypeName: 'entity', entityID: '1', kind: 'event' },
      -1,
      1,
      1
    )
  })

  it('Generate paginated query with EventSearchParameters  as EventParametersFilterByEntity with time', async () => {
    const parameters: EventSearchParameters = {
      entity: 'entity',
      entityID: '1',
      from: 'from',
      to: 'to',
      limit: 1,
      afterCursor: {
        id: '1',
      },
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters, true)

    expect(queryStub).to.have.been.calledWithExactly(
      { entityTypeName: 'entity', entityID: '1', createdAt: { $gte: 'from', $lte: 'to' }, kind: 'event' },
      -1,
      1,
      1
    )
  })

  it('Generate paginated query with EventSearchParameters as EventParametersFilterByEntity with time and limit', async () => {
    const parameters: EventSearchParameters = {
      entity: 'entity',
      entityID: '1',
      from: 'from',
      to: 'to',
      limit: 2,
      afterCursor: {
        id: '1',
      },
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters, true)

    expect(queryStub).to.have.been.calledWithExactly(
      { entityTypeName: 'entity', entityID: '1', createdAt: { $gte: 'from', $lte: 'to' }, kind: 'event' },
      -1,
      2,
      1
    )
  })

  it('Generate paginated query with EventSearchParameters as EventParametersFilterByType', async () => {
    const parameters: EventSearchParameters = {
      type: 'type',
      limit: 1,
      afterCursor: {
        id: '1',
      },
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters, true)

    expect(queryStub).to.have.been.calledWithExactly({ typeName: 'type', kind: 'event' }, -1, 1, 1)
  })

  it('Generate paginated query with EventSearchParameters as EventParametersFilterByType with time', async () => {
    const parameters: EventSearchParameters = {
      type: 'type',
      from: 'from',
      to: 'to',
      limit: 1,
      afterCursor: {
        id: '1',
      },
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters, true)

    expect(queryStub).to.have.been.calledWithExactly(
      { typeName: 'type', createdAt: { $gte: 'from', $lte: 'to' }, kind: 'event' },
      -1,
      1,
      1
    )
  })

  it('Generate paginated query with EventSearchParameters as EventParametersFilterByType with time and limit', async () => {
    const parameters: EventSearchParameters = {
      type: 'type',
      from: 'from',
      to: 'to',
      limit: 2,
      afterCursor: {
        id: '1',
      },
    }
    await searchEvents(mockEventRegistry, mockConfig, parameters, true)

    expect(queryStub).to.have.been.calledWithExactly(
      { typeName: 'type', createdAt: { $gte: 'from', $lte: 'to' }, kind: 'event' },
      -1,
      2,
      1
    )
  })
})

describe('The "searchEntitiesIDs" method', () => {
  let mockConfig: BoosterConfig
  let queryStub: SinonStub
  type StubbedClass<T> = SinonStubbedInstance<T> & T
  let mockEventRegistry: SinonStubbedInstance<EventRegistry>

  beforeEach(() => {
    mockConfig = new BoosterConfig('test')
    queryStub = stub()

    mockEventRegistry = createStubInstance(EventRegistry) as StubbedClass<EventRegistry>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    replace(mockEventRegistry, 'query', queryStub as any)
  })

  afterEach(() => {
    restore()
  })

  it('Generate query for entityTypeName, limit and afterCursor has all fields', async () => {
    const limit = 1
    const afterCursor = { id: '1' }
    const entityTypeName = 'entity'
    await searchEntitiesIds(mockEventRegistry as any, mockConfig, limit, afterCursor, entityTypeName)

    expect(queryStub).to.have.been.calledWithExactly(
      { kind: 'event', entityTypeName: 'entity' },
      -1,
      undefined,
      undefined,
      {
        entityID: 1,
      }
    )
  })

  it('Generate query for entityTypeName, limit has all fields', async () => {
    const limit = 1
    const entityTypeName = 'entity'
    await searchEntitiesIds(mockEventRegistry as any, mockConfig, limit, undefined, entityTypeName)

    expect(queryStub).to.have.been.calledWithExactly(
      { kind: 'event', entityTypeName: 'entity' },
      -1,
      undefined,
      undefined,
      {
        entityID: 1,
      }
    )
  })
})
