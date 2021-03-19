import { K8sManagement } from './k8s-sdk/k8s-management'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { getProjectNamespaceName, createProjectZipFile, uploadFile } from './utils'
import { uploadService } from './templates/upload-service-template'
import { boosterVolumeClaim } from './templates/volume-claim-template'
import { boosterService } from './templates/booster-service-template'
import { Template, TemplateValues } from './templates/template-types'
import { uploaderPod } from './templates/file-uploader-app-template'
import { boosterAppPod } from './templates/booster-app-template'
import { HelmManager } from './helm-manager'
import { DaprManager } from './dapr-manager'
import { scopeLogger } from '../helpers/logger'

export class DeployManager {
  private clusterManager: K8sManagement
  private namespace: string
  private templateValues: TemplateValues
  private helmManager: HelmManager
  private DaprRepo = 'https://daprio.azurecr.io/helm/v1/repo'
  private daprManager: DaprManager
  private logger: Logger

  constructor(
    logger: Logger,
    configuration: BoosterConfig,
    clusterManager: K8sManagement,
    daprManager: DaprManager,
    helmManager: HelmManager
  ) {
    this.clusterManager = clusterManager
    this.daprManager = daprManager
    this.namespace = getProjectNamespaceName(configuration)
    this.helmManager = helmManager
    this.templateValues = {
      environment: configuration.environmentName,
      namespace: this.namespace,
      clusterVolume: boosterVolumeClaim.name,
    }
    this.logger = scopeLogger('DeployManager', logger)
  }

  /**
   * verify that helm is installed and if not tries to install it
   */
  public async ensureHelmIsReady(): Promise<void> {
    const l = scopeLogger('ensureHelmIsReady', this.logger)
    l.debug('Calling `helmManager.isVersion3()`')
    await this.helmManager.isVersion3()
  }

  /**
   * verify that Dapr is installed and if not tries to install it
   */
  public async ensureDaprExists(): Promise<void> {
    const l = scopeLogger('ensureDaprExists', this.logger)
    l.debug('Checking if `dapr` repo is installed')
    const repoInstalled = await this.helmManager.isRepoInstalled('dapr')
    if (!repoInstalled) {
      l.debug('Repo is not installed, installing')
      await this.helmManager.installRepo('dapr', this.DaprRepo)
    }
    l.debug('Checking if `dapr-operator` pod exists')
    const daprPod = await this.clusterManager.getPodFromNamespace(this.namespace, 'dapr-operator')
    if (!daprPod) {
      l.debug("Dapr pod doesn't exist, creating with helm")
      await this.helmManager.exec(`install dapr dapr/dapr --namespace ${this.namespace}`)
      l.debug('Waiting for pod to be ready')
      await this.clusterManager.waitForPodToBeReady(this.namespace, 'dapr-operator')
    }
  }

  /**
   *  verify that the event store is present and in a negative case, it tries to create one through Dapr Manager
   */
  public async ensureEventStoreExists(): Promise<void> {
    const l = scopeLogger('ensureEventStoreExists', this.logger)
    l.debug('Starting to configure event store')
    await this.daprManager.configureEventStore()
  }

  /**
   * check that the specified namespace exists and if not it tries to create it
   */
  public async ensureNamespaceExists(): Promise<void> {
    const l = scopeLogger('ensureNamespaceExists', this.logger)
    l.debug('Getting namespace', this.namespace)
    const currentNameSpace = await this.clusterManager.getNamespace(this.namespace)
    l.debug('getNamespace finished, I got:', currentNameSpace, ' -- will create new on undefined')
    const nameSpaceExists = currentNameSpace ?? (await this.clusterManager.createNamespace(this.namespace))
    if (!nameSpaceExists) {
      l.debug("Namespace didn't exist, throwing error....")
      throw new Error('Unable to create a namespace for your project, please check your Kubectl configuration')
    }
  }

  /**
   * verify that the specified Persistent Volume Claim and in a negative case it tries to create it
   */
  public async ensureVolumeClaimExists(): Promise<void> {
    const l = scopeLogger('ensureVolumeClaimExists', this.logger)
    l.debug('Getting volume claim')
    const clusterVolumeClaim = await this.clusterManager.getVolumeClaimFromNamespace(
      this.namespace,
      this.templateValues.clusterVolume
    )
    if (!clusterVolumeClaim) {
      l.debug("Couldn't get volume claim, applying template")
      const clusterResponse = await this.clusterManager.applyTemplate(boosterVolumeClaim.template, this.templateValues)
      if (clusterResponse.length == 0) {
        l.debug("Cluster didn't respond after applying template, throwing")
        throw new Error('Unable to create a volume claim for your project, please check your Kubectl configuration')
      }
    }
  }
  /**
   * verify that the upload service is running and in a negative case it tries to create it
   */
  public async ensureUploadServiceExists(): Promise<void> {
    const l = scopeLogger('ensureUploadServiceExists', this.logger)
    l.debug('ensuring service is ready')
    return await this.ensureServiceIsReady(uploadService)
  }

