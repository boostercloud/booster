import * as path from 'path'
import fs = require('fs')
import util = require('util')
import { stateStore } from './templates/statestore'
import { K8sManagement } from './k8s-sdk/K8sManagement'
import { DaprTemplateValues } from './templates/templateInterface'
import { HelmManager } from './helm-manager'
import Mustache = require('mustache')
import { BoosterConfig } from '@boostercloud/framework-types'
import { getProjectNamespaceName } from './utils'
const readdir = util.promisify(require('fs').readdir)
const mkdir = util.promisify(require('fs').mkdir)
const writeFile = util.promisify(require('fs').writeFile)
const readFile = util.promisify(require('fs').readFile)
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
   *
   * @returns {Promise<string[]>}
   * @memberof DaprManager
   */
  public async configureEventStore(): Promise<string[]> {
    const errors: string[] = []
    if (!fs.existsSync(this.daprComponentsPath)) {
      const templateValues: DaprTemplateValues = await this.verifyEventStore()
      await this.createDaprComponentFile(templateValues)
    }
    const daprComponents = await this.readDaprComponentDirectory()
    for (const component of daprComponents) {
      const componentYaml = path.join(this.daprComponentsPath, component)
      try {
        await this.clusterManager.execRawCommand(`apply -f ${componentYaml}`)
      } catch (err) {
        errors.push(err.toString())
      }
    }
    return errors.length > 0 ? Promise.reject(errors.toString()) : Promise.resolve([])
  }

  /**
   * remove dapr services from your cluster
   *
   * @returns {(Promise<string>)}
   * @memberof DaprManager
   */
  public async deleteDaprService(): Promise<string> {
    const { stderr } = await this.helmManager.exec(`uninstall dapr -n ${this.namespace}`)
    if (stderr) throw new Error(stderr)
    return 'ok'
  }

  /**
   * delete the event store from your cluster if the event store was created by booster during the deploy,
   * in the case that the event store were provided by the user, this method is not going to delete it
   *
   * @returns {Promise<string>}
   * @memberof DaprManager
   */
  public async deleteEventStore(): Promise<string> {
    const fileContent = await this.readDaprComponentFile(this.stateStoreFileName)
    if (fileContent.indexOf('booster/created: "true"') > -1) {
      const { stderr } = await this.helmManager.exec(`uninstall redis -n ${this.namespace}`)
      if (stderr) {
        return Promise.reject(stderr.toString())
      }
    }
    return 'ok'
  }

  /**
   * create an event store to be used by booster
   *
   * @returns {Promise<DaprTemplateValues>}
   * @memberof DaprManager
   */
  public async verifyEventStore(): Promise<DaprTemplateValues> {
    const eventStore = await this.clusterManager.getPodFromNamespace(this.namespace, this.eventStorePod)
    if (!eventStore) {
      const repoInstalled = await this.helmManager.isRepoInstalled(this.eventStoreRepo)
      if (!repoInstalled) {
        await this.helmManager.installRepo(this.eventStoreRepoName, this.eventStoreRepo)
      }
      await this.helmManager.exec(`install redis bitnami/redis -n ${this.namespace}`)
      await this.clusterManager.waitForPodToBeReady(this.namespace, this.eventStorePod)
    }
    const eventStorePassword = await this.clusterManager.getSecret(this.namespace, this.eventStorePod).catch((err) => {
      return Promise.reject(err)
    })
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
   *
   * @returns {Promise<string[]>}
   * @memberof DaprManager
   */
  public async readDaprComponentDirectory(): Promise<string[]> {
    return readdir(this.daprComponentsPath)
  }

  /**
   * parse a Dapr component file
   *
   * @param {string} componentFile
   * @returns {Promise<string>}
   * @memberof DaprManager
   */
  public async readDaprComponentFile(componentFile: string): Promise<string> {
    const filePath = path.join(this.daprComponentsPath, componentFile)
    return readFile(filePath, { encoding: 'utf-8' })
  }

  /**
   * create a Dapr component file using the provided template inside the Dapr component folder
   *
   * @param {DaprTemplateValues} templateValues
   * @returns {(Promise<string | void>)}
   * @memberof DaprManager
   */
  public async createDaprComponentFile(templateValues: DaprTemplateValues): Promise<void> {
    await mkdir(this.daprComponentsPath).catch(() => {
      return Promise.reject('Unable to create folder for Dapr components, review permissions')
    })
    const outFile = path.join(this.daprComponentsPath, this.stateStoreFileName)
    const renderedYaml = Mustache.render(stateStore.template, templateValues)

    writeFile(outFile, renderedYaml).catch(() => {
      return Promise.reject('Unable to create the index file for your app')
    })
  }
}
