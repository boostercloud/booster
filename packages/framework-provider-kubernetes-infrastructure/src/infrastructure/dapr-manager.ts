import * as path from 'path'
import { Promises } from '@boostercloud/framework-common-helpers'
import * as fs from 'fs'
import { stateStore } from './templates/statestore'
import { K8sManagement } from './k8s-sdk/k8s-management'
import { DaprTemplateRoles, DaprTemplateValues, Template } from './templates/template-types'
import { HelmManager } from './helm-manager'
import * as Mustache from 'mustache'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { getProjectNamespaceName } from './utils'
import { scopeLogger } from '../helpers/logger'
import { safeLoad } from 'js-yaml'
import { stateStoreRoleBinding } from './templates/statestore-role-binding'
import { stateStoreRole } from './templates/statestore-role'

interface StateStoreYaml {
  metadata: {
    annotations: {
      [key: string]: string
    }
  }
}
export class DaprManager {
  private eventStoreRepo = 'https://charts.bitnami.com/bitnami'
  private eventStoreRepoName = 'bitnami'
  public eventStoreHost = 'eventstore-mongodb.booster-test-production.svc.cluster.local:27017'
  public eventStoreUser = 'boosteruser'
  public eventStoreSecretName = 'eventstore-mongodb'
  public eventStoreSecretKey = 'mongodb-password'
  private eventStorePod = 'mongodb'
  private daprComponentsPath = path.join(process.cwd(), 'components')
  private stateStoreFileName = 'statestore.yaml'
  private namespace: string
  private clusterManager: K8sManagement
  private helmManager: HelmManager
  private logger: Logger

  constructor(logger: Logger, configuration: BoosterConfig, clusterManager: K8sManagement, helmManager: HelmManager) {
    this.namespace = getProjectNamespaceName(configuration)
    this.clusterManager = clusterManager
    this.helmManager = helmManager
    this.logger = scopeLogger('HelmManager', logger)
  }

  /**
   * Allow Dapr to read secrets in other namespaces different than default
   */
  public async allowDaprToReadSecrets(): Promise<void> {
    await this.applyTemplate(stateStoreRole, { namespace: this.namespace })
    await this.applyTemplate(stateStoreRoleBinding, { namespace: this.namespace })
  }

  /**
   * check if the event store is provided by the user but if the user has not provided a event store,
   * it will create a specific Dapr compatible event store to be used by Booster applications
   */
  public async configureEventStore(): Promise<void> {
    const l = scopeLogger('configureEventStore', this.logger)
    l.debug('Starting to configure event store')
    const stateStoreFilePath = path.join(this.daprComponentsPath, this.stateStoreFileName)
    if (fs.existsSync(stateStoreFilePath)) {
      l.debug('statetore.yaml exists')
      const stateStoreFileContent = fs.readFileSync(stateStoreFilePath).toString()
      const yamlData = safeLoad(stateStoreFileContent) as StateStoreYaml
      if (!yamlData.metadata.annotations['booster/created']) {
        l.debug('The state store is provisioned by the user. Getting statestore credentials')
        //TODO: Get the credentials for the state store in K8s provider if the user provides us the statestore in Dapr. We need to get the DB_HOST, DB_USER and DB_PASS to pass it to the runtime
        //Research how to get the eventStorePassword from the statestore file
        //this.eventStorePassword has to be set
        throw new Error('Unable to get the state store credentials from your Dapr File')
      }
    }
    l.debug("Components path doesn't exist, ensuring event store is ready")
    const templateValues: DaprTemplateValues = await this.ensureEventStoreIsReady()
    l.debug('Creating component file')
    await this.createDaprComponentFile(templateValues)
  }

  /**
   * register the event store as a Dapr component applying the statestore.yaml under the component folder to the cluster with a manual kubectl apply
   */
  public async registerEventStoreInDapr(): Promise<void> {
    const l = scopeLogger('registerEventStoreInDapr', this.logger)
    l.debug('Reading dapr component directory')
    const daprComponents = await this.readDaprComponentDirectory()
    l.debug('Creating all yaml components')
    await Promises.allSettledAndFulfilled(
      daprComponents.map(async (component) => {
        const componentYaml = path.join(this.daprComponentsPath, component)
        l.debug('Applying', componentYaml)
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
      const { stderr } = await this.helmManager.exec(`uninstall eventstore -n ${this.namespace}`)
      if (stderr) {
        throw new Error(stderr)
      }
    }
  }

  /**
   * create an event store to be used by booster
   */
  public async ensureEventStoreIsReady(): Promise<DaprTemplateValues> {
    const l = scopeLogger('ensureEventStoreIsReady', this.logger)
    l.debug('Getting pod from namespace')
    const eventStore = await this.clusterManager.getPodFromNamespace(this.namespace, this.eventStorePod)
    if (!eventStore) {
      l.debug('Checking if repo is installed')
      const repoInstalled = await this.helmManager.isRepoInstalled(this.eventStoreRepo)
      if (!repoInstalled) {
        l.debug('Repo not installed, installing')
        await this.helmManager.installRepo(this.eventStoreRepoName, this.eventStoreRepo)
      }
      l.debug('Installing mongoDB using bitnami/mongodb')
      await this.helmManager.exec(
        `install eventstore bitnami/mongodb -n ${this.namespace} --set auth.enabled=true --set auth.username=boosteruser --set auth.database=booster`
      )
      l.debug('Waiting for pod to be ready')
      await this.clusterManager.waitForPodToBeReady(this.namespace, this.eventStorePod)
    }
    return {
      namespace: this.namespace,
      eventStoreHost: this.eventStoreHost,
      eventStoreUsername: this.eventStoreUser,
      eventStoreSecretName: this.eventStoreSecretName,
      eventStoreSecretKey: this.eventStoreSecretKey,
    }
  }

  /**
   * return all the dapr components filename included inside the Dapr component folder
   */
  public async readDaprComponentDirectory(): Promise<string[]> {
    const l = scopeLogger('readDaprComponentDirectory', this.logger)
    l.debug('Reading directory', this.daprComponentsPath)
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
    const l = scopeLogger('createDaprComponentFile', this.logger)
    l.debug('Creating directory for dapr components')
    if (!fs.existsSync(this.daprComponentsPath)) {
      await fs.promises.mkdir(this.daprComponentsPath).catch(() => {
        l.debug("Couldn't create directory, throwing")
        throw new Error(
          'Unable to create folder for Dapr components. Please check permissions of your booster project folder'
        )
      })
    }
    const outFile = path.join(this.daprComponentsPath, this.stateStoreFileName)
    const renderedYaml = Mustache.render(stateStore.template, templateValues)
    l.debug('Rendered Yaml:\n', renderedYaml)

    l.debug('Writing yaml file', outFile)
    fs.promises.writeFile(outFile, renderedYaml).catch(() => {
      l.debug("Couldn't write file, throwing")
      throw new Error(`Unable to create the index file for your app: Tried to write ${outFile} and failed`)
    })
  }

  /*
   * Fill the template with the provided values and apply it to the cluster
   */
  private async applyTemplate(template: Template, templateValues: DaprTemplateRoles): Promise<boolean> {
    const l = scopeLogger('applyTemplate', this.logger)
    l.debug('Applying template')
    const clusterResponse = await this.clusterManager.applyTemplate(template.template, templateValues)
    if (clusterResponse.length == 0) {
      l.debug("Cluster didn't respond throwing")
      throw new Error(
        `Unable to create ${template.name} service for your project, please check your Kubectl configuration`
      )
    }
    return true
  }
}
