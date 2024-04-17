import { HealthIndicatorMetadata } from '@boostercloud/framework-types'
import 'mocha'
import {
  childrenHealthProviders,
  isEnabled,
  metadataFromId,
  parentId,
  rootHealthProviders,
  showChildren,
} from '../../../src/sensor/health/health-utils'
import { expect } from '../../expect'
import { BoosterHealthIndicator } from '../../../src/sensor'

describe('Health utils', () => {
  let root: HealthIndicatorMetadata
  let rootChildren1: HealthIndicatorMetadata
  let rootChildren2: HealthIndicatorMetadata
  let rootChildren1Children1: HealthIndicatorMetadata
  let rootChildren1Children2: HealthIndicatorMetadata
  let healthProviders: Record<string, HealthIndicatorMetadata>
  beforeEach(() => {
    root = {
      class: BoosterHealthIndicator,
      healthIndicatorConfiguration: {
        id: 'root',
        name: 'root',
        enabled: true,
        details: true,
        showChildren: true,
      },
    }
    rootChildren1 = {
      class: BoosterHealthIndicator,
      healthIndicatorConfiguration: {
        id: 'root/rootChildren1',
        name: 'root/rootChildren1',
        enabled: true,
        details: true,
        showChildren: true,
      },
    }
    rootChildren2 = {
      class: BoosterHealthIndicator,
      healthIndicatorConfiguration: {
        id: 'root/rootChildren2',
        name: 'root/rootChildren2',
        enabled: true,
        details: true,
        showChildren: true,
      },
    }
    rootChildren1Children1 = {
      class: BoosterHealthIndicator,
      healthIndicatorConfiguration: {
        id: 'root/rootChildren1/rootChildren1Children1',
        name: 'root/rootChildren1/rootChildren1Children1',
        enabled: true,
        details: true,
        showChildren: true,
      },
    }
    rootChildren1Children2 = {
      class: BoosterHealthIndicator,
      healthIndicatorConfiguration: {
        id: 'root/rootChildren1/rootChildren1Children2',
        name: 'root/rootChildren1/rootChildren1Children2',
        enabled: true,
        details: true,
        showChildren: true,
      },
    }
    healthProviders = {
      root: root,
      [rootChildren1.healthIndicatorConfiguration.id]: rootChildren1,
      [rootChildren2.healthIndicatorConfiguration.id]: rootChildren2,
      [rootChildren1Children1.healthIndicatorConfiguration.id]: rootChildren1Children1,
      [rootChildren1Children2.healthIndicatorConfiguration.id]: rootChildren1Children2,
    }
  })
  it('isEnabled return true if all are true', () => {
    expect(isEnabled(root, healthProviders)).to.be.true
    expect(isEnabled(rootChildren1, healthProviders)).to.be.true
    expect(isEnabled(rootChildren2, healthProviders)).to.be.true
    expect(isEnabled(rootChildren1Children1, healthProviders)).to.be.true
    expect(isEnabled(rootChildren1Children2, healthProviders)).to.be.true
  })

  it('isEnabled return false in a component but not in parents or siblings', () => {
    healthProviders[rootChildren1Children1.healthIndicatorConfiguration.id].healthIndicatorConfiguration.enabled = false
    expect(isEnabled(root, healthProviders)).to.be.true

    expect(isEnabled(rootChildren1, healthProviders)).to.be.true
    expect(isEnabled(rootChildren1Children1, healthProviders)).to.be.false
    expect(isEnabled(rootChildren1Children2, healthProviders)).to.be.true

    expect(isEnabled(rootChildren2, healthProviders)).to.be.true
  })

  it('isEnabled return false in a component and all the children but not siblings', () => {
    healthProviders[rootChildren1.healthIndicatorConfiguration.id].healthIndicatorConfiguration.enabled = false
    expect(isEnabled(root, healthProviders)).to.be.true

    expect(isEnabled(rootChildren1, healthProviders)).to.be.false
    expect(isEnabled(rootChildren1Children1, healthProviders)).to.be.false
    expect(isEnabled(rootChildren1Children2, healthProviders)).to.be.false

    expect(isEnabled(rootChildren2, healthProviders)).to.be.true
  })

  it('showChildren return true if all are true', () => {
    expect(showChildren(root, healthProviders)).to.be.true
    expect(showChildren(rootChildren1, healthProviders)).to.be.true
    expect(showChildren(rootChildren2, healthProviders)).to.be.true
    expect(showChildren(rootChildren1Children1, healthProviders)).to.be.true
    expect(showChildren(rootChildren1Children2, healthProviders)).to.be.true
  })

  it('showChildren return false in a component but not in parents or siblings', () => {
    healthProviders[rootChildren1Children1.healthIndicatorConfiguration.id].healthIndicatorConfiguration.showChildren =
      false
    expect(showChildren(root, healthProviders)).to.be.true

    expect(showChildren(rootChildren1, healthProviders)).to.be.true
    expect(showChildren(rootChildren1Children1, healthProviders)).to.be.false
    expect(showChildren(rootChildren1Children2, healthProviders)).to.be.true

    expect(showChildren(rootChildren2, healthProviders)).to.be.true
  })

  it('showChildren return false in a component and all the children but not siblings', () => {
    healthProviders[rootChildren1.healthIndicatorConfiguration.id].healthIndicatorConfiguration.showChildren = false
    expect(showChildren(root, healthProviders)).to.be.true

    expect(showChildren(rootChildren1, healthProviders)).to.be.false
    expect(showChildren(rootChildren1Children1, healthProviders)).to.be.false
    expect(showChildren(rootChildren1Children2, healthProviders)).to.be.false

    expect(showChildren(rootChildren2, healthProviders)).to.be.true
  })

  it('metadataFromId', () => {
    expect(() => metadataFromId(healthProviders, '')).to.throw('Unexpected HealthProvider id ')
    expect(() => metadataFromId(healthProviders, 'xxx')).to.throw('Unexpected HealthProvider id ')
    expect(metadataFromId(healthProviders, 'root')).to.be.deep.equal(root)
    expect(metadataFromId(healthProviders, 'root/rootChildren1')).to.be.deep.equal(rootChildren1)
    expect(metadataFromId(healthProviders, 'root/rootChildren2')).to.be.deep.equal(rootChildren2)
    expect(metadataFromId(healthProviders, 'root/rootChildren1/rootChildren1Children1')).to.be.deep.equal(
      rootChildren1Children1
    )
    expect(metadataFromId(healthProviders, 'root/rootChildren1/rootChildren1Children2')).to.be.deep.equal(
      rootChildren1Children2
    )
  })

  it('parentId', () => {
    expect(parentId(root)).to.be.eq('')
    expect(parentId(rootChildren1)).to.be.eq('root')
    expect(parentId(rootChildren2)).to.be.eq('root')
    expect(parentId(rootChildren1Children1)).to.be.eq('root/rootChildren1')
    expect(parentId(rootChildren1Children2)).to.be.eq('root/rootChildren1')
  })

  it('rootHealthProviders', () => {
    expect(rootHealthProviders(healthProviders)).to.be.deep.equal([root])
  })

  it('childrenHealthProviders', () => {
    expect(childrenHealthProviders(root, healthProviders)).to.be.deep.equal([rootChildren1, rootChildren2])
    expect(childrenHealthProviders(rootChildren1, healthProviders)).to.be.deep.equal([
      rootChildren1Children1,
      rootChildren1Children2,
    ])
    expect(childrenHealthProviders(rootChildren1Children1, healthProviders)).to.be.deep.equal([])
    expect(childrenHealthProviders(rootChildren1Children2, healthProviders)).to.be.deep.equal([])
    expect(childrenHealthProviders(rootChildren2, healthProviders)).to.be.deep.equal([])
  })
})
