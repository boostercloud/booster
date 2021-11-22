import { expect } from '../expect'
import { toTerraformName } from '../../dist/infrastructure/utils'

describe('Users want to use utility methods', () => {
  describe('and build the terraform name', () => {
    it('with a simple name', () => {
      const result = toTerraformName('name')
      expect(result).to.be.equal('name')
    })

    it('with a simple name and suffix', () => {
      const result = toTerraformName('name', 'suffix')
      expect(result).to.be.equal('namesuffix')
    })

    it('with a long name and suffix return last name characters and the whole suffix', () => {
      const result = toTerraformName('0123456789012345678901234', 'suffix')
      expect(result).to.be.equal('901234suffix')
    })

    it('with a name and a 24 characters suffix return name and first suffix characters', () => {
      const result = toTerraformName('name', '012345678901234567890123')
      expect(result).to.be.equal('name01234567890123456789')
    })
  })
})
