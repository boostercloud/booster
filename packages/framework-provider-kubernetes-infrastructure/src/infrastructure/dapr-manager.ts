import * as path from 'path'
import { getLogger, Promises } from '@boostercloud/framework-common-helpers'
import * as fs from 'fs'
import { stateStore } from './templates/statestore'
import { K8sManagement } from './k8s-sdk/k8s-management'
import { DaprTemplateRoles, DaprTemplateValues, Template } from './templates/template-types'
import { HelmManager } from './helm-manager'
import * as Mustache from 'mustache'
import { BoosterConfig } from '@boostercloud/framework-types'
import { getProjectNamespaceName } from './utils'
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
// Manager for Dapr
export class DaprManager {
  private eventStoreRepo = 'https://charts.bitnami.com/bitnami'
  private eventStoreRepoName = 'bitnami'
  public eventStoreHost = 'redis-master:6379'
  private eventStoreSecretName = 'redis-password'
  public eventStoreUser = 'admin'
  private eventStorePod = 'redis'
  public eventStorePassword = ''
  private daprComponentsPath = path.join(process.cwd(), 'components')
  private stateStoreFileName = 'statestore.yaml'
  private namespace: string

  constructor(
    readonly config: BoosterConfig,
    readonly clusterManager: K8sManagement,
    readonly helmManager: HelmManager
  ) {
    this.namespace = getProjectNamespaceName(config)
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
    const logger = getLogger(this.config, 'DaprManager#configureEventStore')
    logger.debug('Starting to configure event store')
    const stateStoreFilePath = path.join(this.daprComponentsPath, this.stateStoreFileName)
    if (fs.existsSync(stateStoreFilePath)) {
      logger.debug('statetore.yaml exists')
      const stateStoreFileContent = fs.readFileSync(stateStoreFilePath).toString()
      const yamlData = safeLoad(stateStoreFileContent) as StateStoreYaml
      if (!yamlData.metadata.annotations['booster/created']) {
        logger.debug('The state store is provisioned by the user. Getting statestore credentials')
        //TODO: Get the credentials for the state store in K8s provider if the user provides us the statestore in Dapr. We need to get the DB_HOST, DB_USER and DB_PASS to pass it to the runtime
        //Research how to get the eventStorePassword from the statestore file
        //this.eventStorePassword has to be set
        throw new Error('Unable to get the state store credentials from your Dapr File')
      }
      logger.debug(
        'The state store is provisioned by Booster. Verifying that the state store is working on the cluster'
      )
      const templateValues: DaprTemplateValues = await this.ensureEventStoreIsReady()
      await this.createDaprComponentFile(templateValues)
      this.eventStorePassword = templateValues.eventStorePassword
    } else {
      logger.debug("Components path doesn't exist, ensuring event store is ready")
      const templateValues: DaprTemplateValues = await this.ensureEventStoreIsReady()
      logger.debug('Creating component file')
      await this.createDaprComponentFile(templateValues)
      this.eventStorePassword = await this.getEventStorePassword()
    }
    logger.debug('Reading dapr component directory')
    const daprComponents = await this.readDaprComponentDirectory()
    logger.debug('Creating all yaml components')
    await Promises.allSettledAndFulfilled(
      daprComponents.map(async (component) => {
        const componentYaml = path.join(this.daprComponentsPath, component)
        logger.debug('Applying', componentYaml)
        const { stderr } = await this.clusterManager.execRawCommand(`apply -f ${componentYaml}`)
        if (stderr) throw new Error(stderr)
      })
    )
  }

  /**
   * remove dapr services from your cluster
   */
  public async deleteDaprService(): Promise<void> {
    const { stdout, stderr } = await this.helmManager.exec(`uninstall dapr -n ${this.namespace}`)
    if (!stdout && stderr) throw new Error(stderr)
  }

  /**
   * delete the event store from your cluster if the event store was created by booster during the deploy,
   * in the case that the event store were provided by the user, this method is not going to delete it
   */
  public async deleteEventStore(): Promise<void> {
    const fileContent = await this.readDaprComponentFile(this.stateStoreFileName)
    if (fileContent.indexOf('booster/created: "true"') > -1) {
      const { stdout, stderr } = await this.helmManager.exec(`uninstall redis -n ${this.namespace}`)
      if (!stdout && stderr) {
        throw new Error(stderr)
      }
    }
  }

