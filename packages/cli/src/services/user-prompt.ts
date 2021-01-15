import inquirer = require('inquirer')

export default class Prompter {
  public async defaultOrPrompt(value: string | undefined | null, promptMessage: string): Promise<string> {
    if (value) {
      return Promise.resolve(value.replace(/\"/g,"\\\""))
    } else {
      const res = await inquirer.prompt([{ name: 'value', type: 'input', message: promptMessage }])
      return Promise.resolve(res['value'].replace(/\"/g,"\\\""))
    }
  }

  public async defaultOrChoose(
    value: string | undefined | null,
    promptMessage: string,
    options: Array<string>
  ): Promise<string> {
    if (value) {
      return Promise.resolve(value)
    } else {
      const res = await inquirer.prompt([{ name: 'value', type: 'list', message: promptMessage, choices: options }])
      return Promise.resolve(res['value'])
    }
  }
}
