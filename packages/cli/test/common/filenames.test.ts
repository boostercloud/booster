import { checkResourceNameIsValid, classNameToFileName } from '../../src/common/filenames'
import { expect } from '../expect'
import { fake, replace, restore } from 'sinon'
import { logger } from '../../src/services/logger'

const rewire = require('rewire')
const filenames = rewire('../../src/common/filenames')
const formatResourceName = filenames.__get__('formatResourceName')
const titleCaseString = filenames.__get__('titleCaseString')

describe('filenames', () => {
  afterEach(() => {
    restore()
  })

  describe('checkResourceNameIsValid', () => {
    beforeEach(() => {
      replace(logger, 'info', fake.resolves({}))
    })

    it('should print info message', async () => {
      await checkResourceNameIsValid('TestResource')
      expect(logger.info).to.have.been.calledWithMatch('Checking if resource has valid name...')
    })

    it('should do nothing if resource name is valid PascalCase', async () => {
      let exceptionThrown = false

      await checkResourceNameIsValid('TestResource').then(() => {
        exceptionThrown = false
      })

      expect(exceptionThrown).to.be.equal(false)
    })

    it('should throw error if resource name is not valid PascalCase ', async () => {
      const resourceName = 'test resource'
      let exceptionThrown = false
      let exceptionMessage = ''

      await checkResourceNameIsValid(resourceName).catch((e) => {
        exceptionThrown = true
        exceptionMessage = e.message
      })

      expect(exceptionThrown).to.be.equal(true)
      expect(exceptionMessage).to.be.equal(
        `'${resourceName}' is not valid resource name. Please use PascalCase name with valid characters.`
      )
    })
  })

  describe('formatResourceName', () => {
    describe('valid PascalCase', () => {
      it('from string with spaces', () => {
        expect(formatResourceName('test case')).to.be.equal('TestCase')
      })

      it('from single character string', () => {
        expect(formatResourceName('t')).to.be.equal('T')
      })

      it('from string with spaces and invalid characters ', () => {
        expect(formatResourceName('_ =& test resource^name*With 21   =Fields')).to.be.equal(
          'TestResourceNameWith21Fields'
        )
      })

      it('from string with underline characters', () => {
        expect(formatResourceName('test_resource_name')).to.be.equal('TestResourceName')
      })
    })

    describe('should return null when', () => {
      it('string is empty', () => {
        expect(formatResourceName('')).to.be.null
      })

      it('string is empty after removing invalid characters', () => {
        expect(formatResourceName('###!##-==-_-')).to.be.null
      })
    })
  })

  describe('titleCaseString', () => {
    it('should transform string to title case', () => {
      expect(titleCaseString('test')).to.be.equal('Test')
    })

    it('should transform string[] to expected title case', () => {
      const stringsMap: string[][] = [
        ['first', 'First'],
        ['second', 'Second'],
        ['third', 'Third'],
      ]

      stringsMap.forEach((item) => {
        expect(titleCaseString(item[0])).to.be.equal(item[1])
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
