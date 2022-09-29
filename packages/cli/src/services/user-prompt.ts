import inquirer = require('inquirer')

export default class Prompter {
  public async defaultOrPrompt(value: string | undefined | undefined, promptMessage: string): Promise<string> {
    if (value) {
      return value.replace(/"/g, '\\"')
    } else {
      const res = await inquirer.prompt([{ name: 'value', type: 'input', message: promptMessage }])
      return res['value'].replace(/"/g, '\\"')
    }
  }

  public async defaultOrChoose(
    value: string | undefined | undefined,
    promptMessage: string,
    options: Array<string>
  ): Promise<string> {
    if (value) {
      return value
    } else {
      const res = await inquirer.prompt([{ name: 'value', type: 'list', message: promptMessage, choices: options }])
      return res['value']
    }
  }

  public static async confirmPrompt(promptParameters: object): Promise<boolean> {
    const confirm = await inquirer
      .prompt([{ name: 'confirm', type: 'confirm', default: false, ...promptParameters }])
      .then(({ confirm }) => confirm)

    return confirm
  }
}
