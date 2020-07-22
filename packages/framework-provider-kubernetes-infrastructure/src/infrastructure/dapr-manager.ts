/* eslint-disable @typescript-eslint/explicit-function-return-type */
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

  public async configureEventStore() {
    if (!this.existsComponentFolder()) {
      const templateValues: DaprTemplateValues = await this.verifyEventStore()
      await this.createDaprComponentFile(templateValues)
    }
    const daprComponents = await readdir(this.daprComponentsPath)
    daprComponents.forEach(async (component: string) => {
      const componentYaml = path.join(this.daprComponentsPath, component)
      try {
        await this.clusterManager.execRawCommand(`apply -f ${componentYaml}`)
      } catch (err) {
        console.log(err)
      }
    })
  }

  public async deleteDaprService() {
    await this.helmManager.exec(`uninstall dapr -n ${this.namespace}`)
  }

  public async deleteEventStore() {
    const eventStorePath = path.join(this.daprComponentsPath, this.stateStoreFileName)
    const fileContent = await readFile(eventStorePath, { encoding: 'utf-8' })
    if (fileContent.indexOf('booster/created: "true"') > -1) {
      await this.helmManager.exec(`uninstall redis -n ${this.namespace}`)
    }
  }

  private existsComponentFolder(): boolean {
    if (fs.existsSync(this.daprComponentsPath)) {
      return true
    }
    return false
  }

  private async verifyEventStore(): Promise<DaprTemplateValues> {
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
      throw new Error(err)
    })
    const buff = Buffer.from(eventStorePassword?.data?.[this.eventStoreSecretName] ?? '', 'base64')
    const decodePassword = buff.toString('utf-8')

    return {
      namespace: this.namespace,
      eventStoreHost: this.eventStoreHost,
      eventStoreUsername: this.eventStoreUser,
      eventStorePassword: decodePassword,
    }
  }

  private async createDaprComponentFile(templateValues: DaprTemplateValues) {
    await mkdir(this.daprComponentsPath).catch(() => {
      throw new Error('Unable to create folder for Dapr components, review permissions')
    })
    const outFile = path.join(this.daprComponentsPath, this.stateStoreFileName)
    const renderedYaml = Mustache.render(stateStore.template, templateValues)

    writeFile(outFile, renderedYaml).catch(() => {
      throw new Error('Unable to create the index file for your app')
    })
  }
}
