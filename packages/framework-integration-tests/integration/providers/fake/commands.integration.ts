import { expect } from 'chai'
import { loadBoosterApp } from './application-helper'

describe('commands', () => {
  context('a valid command', () => {
    xit('should be properly executed', () => {
      const app = loadBoosterApp()

      expect(app).not.to.be.null
    })
  })
})
