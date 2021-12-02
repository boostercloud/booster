import { expect } from '../../expect'
import { buildAppPrefix, toAzureName, toTerraformName } from '../../../src/infrastructure/helper/utils'
import { BoosterConfig } from '@boostercloud/framework-types'
import { createResourceGroupName } from '../../../dist/infrastructure/helper/utils'

describe('Users want to use utility methods', () => {
  describe('and build the terraform name', () => {
    it('with a simple name', () => {
      const result = toTerraformName('name', '')
      expect(result).to.be.equal('name')
    })

    it('with a simple name and suffix', () => {
      const result = toTerraformName('name', 'suffix')
      expect(result).to.be.equal('namesuffix')
    })

    it('with a long name and suffix return last name characters and the whole suffix', () => {
      const result = toTerraformName('0123456789012345678901234', 'suffix')
      expect(result).to.be.equal('789012345678901234suffix')
    })

    it('with a name and a 24 characters suffix return name and first suffix characters', () => {
      const result = toTerraformName('name', '012345678901234567890123')
      expect(result).to.be.equal('name0123456789012345678')
    })
  })

  describe('and clean an Azure name', () => {
    it('with a simple name', () => {
      const result = toAzureName('name')
      expect(result).to.be.equal('name')
    })
    it('with a name longer than expected', () => {
      const result = toAzureName('0123456789012345678901234567890')
      expect(result).to.be.equal('012345678901234567890123')
    })
    it('with invalid characters in the name', () => {
      const result = toAzureName('0-_-1_-_2')
      expect(result).to.be.equal('012')
    })
  })

  describe('and build the app prefix', () => {
    it('with a simple name', () => {
      const config = { appName: 'appName', environmentName: 'environmentName' } as BoosterConfig
      const result = buildAppPrefix(config)
      expect(result).to.be.equal('appnameenvironmentname')
    })
    it('with a name longer than expected', () => {
      const config = { appName: 'appName0123456789', environmentName: 'environmentName0123456789' } as BoosterConfig
      const result = buildAppPrefix(config)
      expect(result).to.be.equal('appname0123456789environ')
    })
  })

  describe('and create a resource group name', () => {
    it('with a simple name', () => {
      const result = createResourceGroupName('appName', 'environmentName')
      expect(result).to.be.equal('appNameenvironmentNarg')
    })
    it('with a name longer than expected', () => {
      const result = createResourceGroupName('appName0123456789', 'environmentName0123456789')
      expect(result).to.be.equal('appName0123456789envrg')
    })
  })
})