  /**
   * verify that the booster service is running and in a negative case it tries to create it
   */
  public async ensureBoosterServiceExists(): Promise<void> {
    const l = scopeLogger('ensureBoosterServiceExists', this.logger)
    l.debug('Ensuring service is ready')
    return await this.ensureServiceIsReady(boosterService)
  }

  /**
   * verify that the upload pod is running and in a negative case it tries to create it
   */
  public async ensureUploadPodExists(): Promise<void> {
    const l = scopeLogger('ensureUploadPodExists', this.logger)
    l.debug('Ensuring pod is ready')
    await this.ensurePodIsReady(uploaderPod)
    l.debug('Waiting for pod to be ready')
    await this.clusterManager.waitForPodToBeReady(this.namespace, uploaderPod.name)
  }

  /**
   * verify that the booster pod is running and in a negative case it tries to create it
   */
  public async ensureBoosterPodExists(): Promise<void> {
    const l = scopeLogger('ensureBoosterPodExists', this.logger)
    l.debug('Ensuring pod is ready')
    await this.ensurePodIsReady(boosterAppPod, true)
  }

  /**
   * upload all the user code into the cluster and create the express server index for the booster project
   */
  public async uploadUserCode(): Promise<void> {
    const l = scopeLogger('uploadUserCode', this.logger)
    l.debug('Waiting for service to be ready')
    const fileUploadService = await this.clusterManager.waitForServiceToBeReady(this.namespace, uploadService.name)
    l.debug('Creating zip file')
    const codeZipFile = await createProjectZipFile(l)
    l.debug('Uploading file')
    const fileUploadResponse = await uploadFile(l, fileUploadService?.ip, codeZipFile)
    if (fileUploadResponse.statusCode !== 200) {
      l.debug('Cannot upload code, throwing')
      throw new Error('Unable to upload your code, please check the fileuploader pod for more information')
    }
  }

  /**
   * deploy a booster app pod inside the cluster and get the booster app url from the cluster
   */
  public async deployBoosterApp(
    eventStoreHost: string,
    eventStoreUser: string,
    eventStorePassword: string
  ): Promise<string> {
    this.templateValues.dbHost = eventStoreHost
    this.templateValues.dbUser = eventStoreUser
    this.templateValues.dbPass = eventStorePassword
    const l = scopeLogger('deployBoosterApp', this.logger)
    l.debug('Ensuring booster pod exists')
    await this.ensureBoosterPodExists()
    l.debug('Waiting for pod to be ready')
    await this.clusterManager.waitForPodToBeReady(this.namespace, boosterAppPod.name)
    l.debug('Getting service ip')
    const service = await this.clusterManager.waitForServiceToBeReady(this.namespace, boosterService.name)
    const ip = service?.ip
    l.debug('Got ip', ip ?? 'UNDEFINED')
    return ip ?? ''
  }

  /**
   * delete Dapr services from cluster
   */
  public async deleteDapr(): Promise<void> {
    await this.daprManager.deleteDaprService()
  }

  /**
   * delete Redis event store from cluster if it was create automatically by booster during deploy
   */
  public async deleteRedis(): Promise<void> {
    await this.daprManager.deleteEventStore()
  }

  /**
   * delete all booster resources from the cluster
   */
  public async deleteAllResources(): Promise<void> {
    await this.clusterManager.deleteNamespace(this.namespace)
  }

  private async ensureServiceIsReady(template: Template): Promise<void> {
    const l = scopeLogger('ensureServiceIsReady', this.logger)
    l.debug('Getting service from namespace')
    const clusterService = await this.clusterManager.getServiceFromNamespace(this.namespace, template.name)
    if (!clusterService) {
      l.debug("Didn't get cluster service, applying template")
      await this.applyTemplate(template, this.templateValues)
    }
  }

  private async ensurePodIsReady(template: Template, forceRestart = false): Promise<void> {
    const l = scopeLogger('ensurePodIsReady', this.logger)
    l.debug('Getting pod from namespace')
    const clusterPod = await this.clusterManager.getPodFromNamespace(this.namespace, template.name)
    if (!clusterPod) {
      l.debug('No pod found, applying template')
      await this.applyTemplate(template, this.templateValues)
    } else if (forceRestart) {
      l.debug('Force restart found, applying template')
      this.templateValues.timestamp = Date.now().toString()
      await this.applyTemplate(template, this.templateValues)
    }
  }

  private async applyTemplate(template: Template, templateValues: TemplateValues): Promise<boolean> {
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
