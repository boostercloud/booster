import * as fs from 'fs'
import * as yaml from 'yaml'
import * as path from 'path'
import { ProviderTestHelper } from '@boostercloud/application-tester'
import { runCommand } from '../../helper/run-command'
import { CloudFormation } from 'aws-sdk'
import { Stack } from 'aws-sdk/clients/cloudformation'

const cloudFormation = new CloudFormation()

interface Phase {
  duration: number
  arrivalRate: number
}

interface OverrideOptions {
  phases?: Array<Phase>
  variables?: Record<string, any>
}

export class ArtilleryExecutor {
  private readonly serverlessArtilleryStackPrefix = 'serverless-artillery'
  constructor(
    private scriptsFolder: string,
    private readonly providerTestHelper: ProviderTestHelper,
    private readonly stage = 'booster'
  ) {}

  public async ensureDeployed(): Promise<void> {
    let slsartStack: Stack | undefined
    try {
      const { Stacks } = await cloudFormation
        .describeStacks({
          StackName: `${this.serverlessArtilleryStackPrefix}-${this.stage}`,
        })
        .promise()
      if (Stacks?.[0]) {
        slsartStack = Stacks[0]
      }
    } catch (e) {
      // The CDK returns an exception when the stack is not found. Ignore it and deploy
    }
    const validStatuses = ['CREATE_COMPLETE', 'UPDATE_COMPLETE']
    if (slsartStack) {
      if (validStatuses.includes(slsartStack?.StackStatus)) {
        console.info('Serverless Artillery stack is already deployed. Skipping redeployment.')
        return
      }
      console.info(`Serverless Artillery stack is in a wrong state: ${slsartStack?.StackStatus}. Redeploying.`)
    }
    await runCommand('.', `slsart deploy --stage ${this.stage}`)
  }

  public async executeScript(scriptName: string, overrideOptions: OverrideOptions = {}): Promise<void> {
    const scriptContent = this.getScriptContent(scriptName, overrideOptions)
    await runCommand('.', `slsart invoke --stage ${this.stage} --data '${scriptContent}'`)
  }

  private getScriptContent(scriptName: string, options: OverrideOptions): string {
    const scriptPath = path.join(this.scriptsFolder, scriptName)
    const parsedScript = yaml.parse(fs.readFileSync(scriptPath).toString())
    parsedScript.config.target = this.providerTestHelper.outputs.graphqlURL

    if (options.phases) {
      parsedScript.config.phases = options.phases
    }
    if (options.variables) {
      for (const [name, value] of Object.entries(options.variables)) {
        parsedScript.config.variables[name] = value
      }
    }

    return JSON.stringify(parsedScript)
  }
}
