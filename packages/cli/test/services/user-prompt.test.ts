import inquirer = require('inquirer')
import Prompter from '../../src/services/user-prompt'
import { expect } from '../expect'
import { restore, stub } from 'sinon'

describe('user prompt', () => {
    beforeEach(() => {
    })
  
    afterEach(() => {
      restore()
    })

    it('defaultOrPrompt with provided value', async () => {
        const value = await new Prompter().defaultOrPrompt("randomValue","Enter a value")
        expect(value).to.equal('randomValue')
    })

    it('defaultOrPrompt with undefined value', async () => {
        const promptStub = stub(inquirer,'prompt')
        promptStub.resolves({value: 'mockedValue'})
        const value = await new Prompter().defaultOrPrompt(undefined,"Enter a value")
        expect(value).to.equal('mockedValue')
    })

    it('defaultOrPrompt with null value', async () => {
        const promptStub = stub(inquirer,'prompt')
        promptStub.resolves({value: 'mockedValue'})
        const value = await new Prompter().defaultOrPrompt(null,"Enter a value")
        expect(value).to.equal('mockedValue')
    })

    it('defaultOrPrompt with provided value', async () => {
        const value = await new Prompter().defaultOrPrompt("randomValue","Enter a value")
        expect(value).to.equal('randomValue')
    })

    it('defaultOrChoose with provided value', async () => {
        const value = await new Prompter().defaultOrChoose("value1","Choose a value",['value1','value2','value3'])
        expect(value).to.equal('value1')
    })
    
    it('defaultOrChoose with undefined value', async () => {
        const promptStub = stub(inquirer,'prompt')
        promptStub.resolves({value: 'value2'})
        const value = await new Prompter().defaultOrChoose(undefined,"Choose a value",['value1','value2','value3'])
        expect(value).to.equal('value2')
    })

    it('defaultOrChoose with null value', async () => {
        const promptStub = stub(inquirer,'prompt')
        promptStub.resolves({value: 'value2'})
        const value = await new Prompter().defaultOrChoose(null,"Choose a value",['value1','value2','value3'])
        expect(value).to.equal('value2')
    })
})