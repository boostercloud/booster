import { describe } from 'mocha'
import { expect } from 'chai'
import * as BoosterCore from '../src/index'
import * as Booster from '../src/booster'

describe('framework-core package', () => {
  it('exports the `boosterEventDispatcher` function', () => {
    expect(BoosterCore.boosterEventDispatcher).not.to.be.null
    expect(BoosterCore.boosterEventDispatcher).to.equal(Booster.boosterEventDispatcher)
  })

  it('exports the `boosterPreSignUpChecker` function', () => {
    expect(BoosterCore.boosterPreSignUpChecker).not.to.be.null
    expect(BoosterCore.boosterPreSignUpChecker).to.equal(Booster.boosterPreSignUpChecker)
  })
})
