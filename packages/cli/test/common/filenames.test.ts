import { classNameToFileName, resourceNameToClassName } from '../../src/common/filenames'
import { expect } from '../expect'

describe('filenames', () => {
  describe('resourceNameToClassName', () => {
    it('creates correct class name', () => {
      const generatedClassName = resourceNameToClassName('testResource')
      expect(generatedClassName).to.equal('TestResource')
    })

    describe('generates correct class name when passed with invalid characters', () => {
      it('resource name with space characters', () => {
        const generatedClassName = resourceNameToClassName('test resource name')
        expect(generatedClassName).to.equal('TestResourceName')
      })

      it('resource name with underline character', () => {
        const generatedClassName = resourceNameToClassName('test_resource_name')
        expect(generatedClassName).to.equal('TestResourceName')
      })

      it('resource name with dash character', () => {
        const generatedClassName = resourceNameToClassName('test-resource-name')
        expect(generatedClassName).to.equal('TestResourceName')
      })
    })
  })

  describe('classNameToFileName', () => {
    it('transforms passed resource name to correct file name', () => {
      const generatedClassName = classNameToFileName('testResource')
      expect(generatedClassName).to.equal('test-resource')
    })

    it('transforms PascalCased resource name to correct file name', () => {
      const generatedClassName = classNameToFileName('TestResource')
      expect(generatedClassName).to.equal('test-resource')
    })
  })
})
