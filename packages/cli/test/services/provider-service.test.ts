/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { afterEach, describe } from 'mocha'
import * as providerService from '../../src/services/provider-service'
import { restore } from 'sinon'
import { expect } from '../expect'
import { random } from 'faker'

describe('providerService', () => {
  afterEach(() => {
    restore()
  })

  describe('assertNameIsCorrect', () => {
    it('should throw an error on surpassing project name max length', () => {
      const inputString = random.alphaNumeric(random.number( { min: 38 } ))
      const errorString = `Project name cannot be longer than 37 chars long:\n\n    Found: '${inputString}'`

      expect(() => providerService.assertNameIsCorrect(inputString)).to.throw(errorString)
    })

    it('should throw an error if project name includes a space', () => {
      const inputString = random.words(2)
      const errorString = `Project name cannot contain spaces:\n\n    Found: '${inputString}'`

      expect(() => providerService.assertNameIsCorrect(inputString)).to.throw(errorString)
    })

    it('should throw an error if project name includes an uppercase letter', () => {
      const inputString = random.alphaNumeric(37).toUpperCase()
      const errorString = `Project name cannot contain uppercase letters:\n\n    Found: '${inputString}'`

      expect(() => providerService.assertNameIsCorrect(inputString)).to.throw(errorString)
    })

    it('should not throw an error if project name is correct', () => {
      const inputString = random.alphaNumeric(37)

      expect(() => providerService.assertNameIsCorrect(inputString)).to.not.throw()
    })
  })

  describe.skip('deployToCloudProvider', () => {})
})
