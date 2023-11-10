import { expect } from '../../expect'
import { isJoinKeyByEntity } from '../../../src/services/read-model-projections/read-model-project-utils'

describe('isJoinKeyByEntity', () => {
  it('returns true if the join key is a string', () => {
    expect(isJoinKeyByEntity('id')).to.be.true
  })

  it('returns false if the join key is a ReadModelJoinKeyFunction', () => {
    expect(
      isJoinKeyByEntity((entity) => {
        return undefined
      })
    ).to.be.false
  })
})
