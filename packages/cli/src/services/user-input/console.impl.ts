import { Component } from '../../common/component'
import * as inquirer from 'inquirer'

@Component
export class ConsoleUserInput {
  public async defaultString(message: string, defaultValue?: string): Promise<string> {
    if (defaultValue) return this.removeQuotes(defaultValue)
    const { value } = await inquirer.prompt([{ name: 'value', type: 'input', message }])
    return this.removeQuotes(value)
  }

  public async defaultChoice(message: string, choices: Array<string>, defaultValue?: string): Promise<string> {
    if (defaultValue) return defaultValue
    const { value } = await inquirer.prompt([{ name: 'value', type: 'list', message, choices }])
    return value
  }

  public async defaultBoolean(message: string, defaultValue?: boolean): Promise<boolean> {
    if (defaultValue) return defaultValue
    const { value } = await inquirer.prompt([{ name: 'value', type: 'confirm', message }])
    return value
  }

  private removeQuotes(value: string): string {
    return value.replace(/"/g, '\\"')
  }
}
