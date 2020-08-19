import * as path from 'path'
import * as fs from 'fs'
import { stateStore } from './templates/statestore'
import { K8sManagement } from './k8s-sdk/k8s-management'
import { DaprTemplateValues } from './templates/template-types'
import { HelmManager } from './helm-manager'
import * as Mustache from 'mustache'
import { BoosterConfig } from '@boostercloud/framework-types'
import { getProjectNamespaceName } from './utils'
export class DaprManager {
  private eventStoreRepo = 'https://charts.bitnami.com/bitnami'
  private eventStoreRepoName = 'bitnami'
  private eventStoreHost = 'redis-master:6379'
  private eventStoreSecretName = 'redis-password'
  private eventStoreUser = 'admin'
  private eventStorePod = 'redis'
  private daprComponentsPath = path.join(process.cwd(), 'components')
  private stateStoreFileName = 'statestore.yaml'
  private namespace: string
  private clusterManager: K8sManagement
  private helmManager: HelmManager

  constructor(configuration: BoosterConfig, clusterManager: K8sManagement, helmManager: HelmManager) {
    this.namespace = getProjectNamespaceName(configuration)
    this.clusterManager = clusterManager
    this.helmManager = helmManager
  }

  /**
   * check if the event store is provided by the user but if the user has not provided a event store,
   * it will create a specific Dapr compatible event store to be used by Booster applications
   */
  public async configureEventStore(): Promise<void> {
    if (!fs.existsSync(this.daprComponentsPath)) {
      const templateValues: DaprTemplateValues = await this.ensureEventStoreIsReady()
      await this.createDaprComponentFile(templateValues)
    }
    const daprComponents = await this.readDaprComponentDirectory()
    await Promise.all(
      daprComponents.map(async (component) => {
        const componentYaml = path.join(this.daprComponentsPath, component)
        const { stderr } = await this.clusterManager.execRawCommand(`apply -f ${componentYaml}`)
        if (stderr) throw new Error(stderr)
      })
    )
  }

  /**
   * remove dapr services from your cluster
   */
  public async deleteDaprService(): Promise<void> {
    const { stderr } = await this.helmManager.exec(`uninstall dapr -n ${this.namespace}`)
    if (stderr) throw new Error(stderr)
  }

  /**
   * delete the event store from your cluster if the event store was created by booster during the deploy,
   * in the case that the event store were provided by the user, this method is not going to delete it
   */
  public async deleteEventStore(): Promise<void> {
    const fileContent = await this.readDaprComponentFile(this.stateStoreFileName)
    if (fileContent.indexOf('booster/created: "true"') > -1) {
      const { stderr } = await this.helmManager.exec(`uninstall redis -n ${this.namespace}`)
      if (stderr) {
        throw new Error(stderr)
      }
    }
  }

  /**
   * create an event store to be used by booster
   */
  public async ensureEventStoreIsReady(): Promise<DaprTemplateValues> {
    const eventStore = await this.clusterManager.getPodFromNamespace(this.namespace, this.eventStorePod)
    if (!eventStore) {
      const repoInstalled = await this.helmManager.isRepoInstalled(this.eventStoreRepo)
      if (!repoInstalled) {
        await this.helmManager.installRepo(this.eventStoreRepoName, this.eventStoreRepo)
      }
      await this.helmManager.exec(`install redis bitnami/redis -n ${this.namespace}`)
      await this.clusterManager.waitForPodToBeReady(this.namespace, this.eventStorePod)
    }
    const eventStorePassword = await this.clusterManager.getSecret(this.namespace, this.eventStorePod)
    if (!eventStorePassword) {
      throw new Error(
        'Impossible to get the secret from the cluster for your event store, please check your cluster for more information'
      )
    }
    const buff = Buffer.from(eventStorePassword?.data?.[this.eventStoreSecretName] ?? '', 'base64')
    const decodedPassword = buff.toString('utf-8')

    return {
      namespace: this.namespace,
      eventStoreHost: this.eventStoreHost,
      eventStoreUsername: this.eventStoreUser,
      eventStorePassword: decodedPassword,
    }
  }

  /**
   * return all the dapr components filename included inside the Dapr component folder
   */
  public async readDaprComponentDirectory(): Promise<string[]> {
    return fs.promises.readdir(this.daprComponentsPath)
  }

  /**
   * parse a Dapr component file
   */
  public async readDaprComponentFile(componentFile: string): Promise<string> {
    const filePath = path.join(this.daprComponentsPath, componentFile)
    return fs.promises.readFile(filePath, { encoding: 'utf-8' })
  }

  /**
   * create a Dapr component file using the provided template inside the Dapr component folder
   */
  public async createDaprComponentFile(templateValues: DaprTemplateValues): Promise<void> {
    await fs.promises.mkdir(this.daprComponentsPath).catch(() => {
      throw new Error(
        'Unable to create folder for Dapr components. Please check permissions of your booster project folder'
      )
    })
    const outFile = path.join(this.daprComponentsPath, this.stateStoreFileName)
    const renderedYaml = Mustache.render(stateStore.template, templateValues)

    fs.promises.writeFile(outFile, renderedYaml).catch(() => {
      throw new Error(`Unable to create the index file for your app: Tried to write ${outFile} and failed`)
    })
  }
}
