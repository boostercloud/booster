import { K8sManagement } from './k8s-sdk/k8s-management'
import { BoosterConfig } from '@boostercloud/framework-types'
import { getProjectNamespaceName, createProjectZipFile, uploadFile, waitForIt } from './utils'
import { uploadService } from './templates/upload-service-template'
import { boosterVolumeClaim } from './templates/volume-claim-template'
import { boosterService } from './templates/booster-service-template'
import { Template, TemplateValues } from './templates/template-types'
import { uploaderPod } from './templates/file-uploader-app-template'
import { boosterAppPod } from './templates/booster-app-template'
import { HelmManager } from './helm-manager'
import { DaprManager } from './dapr-manager'
import fetch from 'node-fetch'
import { getLogger } from '@boostercloud/framework-common-helpers'
export class DeployManager {
  private namespace: string
  private templateValues: TemplateValues
  private DaprRepo = 'https://daprio.azurecr.io/helm/v1/repo'

  constructor(
    readonly config: BoosterConfig,
    readonly clusterManager: K8sManagement,
    readonly daprManager: DaprManager,
    readonly helmManager: HelmManager
  ) {
    this.namespace = getProjectNamespaceName(config)
    this.templateValues = {
      environment: config.environmentName,
      namespace: this.namespace,
      clusterVolume: boosterVolumeClaim.name,
      serviceType: 'LoadBalancer',
    }
  }

