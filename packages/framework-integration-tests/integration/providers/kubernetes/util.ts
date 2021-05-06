import * as fs from 'fs'
import * as path from 'path'
import * as Mustache from 'mustache'
import { stateStore } from '../../../templates/statestore'
import { kubernetesNamespace } from './constants'

const templateValues = {
  namespace: kubernetesNamespace,
  eventStoreHost: 'redis-master:6379',
  eventStoreUsername: 'admin',
  eventStorePassword: '1234',
}

const stateStoreFileName = 'statestore.yaml'

export const createDaprComponentFile = async (sandboxPath: string): Promise<void> => {
  const daprComponentsPath = `${sandboxPath}/components`
  console.log('Creating directory for dapr components')
  if (!fs.existsSync(daprComponentsPath)) {
    await fs.promises.mkdir(daprComponentsPath).catch(() => {
      console.log("Couldn't create directory, throwing")
      throw new Error(
        'Unable to create folder for Dapr components. Please check permissions of your booster project folder'
      )
    })
  }

  const outFile = path.join(daprComponentsPath, stateStoreFileName)
  const renderedYaml = Mustache.render(stateStore.template, templateValues)

  console.log('Rendered Yaml:\n', renderedYaml)

  console.log('Writing yaml file', outFile)
  await fs.promises.writeFile(outFile, renderedYaml).catch(() => {
    console.log("Couldn't write file, throwing")
    throw new Error(`Unable to create the index file for your app: Tried to write ${outFile} and failed`)
  })
}
