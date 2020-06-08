import { CoreV1Api, KubeConfig } from '@kubernetes/client-node'
import { Pod } from './models/pod'
import { Namespace } from './models/namespace'

export class K8sManagement {
  private kube: KubeConfig
  private k8sClient: CoreV1Api

  constructor() {
    this.kube = new KubeConfig()
    this.kube.loadFromDefault()
    this.k8sClient = this.kube.makeApiClient(CoreV1Api)
  }

  public async getAllPodsInNamespace(namespace: string): Promise<Array<Pod>> {
    const response = await this.unwrapResponse(this.k8sClient.listNamespacedPod(namespace))
    return response.items.map((item) => {
      return {
        name: item.metadata?.name,
        namespace: item.metadata?.namespace ?? 'default',
        labels: item.metadata?.labels ?? {},
        status: item.status?.phase,
        nodeName: item.spec?.nodeName,
        ip: item.status?.podIP,
      }
    })
  }

  public async getAllNamespaces(): Promise<Array<Namespace>> {
    const response = await this.unwrapResponse(this.k8sClient.listNamespace())
    return response.items.map((item) => {
      return {
        name: item.metadata?.name,
        status: item.status?.phase,
        labels: item.metadata?.labels ?? {},
      }
    })
  }

  public async createNamespace(name: string): Promise<boolean> {
    const namespace = {
      metadata: {
        name: name,
      },
    }
    return this.k8sClient.createNamespace(namespace).then(
      () => {
        return true
      },
      () => {
        return false
      }
    )
  }

  public async deleteNamespace(name: string): Promise<boolean> {
    return this.k8sClient.deleteNamespace(name).then(
      () => {
        return true
      },
      () => {
        return false
      }
    )
  }

  public async getNamespace(name: string): Promise<Namespace | null> {
    const namespaces = await this.getAllNamespaces()
    return (
      namespaces.find((namespace) => {
        return namespace?.name === name
      }) ?? null
    )
  }

  public async getPodFromNamespace(name: string, namespace: string): Promise<Pod | null> {
    const pods = await this.getAllPodsInNamespace(namespace)
    return (
      pods.find((pod) => {
        return pod?.name === name
      }) ?? null
    )
  }

  private async unwrapResponse<TType>(wrapped: Promise<{ body: TType }>): Promise<TType> {
    const unwrapped = await wrapped
    return unwrapped.body
  }
}
