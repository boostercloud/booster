import { BoosterConfig } from '@boostercloud/framework-types'
import { createStubInstance, replace, restore, SinonStub, SinonStubbedInstance, stub } from 'sinon'
import { searchEntitiesIds } from '../../src/library/events-search-adapter'
import { expect } from '../expect'
import { WebSocketRegistry } from '../../src/services/web-socket-registry'

describe('The "searchEntitiesIDs" method', () => {
  let mockConfig: BoosterConfig
  let queryStub: SinonStub
  type StubbedClass<T> = SinonStubbedInstance<T> & T
  let mockEventRegistry: SinonStubbedInstance<WebSocketRegistry>

  beforeEach(() => {
    mockConfig = new BoosterConfig('test')
    queryStub = stub()

    mockEventRegistry = createStubInstance(WebSocketRegistry) as StubbedClass<WebSocketRegistry>
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

    expect(queryStub).to.have.been.calledWithExactly({ kind: 'event', entityTypeName: 'entity' }, -1, undefined, {
      entityID: 1,
    })
  })

  it('Generate query for entityTypeName, limit has all fields', async () => {
    const limit = 1
    const entityTypeName = 'entity'
    await searchEntitiesIds(mockEventRegistry as any, mockConfig, limit, undefined, entityTypeName)

    expect(queryStub).to.have.been.calledWithExactly({ kind: 'event', entityTypeName: 'entity' }, -1, undefined, {
      entityID: 1,
    })
  })
})
