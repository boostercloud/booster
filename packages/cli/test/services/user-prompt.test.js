"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const inquirer = require("inquirer");
const user_prompt_1 = require("../../src/services/user-prompt");
const expect_1 = require("../expect");
const sinon_1 = require("sinon");
describe('user prompt', () => {
    beforeEach(() => { });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    it('defaultOrPrompt with provided value', async () => {
        const value = await new user_prompt_1.default().defaultOrPrompt('randomValue', 'Enter a value');
        (0, expect_1.expect)(value).to.equal('randomValue');
    });
    it('defaultOrPrompt with undefined value', async () => {
        const promptStub = (0, sinon_1.stub)(inquirer, 'prompt');
        promptStub.resolves({ value: 'mockedValue' });
        const value = await new user_prompt_1.default().defaultOrPrompt(undefined, 'Enter a value');
        (0, expect_1.expect)(value).to.equal('mockedValue');
    });
    it('defaultOrPrompt with null value', async () => {
        const promptStub = (0, sinon_1.stub)(inquirer, 'prompt');
        promptStub.resolves({ value: 'mockedValue' });
        const value = await new user_prompt_1.default().defaultOrPrompt(null, 'Enter a value');
        (0, expect_1.expect)(value).to.equal('mockedValue');
    });
    it('defaultOrPrompt with provided value', async () => {
        const value = await new user_prompt_1.default().defaultOrPrompt('randomValue', 'Enter a value');
        (0, expect_1.expect)(value).to.equal('randomValue');
    });
    it('defaultOrPrompt with provided value with single quotes', async () => {
        const value = await new user_prompt_1.default().defaultOrPrompt("'randomValue'", 'Enter a value');
        (0, expect_1.expect)(value).to.equal("'randomValue'");
    });
    it('defaultOrPrompt with provided value with double quotes', async () => {
        const value = await new user_prompt_1.default().defaultOrPrompt('"randomValue"', 'Enter a value');
        (0, expect_1.expect)(value).to.equal('\\"randomValue\\"');
    });
    it('defaultOrPrompt with undefined value entering double quotes', async () => {
        const promptStub = (0, sinon_1.stub)(inquirer, 'prompt');
        promptStub.resolves({ value: '"mockedValue"' });
        const value = await new user_prompt_1.default().defaultOrPrompt(undefined, 'Enter a value');
        (0, expect_1.expect)(value).to.equal('\\"mockedValue\\"');
    });
    it('defaultOrPrompt with undefined value entering single quotes', async () => {
        const promptStub = (0, sinon_1.stub)(inquirer, 'prompt');
        promptStub.resolves({ value: "'mockedValue'" });
        const value = await new user_prompt_1.default().defaultOrPrompt(undefined, 'Enter a value');
        (0, expect_1.expect)(value).to.equal("'mockedValue'");
    });
    it('defaultOrChoose with provided value', async () => {
        const value = await new user_prompt_1.default().defaultOrChoose('value1', 'Choose a value', ['value1', 'value2', 'value3']);
        (0, expect_1.expect)(value).to.equal('value1');
    });
    it('defaultOrChoose with undefined value', async () => {
        const promptStub = (0, sinon_1.stub)(inquirer, 'prompt');
        promptStub.resolves({ value: 'value2' });
        const value = await new user_prompt_1.default().defaultOrChoose(undefined, 'Choose a value', ['value1', 'value2', 'value3']);
        (0, expect_1.expect)(value).to.equal('value2');
    });
    it('defaultOrChoose with null value', async () => {
        const promptStub = (0, sinon_1.stub)(inquirer, 'prompt');
        promptStub.resolves({ value: 'value2' });
        const value = await new user_prompt_1.default().defaultOrChoose(null, 'Choose a value', ['value1', 'value2', 'value3']);
        (0, expect_1.expect)(value).to.equal('value2');
    });
    it('confirmPrompt returns confirm boolean value', async () => {
        const promptStub = (0, sinon_1.stub)(inquirer, 'prompt');
        promptStub.resolves({ confirm: true });
        const value = await user_prompt_1.default.confirmPrompt({ message: 'Delete resource?' });
        (0, expect_1.expect)(value).to.be.true;
    });
});
