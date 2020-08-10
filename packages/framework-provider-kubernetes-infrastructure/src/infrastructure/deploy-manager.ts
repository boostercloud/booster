import { K8sManagement } from './k8s-sdk/K8sManagement'
import { BoosterConfig } from '@boostercloud/framework-types'
import { getProjectNamespaceName, createProjectZipFile, createIndexFile, uploadFile } from './utils'
import { uploadService } from './templates/uploadService'
import { boosterVolumeClaim } from './templates/boosterVolumeClaim'
import { boosterService } from './templates/boosterService'
import { Template, TemplateValues } from './templates/templateInterface'
import { uploaderPod } from './templates/fileUploader'
import { boosterAppPod } from './templates/boosterApp'
import { HelmManager } from './helm-manager'
import { DaprManager } from './dapr-manager'

export class DeployManager {
  private clusterManager: K8sManagement
  private namespace: string
  private templateValues: TemplateValues
  private helmManager: HelmManager
  private DaprRepo = 'https://daprio.azurecr.io/helm/v1/repo'
  private daprManager: DaprManager

  constructor(
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
  }

  /**
   * verify that helm is installed and if not tries to install it
   */
  public async ensureHelmIsReady(): Promise<void> {
    await this.helmManager.isVersion3()
  }

  /**
   * verify that Dapr is installed and if not tries to install it
   */
  public async ensureDaprExists(): Promise<void> {
    const repoInstalled = await this.helmManager.isRepoInstalled('dapr')
    if (!repoInstalled) {
      await this.helmManager.installRepo('dapr', this.DaprRepo)
    }
    const daprPod = await this.clusterManager.getPodFromNamespace(this.namespace, 'dapr-operator')
    if (!daprPod) {
      await this.helmManager.exec(`install dapr dapr/dapr --namespace ${this.namespace}`)
      await this.clusterManager.waitForPodToBeReady(this.namespace, 'dapr-operator')
    }
  }

  /**
   *  verify that the event store is present and in a negative case, it tries to create one through Dapr Manager
   */
  public async ensureEventStoreExists(): Promise<void> {
    await this.daprManager.configureEventStore()
  }

  /**
   * check that the specified namespace exists and if not it tries to create it
   */
  public async ensureNamespaceExists(): Promise<void> {
    const currentNameSpace = await this.clusterManager.getNamespace(this.namespace)
    const nameSpaceExists = currentNameSpace ?? (await this.clusterManager.createNamespace(this.namespace))
    if (!nameSpaceExists)
      throw new Error('Unable to create a namespace for your project, please check your Kubectl configuration')
  }

  /**
   * verify that the specified Persistent Volume Claim and in a negative case it tries to create it
   */
  public async ensureVolumeClaimExists(): Promise<void> {
    const clusterVolumeClaim = await this.clusterManager.getVolumeClaimFromNamespace(
      this.namespace,
      this.templateValues.clusterVolume
    )
    if (!clusterVolumeClaim) {
      const clusterResponse = await this.clusterManager.applyTemplate(boosterVolumeClaim.template, this.templateValues)
      if (clusterResponse.length == 0) {
        throw new Error('Unable to create a volume claim for your project, please check your Kubectl configuration')
      }
    }
  }
  /**
   * verify that the upload service is running and in a negative case it tries to create it
   */
  public async ensureUploadServiceExists(): Promise<void> {
    return await this.verifyService(uploadService)
  }

  /**
   * verify that the booster service is running and in a negative case it tries to create it
   */
  public async ensureBoosterServiceExists(): Promise<void> {
    return await this.verifyService(boosterService)
  }

  /**
   * verify that the upload pod is running and in a negative case it tries to create it
   */
  public async ensureUploadPodExists(): Promise<void> {
    await this.verifyPod(uploaderPod)
    await this.clusterManager.waitForPodToBeReady(this.namespace, uploaderPod.name)
  }

  /**
   * verify that the booster pod is running and in a negative case it tries to create it
   */
  public async ensureBoosterPodExists(): Promise<void> {
    await this.verifyPod(boosterAppPod, true)
  }

  /**
   * upload all the user code into the cluster and create the express server index for the booster project
   */
  public async uploadUserCode(): Promise<void> {
    const fileUploadService = await this.clusterManager.waitForServiceToBeReady(this.namespace, uploadService.name)
    const codeZipFile = await createProjectZipFile()
    const indexFile = await createIndexFile()
    const fileUploadResponse = await uploadFile(fileUploadService?.ip, codeZipFile)
    if (fileUploadResponse.statusCode !== 200) {
      throw new Error('Unable to upload your code, please check the fileuploader pod for more information')
    }
    const indexUploadResult = await uploadFile(fileUploadService?.ip ?? '', indexFile)
    if (indexUploadResult.statusCode !== 200) {
      throw new Error('Unable to upload your code, please check the fileuploader pod for more information')
    }
  }

  /**
   * deploy a booster app pod inside the cluster and get the booster app url from the cluster
   */
  public async deployBoosterApp(): Promise<string> {
    await this.ensureBoosterPodExists()
    await this.clusterManager.waitForPodToBeReady(this.namespace, boosterAppPod.name)
    const service = await this.clusterManager.waitForServiceToBeReady(this.namespace, boosterService.name)
    return service?.ip ?? ''
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

  private async verifyService(template: Template): Promise<void> {
    const clusterService = await this.clusterManager.getServiceFromNamespace(this.namespace, template.name)
    if (!clusterService) {
      await this.applyTemplate(template, this.templateValues)
    }
  }

  private async verifyPod(template: Template, forceRestart = false): Promise<void> {
    const clusterPod = await this.clusterManager.getPodFromNamespace(this.namespace, template.name)
    if (!clusterPod) {
      await this.applyTemplate(template, this.templateValues)
    } else if (forceRestart) {
      this.templateValues.timestamp = Date.now().toString()
      await this.applyTemplate(template, this.templateValues)
    }
  }

  private async applyTemplate(template: Template, templateValues: TemplateValues): Promise<boolean> {
    const clusterResponse = await this.clusterManager.applyTemplate(template.template, templateValues)
    if (clusterResponse.length == 0) {
      throw new Error(
        `Unable to create ${template.name} service for your project, please check your Kubectl configuration`
      )
    }
    return true
  }
}
