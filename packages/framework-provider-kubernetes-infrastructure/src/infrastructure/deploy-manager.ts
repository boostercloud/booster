/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { K8sManagement } from './k8s-sdk/K8sManagement'
import { BoosterConfig } from '@boostercloud/framework-types'
import { getProjectNamespaceName, createProjectZipFile, createIndexFile, uploadFile } from './utils'
import { uploadService } from './templates/uploadService'
import { boosterVolumeClaim } from './templates/boosterVolumeClaim'
import { boosterService } from './templates/boosterService'
import { Template, TemplateValues } from './templates/templateInterface'
import { uploaderPod } from './templates/fileUploader'
import { boosterAppPod } from './templates/boosterApp'

export class DeployManager {
  private clusterManager: K8sManagement
  private namespace: string
  private templateValues: TemplateValues

  constructor(configuration: BoosterConfig) {
    this.clusterManager = new K8sManagement()
    this.namespace = getProjectNamespaceName(configuration)
    this.templateValues = {
      environment: configuration.environmentName,
      namespace: this.namespace,
      clusterVolume: boosterVolumeClaim.name,
    }
  }

  public async verifyNamespace() {
    const clusterNamespace = await this.clusterManager.getNamespace(this.namespace)
    if (!clusterNamespace) {
      const clusterResponse = await this.clusterManager.createNamespace(this.namespace)
      if (!clusterResponse) {
        throw new Error('Unable to create a namespace for your project, please check your Kubectl configuration')
      }
    }
  }

  public async verifyUploadService() {
    await this.verifyService(uploadService)
  }

  public async verifyBoosterService() {
    await this.verifyService(boosterService)
  }

  public async verifyUploadPod() {
    await this.verifyPod(uploaderPod)
  }

  public async verifyBoosterPod() {
    await this.verifyPod(boosterAppPod, true)
  }

  public async uploadUserCode() {
    await this.clusterManager.waitForPodToBeReady(this.namespace, uploaderPod.name)
    const fileUploadService = await this.clusterManager.waitForServiceToBeReady(this.namespace, uploadService.name)
    const codeZipFile = await createProjectZipFile()
    const indexFile = await createIndexFile()
    const fileUploadResponse = await uploadFile(fileUploadService?.ip ?? '', codeZipFile)
    if (fileUploadResponse.statusCode !== 200) {
      throw new Error('Unable to upload your code, please check the fileuploader pod for more information')
    }
    const indexUploadResult = await uploadFile(fileUploadService?.ip ?? '', indexFile)
    if (indexUploadResult.statusCode !== 200) {
      throw new Error('Unable to upload your code, please check the fileuploader pod for more information')
    }
  }

  public async deployBoosterApp(): Promise<string> {
    await this.verifyBoosterPod()
    await this.clusterManager.waitForPodToBeReady(this.namespace, boosterAppPod.name)
    const service = await this.clusterManager.waitForServiceToBeReady(this.namespace, boosterService.name)
    return service?.ip ?? ''
  }

  private async verifyService(template: Template) {
    const clusterService = await this.clusterManager.getServiceFromNamespace(this.namespace, template.name)
    if (!clusterService) {
      await this.applyTemplate(template, this.templateValues)
    }
  }

  private async verifyPod(template: Template, forceRestart = false) {
    const clusterPod = await this.clusterManager.getPodFromNamespace(this.namespace, template.name)
    if (!clusterPod) {
      await this.applyTemplate(template, this.templateValues)
    } else if (forceRestart) {
      this.templateValues.timestamp = Date.now().toString()
      await this.applyTemplate(template, this.templateValues)
    }
  }

  private async applyTemplate(template: Template, templateValues: TemplateValues) {
    const clusterResponse = await this.clusterManager.applyTemplate(template.template, templateValues)
    if (!clusterResponse) {
      throw new Error(
        `Unable to create ${template.name} service for your project, please check your Kubectl configuration`
      )
    }
  }
}
