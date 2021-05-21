import { KubeConfig, CoreV1Api, V1ServiceList, V1PodList } from '@kubernetes/client-node'
import { kubernetesNamespace } from './constants'
import { expect } from '../../helper/expect'

export class K8SManager {
  private kube: KubeConfig
  private k8sClient: CoreV1Api

  constructor() {
    this.kube = new KubeConfig()
    this.kube.loadFromDefault()
    this.k8sClient = this.kube.makeApiClient(CoreV1Api)
  }

  public async getKubernetesServices(): Promise<V1ServiceList> {
    const serviceRequest = await this.k8sClient.listNamespacedService(kubernetesNamespace)

    expect(serviceRequest.body.items).not.to.be.undefined
    return serviceRequest.body
  }

  public async getKubernetesPods(): Promise<V1PodList> {
    const podsRequest = await this.k8sClient.listNamespacedPod(kubernetesNamespace)

    expect(podsRequest.body.items).not.to.be.undefined
    return podsRequest.body
  }
}
