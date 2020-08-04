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
   *
   * @returns {Promise<boolean>}
   * @memberof DeployManager
   */
  public async verifyHelm(): Promise<boolean> {
    await this.helmManager.isVersion3().catch((err) => {
      return Promise.reject(err.toString())
    })
    return true
  }

  /**
   * verify that Dapr is installed and if not tries to install it

   *
   * @returns {Promise<void>}
   * @memberof DeployManager
   */
  public async verifyDapr(): Promise<void> {
    try {
      const repoInstalled = await this.helmManager.isRepoInstalled('dapr')
      if (!repoInstalled) {
        await this.helmManager.installRepo('dapr', this.DaprRepo)
      }
      const daprPod = await this.clusterManager.getPodFromNamespace(this.namespace, 'dapr-operator')
      if (!daprPod) {
        await this.helmManager.exec(`install dapr dapr/dapr --namespace ${this.namespace}`)
        await this.clusterManager.waitForPodToBeReady(this.namespace, 'dapr-operator')
      }
    } catch (err) {
      return Promise.reject(err.toString())
    }
  }

  /**
   *  verify that the event store is present and in a negative case, it tries to create one through Dapr Manager
   *
   * @returns {Promise<void>}
   * @memberof DeployManager
   */
  public async verifyEventStore(): Promise<void> {
    await this.daprManager.configureEventStore().catch((err) => {
      return Promise.reject(err.toString())
    })
  }

  /**
   * check that the specified namespace exists and if not it tries to create it
   *
   * @returns {Promise<void>}
   * @memberof DeployManager
   */
  public async verifyNamespace(): Promise<void> {
    try {
      const clusterNamespace = await this.clusterManager.getNamespace(this.namespace)
      if (!clusterNamespace) {
        const clusterResponse = await this.clusterManager.createNamespace(this.namespace)
        if (!clusterResponse) {
          throw new Error('Unable to create a namespace for your project, please check your Kubectl configuration')
        }
      }
    } catch (err) {
      return Promise.reject(err)
    }
  }

  /**
   * verify that the specified Persistent Volume Claim and in a negative case it tries to create it
   *
   * @returns {Promise<void>}
   * @memberof DeployManager
   */
  public async verifyVolumeClaim(): Promise<void> {
    try {
      const clusterVolumeClaim = await this.clusterManager.getVolumeClaimFromNamespace(
        this.namespace,
        this.templateValues.clusterVolume
      )
      if (!clusterVolumeClaim) {
        const clusterResponse = await this.clusterManager.applyTemplate(
          boosterVolumeClaim.template,
          this.templateValues
        )
        if (clusterResponse.length == 0) {
          return Promise.reject(
            'Unable to create a volume claim for your project, please check your Kubectl configuration'
          )
        }
      }
    } catch (err) {
      return Promise.reject(err.toString())
    }
  }
  /**
   * verify that the upload service is running and in a negative case it tries to create it
   *
   * @returns {Promise<void>}
   * @memberof DeployManager
   */
  public async verifyUploadService(): Promise<void> {
    return await this.verifyService(uploadService)
  }

  /**
   * verify that the booster service is running and in a negative case it tries to create it
   *
   * @returns {Promise<void>}
   * @memberof DeployManager
   */
  public async verifyBoosterService(): Promise<void> {
    return await this.verifyService(boosterService)
  }

  /**
   * verify that the upload pod is running and in a negative case it tries to create it
   *
   * @returns {Promise<void>}
   * @memberof DeployManager
   */
  public async verifyUploadPod(): Promise<void> {
    try {
      await this.verifyPod(uploaderPod)
      await this.clusterManager.waitForPodToBeReady(this.namespace, uploaderPod.name)
    } catch (err) {
      return Promise.reject(err.toString())
    }
  }

  /**
   * verify that the booster pod is running and in a negative case it tries to create it
   *
   * @returns {Promise<void>}
   * @memberof DeployManager
   */
  public async verifyBoosterPod(): Promise<void> {
    try {
      await this.verifyPod(boosterAppPod, true)
    } catch (err) {
      return Promise.reject(err.toString())
    }
  }

  /**
   * upload all the user code into the cluster and create the express server index for the booster project
   *
   * @returns {Promise<boolean>}
   * @memberof DeployManager
   */
  public async uploadUserCode(): Promise<void> {
    try {
      const fileUploadService = await this.clusterManager.waitForServiceToBeReady(this.namespace, uploadService.name)
      const codeZipFile = await createProjectZipFile()
      const indexFile = await createIndexFile()
      const fileUploadResponse = await uploadFile(fileUploadService?.ip ?? '', codeZipFile)
      if (fileUploadResponse.statusCode !== 200) {
        return Promise.reject('Unable to upload your code, please check the fileuploader pod for more information')
      }
      const indexUploadResult = await uploadFile(fileUploadService?.ip ?? '', indexFile)
      if (indexUploadResult.statusCode !== 200) {
        return Promise.reject('Unable to upload your code, please check the fileuploader pod for more information')
      }
    } catch (err) {
      return Promise.reject(err.toString())
    }
  }

  /**
   * deploy a booster app pod inside the cluster and get the booster app url from the cluster
   *
   * @returns {Promise<string>}
   * @memberof DeployManager
   */
  public async deployBoosterApp(): Promise<string> {
    try {
      await this.verifyBoosterPod()
      await this.clusterManager.waitForPodToBeReady(this.namespace, boosterAppPod.name)
      const service = await this.clusterManager.waitForServiceToBeReady(this.namespace, boosterService.name)
      return service?.ip ?? ''
    } catch (err) {
      return Promise.reject(err.toString())
    }
  }

  /**
   * delete Dapr services from cluster
   *
   * @returns {Promise<void>}
   * @memberof DeployManager
   */
  public async deleteDapr(): Promise<void> {
    await this.daprManager.deleteDaprService().catch((err) => {
      return Promise.reject(err.toString())
    })
  }

  /**
   * delete Redis event store from cluster if it was create automatically by booster during deploy
   *
   * @returns {Promise<void>}
   * @memberof DeployManager
   */
  public async deleteRedis(): Promise<void> {
    await this.daprManager.deleteEventStore().catch((err) => {
      return Promise.reject(err.toString())
    })
  }

  /**
   * delete all booster resources from the cluster
   *
   * @memberof DeployManager
   */
  public async deleteAllResources(): Promise<void> {
    await this.clusterManager.deleteNamespace(this.namespace).catch((err) => {
      return Promise.reject(err.toString())
    })
  }

  private async verifyService(template: Template): Promise<void> {
    try {
      const clusterService = await this.clusterManager.getServiceFromNamespace(this.namespace, template.name)
      if (!clusterService) {
        await this.applyTemplate(template, this.templateValues)
      }
    } catch (error) {
      return Promise.reject(error.toString())
    }
  }

  private async verifyPod(template: Template, forceRestart = false): Promise<void> {
    try {
      const clusterPod = await this.clusterManager.getPodFromNamespace(this.namespace, template.name)
      if (!clusterPod) {
        await this.applyTemplate(template, this.templateValues)
      } else if (forceRestart) {
        this.templateValues.timestamp = Date.now().toString()
        await this.applyTemplate(template, this.templateValues)
      }
    } catch (err) {
      return Promise.reject(err.toString())
    }
  }

  private async applyTemplate(template: Template, templateValues: TemplateValues): Promise<boolean> {
    try {
      const clusterResponse = await this.clusterManager.applyTemplate(template.template, templateValues)
      if (clusterResponse.length == 0) {
        return Promise.reject(
          `Unable to create ${template.name} service for your project, please check your Kubectl configuration`
        )
      }
      return true
    } catch (err) {
      return Promise.reject(err.toString())
    }
  }
}
