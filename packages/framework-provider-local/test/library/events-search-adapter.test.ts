import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { createStubInstance, fake, replace, restore, SinonStub, SinonStubbedInstance, stub } from 'sinon'
import { EventRegistry } from '../../src'
import { searchEventsIds } from '../../dist/library/events-search-adapter'
import { expect } from '../expect'

describe('The "searchEventsIds" method', () => {
  let mockLogger: Logger
  let mockConfig: BoosterConfig
  let queryStub: SinonStub
  type StubbedClass<T> = SinonStubbedInstance<T> & T
  let mockEventRegistry: SinonStubbedInstance<EventRegistry>

  beforeEach(() => {
    mockConfig = new BoosterConfig('test')
    mockLogger = {
      info: fake(),
      warn: fake(),
      error: fake(),
      debug: fake(),
    }
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
    await searchEventsIds(mockEventRegistry as any, mockConfig, mockLogger, limit, afterCursor, entityTypeName)

    expect(queryStub).to.have.been.calledWithExactly({ kind: 'event', entityTypeName: 'entity' }, -1, undefined, {
      entityID: 1,
    })
  })

  it('Generate query for entityTypeName, limit has all fields', async () => {
    const limit = 1
    const entityTypeName = 'entity'
    await searchEventsIds(mockEventRegistry as any, mockConfig, mockLogger, limit, undefined, entityTypeName)

    expect(queryStub).to.have.been.calledWithExactly({ kind: 'event', entityTypeName: 'entity' }, -1, undefined, {
      entityID: 1,
    })
  })
})