  /**
   * verify that helm is installed and if not tries to install it
   */
  public async ensureHelmIsReady(): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#ensureHelmIsReady')
    logger.debug('Calling `helmManager.isVersion3()`')
    await this.helmManager.isVersion3()
  }

  /**
   * verify that Dapr is installed and if not tries to install it
   */
  public async ensureDaprExists(): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#ensureDaprExists')
    logger.debug('Checking if `dapr` repo is installed')
    const repoInstalled = await this.helmManager.isRepoInstalled('dapr')
    if (!repoInstalled) {
      logger.debug('Repo is not installed, installing')
      await this.helmManager.installRepo('dapr', this.DaprRepo)
    }
    logger.debug('Checking if `dapr-operator` pod exists')
    const daprPod = await this.clusterManager.getPodFromNamespace(this.namespace, 'dapr-operator')
    if (!daprPod) {
      logger.debug("Dapr pod doesn't exist, creating with helm")
      await this.helmManager.exec(`install dapr dapr/dapr --namespace ${this.namespace}`)
      logger.debug('Waiting for pod to be ready')
      await this.clusterManager.waitForPodToBeReady(this.namespace, 'dapr-operator')
      await this.daprManager.allowDaprToReadSecrets()
    }
  }

  /**
   *  verify that the event store is present and in a negative case, it tries to create one through Dapr Manager
   */
  public async ensureEventStoreExists(): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#ensureEventStoreExists')
    logger.debug('Starting to configure event store')
    await this.daprManager.configureEventStore()
  }

  /**
   * check that the specified namespace exists and if not it tries to create it
   */
  public async ensureNamespaceExists(): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#ensureNamespaceExists')
    logger.debug('Getting namespace', this.namespace)
    const currentNameSpace = await this.clusterManager.getNamespace(this.namespace)
    logger.debug('getNamespace finished, I got:', currentNameSpace, ' -- will create new on undefined')
    const nameSpaceExists = currentNameSpace ?? (await this.clusterManager.createNamespace(this.namespace))
    if (!nameSpaceExists) {
      logger.debug("Namespace didn't exist, throwing error....")
      throw new Error('Unable to create a namespace for your project, please check your Kubectl configuration')
    }
  }

  /**
   * verify that the specified Persistent Volume Claim and in a negative case it tries to create it
   */
  public async ensureVolumeClaimExists(): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#ensureVolumeClaimExists')
    logger.debug('Getting volume claim')
    const clusterVolumeClaim = await this.clusterManager.getVolumeClaimFromNamespace(
      this.namespace,
      this.templateValues.clusterVolume
    )
    if (!clusterVolumeClaim) {
      logger.debug("Couldn't get volume claim, applying template")
      const clusterResponse = await this.clusterManager.applyTemplate(boosterVolumeClaim.template, this.templateValues)
      if (clusterResponse.length == 0) {
        logger.debug("Cluster didn't respond after applying template, throwing")
        throw new Error('Unable to create a volume claim for your project, please check your Kubectl configuration')
      }
    }
  }

  /**
   * Set the type for services in case you are running the cluster locally or in a cloud provider
   */
  public async setServiceType(): Promise<void> {
    const mainNode = await this.clusterManager.getMainNode()
    if (mainNode?.name === 'minikube') {
      this.templateValues.serviceType = 'NodePort'
    }
  }

  /**
   * verify that the upload service is running and in a negative case it tries to create it
   */
  public async ensureUploadServiceExists(): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#ensureUploadServiceExists')
    logger.debug('ensuring service is ready')
    return await this.ensureServiceIsReady(uploadService)
  }

  /**
   * verify that the booster service is running and in a negative case it tries to create it
   */
  public async ensureBoosterServiceExists(): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#ensureBoosterServiceExists')
    logger.debug('Ensuring service is ready')
    return await this.ensureServiceIsReady(boosterService)
  }

  /**
   * verify that the upload pod is running and in a negative case it tries to create it
   */
  public async ensureUploadPodExists(): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#ensureUploadPodExists')
    logger.debug('Ensuring pod is ready')
    await this.ensurePodIsReady(uploaderPod)
    logger.debug('Waiting for pod to be ready')
    await this.clusterManager.waitForPodToBeReady(this.namespace, uploaderPod.name)
  }

  /**
   * verify that the booster pod is running and in a negative case it tries to create it
   */
  public async ensureBoosterPodExists(): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#ensureBoosterPodExists')
    logger.debug('Ensuring pod is ready')
    await this.ensurePodIsReady(boosterAppPod, true)
  }

  /**
   * upload all the user code into the cluster and create the express server index for the booster project
   */
  public async uploadUserCode(): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#uploadUserCode')
    logger.debug('Waiting for Upload service to be ready')
    const fileUploadService = await this.clusterManager.waitForServiceToBeReady(this.namespace, uploadService.name)
    logger.debug('Creating zip file')
    const codeZipFile = await createProjectZipFile(this.config)
    const fileUploadServiceAddress = fileUploadService?.port
      ? `${fileUploadService?.ip}:${fileUploadService?.port}`
      : fileUploadService?.ip
    logger.debug('Waiting for Upload service to be accesible')
    await this.waitForServiceToBeAvailable(fileUploadServiceAddress)
    logger.debug('Uploading file')
    const fileUploadResponse = await uploadFile(this.config, fileUploadServiceAddress, codeZipFile)
    if (fileUploadResponse.statusCode !== 200) {
      logger.debug('Cannot upload code, throwing')
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
    const logger = getLogger(this.config, 'DeployManager#deployBoosterApp')
    logger.debug('Ensuring booster pod exists')
    await this.ensureBoosterPodExists()
    logger.debug('Waiting for pod to be ready')
    await this.clusterManager.waitForPodToBeReady(this.namespace, boosterAppPod.name)
    logger.debug('Getting service ip')
    const service = await this.clusterManager.waitForServiceToBeReady(this.namespace, boosterService.name)
    const boosterServiceAddress = service?.port ? `${service?.ip}:${service.port}` : service?.ip
    logger.debug('Got booster service address', boosterServiceAddress ?? 'UNDEFINED')
    return boosterServiceAddress ?? ''
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

  private async waitForServiceToBeAvailable(url: string | undefined, timeout = 180000): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#waitForServiceToBeAvailable')
    if (!url) {
      throw new Error('Service Url not valid')
    }
    await waitForIt(
      () => {
        logger.debug('Getting service from namespace')
        return fetch(`http://${url}`)
          .then((response) => {
            return response.status
          })
          .catch(() => {
            return 0
          })
      },
      (requestStatus) => {
        return requestStatus === 200
      },
      'Unable to get the services in available status',
      timeout
    )
  }

  private async ensureServiceIsReady(template: Template): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#ensureServiceIsReady')
    logger.debug('Getting service from namespace')
    const clusterService = await this.clusterManager.getServiceFromNamespace(this.namespace, template.name)
    if (!clusterService) {
      logger.debug("Didn't get cluster service, applying template")
      await this.applyTemplate(template, this.templateValues)
    }
  }

  private async ensurePodIsReady(template: Template, forceRestart = false): Promise<void> {
    const logger = getLogger(this.config, 'DeployManager#ensurePodIsReady')
    logger.debug('Getting pod from namespace')
    const clusterPod = await this.clusterManager.getPodFromNamespace(this.namespace, template.name)
    if (!clusterPod) {
      logger.debug('No pod found, applying template')
      await this.applyTemplate(template, this.templateValues)
    } else if (forceRestart) {
      logger.debug('Force restart found, applying template')
      this.templateValues.timestamp = Date.now().toString()
      await this.applyTemplate(template, this.templateValues)
    }
  }

  private async applyTemplate(template: Template, templateValues: TemplateValues): Promise<boolean> {
    const logger = getLogger(this.config, 'DeployManager#applyTemplate')
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
