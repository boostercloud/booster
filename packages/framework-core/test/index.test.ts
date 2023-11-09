import { expect } from './expect'
import * as BoosterCore from '../src/index'
import * as Booster from '../src/booster'

describe('framework-core package', () => {
  it('exports the `boosterEventDispatcher` function', () => {
    expect(BoosterCore.boosterEventDispatcher).not.to.be.null
    expect(BoosterCore.boosterEventDispatcher).to.equal(Booster.boosterEventDispatcher)
  })

  it('exports the `boosterServeGraphQL` function', () => {
    expect(BoosterCore.boosterServeGraphQL).not.to.be.null
    expect(BoosterCore.boosterServeGraphQL).to.equal(Booster.boosterServeGraphQL)
  })

  it('exports the `boosterNotifySubscribers` function', () => {
    expect(BoosterCore.boosterNotifySubscribers).not.to.be.null
    expect(BoosterCore.boosterNotifySubscribers).to.equal(Booster.boosterNotifySubscribers)
  })

  it('exports the `boosterTriggerScheduledCommand` function', () => {
    expect(BoosterCore.boosterTriggerScheduledCommand).not.to.be.null
    expect(BoosterCore.boosterTriggerScheduledCommand).to.equal(Booster.boosterTriggerScheduledCommand)
  })

  it('exports the `boosterRocketDispatcher` function', () => {
    expect(BoosterCore.boosterRocketDispatcher).not.to.be.null
    expect(BoosterCore.boosterRocketDispatcher).to.equal(Booster.boosterRocketDispatcher)
  })

  it('exports the `boosterHealth` function', () => {
    expect(BoosterCore.boosterHealth).not.to.be.null
    expect(BoosterCore.boosterHealth).to.equal(Booster.boosterHealth)
  })
})