  /**
   * create an event store to be used by booster
   */
  public async ensureEventStoreIsReady(): Promise<DaprTemplateValues> {
    const logger = getLogger(this.config, 'DaprManager#ensureEventStoreIsReady')
    logger.debug('Getting pod from namespace')
    const eventStore = await this.clusterManager.getPodFromNamespace(this.namespace, this.eventStorePod)
    if (!eventStore) {
      logger.debug('Checking if repo is installed')
      const repoInstalled = await this.helmManager.isRepoInstalled(this.eventStoreRepo)
      if (!repoInstalled) {
        logger.debug('Repo not installed, installing')
        await this.helmManager.installRepo(this.eventStoreRepoName, this.eventStoreRepo)
      }
      logger.debug('Installing redis using bitnami/redis')
      await this.helmManager.exec(`install redis bitnami/redis -n ${this.namespace}`)
      logger.debug('Waiting for pod to be ready')
      await this.clusterManager.waitForPodToBeReady(this.namespace, this.eventStorePod)
    }
    logger.debug('Getting event store password')
    this.eventStorePassword = await this.getEventStorePassword()
    return {
      namespace: this.namespace,
      eventStoreHost: this.eventStoreHost,
      eventStoreUsername: this.eventStoreUser,
      eventStorePassword: this.eventStorePassword,
    }
  }

  public async getEventStorePassword(): Promise<string> {
    const logger = getLogger(this.config, 'DaprManager#getEventStorePassword')
    logger.debug('Getting event store password')
    const eventStorePassword = await this.clusterManager.getSecret(this.namespace, this.eventStorePod)
    if (!eventStorePassword) {
      logger.debug("Couldn't get event store password, throwing")
      throw new Error(
        'Impossible to get the secret from the cluster for your event store, please check your cluster for more information'
      )
    }
    logger.debug('Encoding password')
    const buff = Buffer.from(eventStorePassword?.data?.[this.eventStoreSecretName] ?? '', 'base64')
    const decodedPassword = buff.toString('utf-8')
    return decodedPassword
  }

  /**
   * return all the dapr components filename included inside the Dapr component folder
   */
  public async readDaprComponentDirectory(): Promise<string[]> {
    const logger = getLogger(this.config, 'DaprManager#readDaprComponentDirectory')
    logger.debug('Reading directory', this.daprComponentsPath)
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
    const logger = getLogger(this.config, 'DaprManager#createDaprComponentFile')
    logger.debug('Creating directory for dapr components')
    if (!fs.existsSync(this.daprComponentsPath)) {
      await fs.promises.mkdir(this.daprComponentsPath).catch(() => {
        logger.debug("Couldn't create directory, throwing")
        throw new Error(
          'Unable to create folder for Dapr components. Please check permissions of your booster project folder'
        )
      })
    }
    const outFile = path.join(this.daprComponentsPath, this.stateStoreFileName)
    const renderedYaml = Mustache.render(stateStore.template, templateValues)
    logger.debug('Rendered Yaml:\n', renderedYaml)

    logger.debug('Writing yaml file', outFile)
    fs.promises.writeFile(outFile, renderedYaml).catch(() => {
      logger.debug("Couldn't write file, throwing")
      throw new Error(`Unable to create the index file for your app: Tried to write ${outFile} and failed`)
    })
  }

  /*
   * Fill the template with the provided values and apply it to the cluster
   */
  private async applyTemplate(template: Template, templateValues: DaprTemplateRoles): Promise<boolean> {
    const logger = getLogger(this.config, 'DaprManager#applyTemplate')
    logger.debug('Applying template')
    const clusterResponse = await this.clusterManager.applyTemplate(template.template, templateValues)
    if (clusterResponse.length == 0) {
      logger.debug("Cluster didn't respond throwing")
      throw new Error(
        `Unable to create ${template.name} service for your project, please check your Kubectl configuration`
      )
    }
    return true
  }
}
