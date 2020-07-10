import { CoreV1Api, KubeConfig, KubernetesObject, KubernetesObjectApi } from '@kubernetes/client-node'
import { Node, Namespace, Pod, TemplateValues } from './models'
import * as Mustache from 'mustache'
import { boosterApp } from '../templates/boosterApp'
import { fileUploader } from '../templates/fileUploader'
import { boosterVolumeClaim } from '../templates/boosterVolumeClaim'
import { safeLoadAll } from 'js-yaml'
import { sleep } from '../utils'

export class K8sManagement {
  private kube: KubeConfig
  private k8sClient: CoreV1Api
  private kubernetesTemplates = new Map([
    ['boosterApp', boosterApp],
    ['fileUploader', fileUploader],
    ['boosterVolumeClaim', boosterVolumeClaim],
  ])

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

  public async getNamespace(name: string): Promise<Namespace | undefined> {
    const namespaces = await this.getAllNamespaces()
    return namespaces.find((namespace) => {
      return namespace?.name === name
    })
  }

  public async getPodFromNamespace(name: string, namespace: string): Promise<Pod | undefined> {
    const pods = await this.getAllPodsInNamespace(namespace)
    return pods.find((pod) => {
      return pod?.labels?.['app'] === name
    })
  }

  public async getAllNodesInCluster(): Promise<Array<Node>> {
    const response = await this.unwrapResponse(this.k8sClient.listNode())
    return response.items.map((item) => {
      const internalIP =
        item.status?.addresses?.find((address) => {
          return address.type === 'InternalIP'
        }) ?? null
      let mainNode = false
      const labels = item.metadata?.labels
      if (labels) {
        if ('node-role.kubernetes.io/master' in labels) {
          mainNode = true
        }
      }

      return {
        name: item.metadata?.name,
        labels: item.metadata?.labels ?? {},
        ip: internalIP?.address,
        mainNode: mainNode,
      }
    })
  }

  public async getAllNodesWithOpenWhiskRole(role: string): Promise<Array<Node>> {
    const clusterNodes = await this.getAllNodesInCluster()
    return clusterNodes.filter((node) => {
      if (node.labels && 'openwhisk-role' in node.labels) {
        return node.labels['openwhisk-role'] === role
      } else {
        return false
      }
    })
  }

  public async getMainNode(): Promise<Node | null> {
    const clusterNodes = await this.getAllNodesInCluster()
    return clusterNodes.find((node) => node.mainNode) ?? null
  }

  public async applyTemplate(templateName: string, templateData: TemplateValues): Promise<Array<KubernetesObject>> {
    const client = KubernetesObjectApi.makeApiClient(this.kube)
    const template = this.kubernetesTemplates.get(templateName)
    if (!template) {
      throw new Error('Kubernetes template for deploy not found')
    }
    const renderedYaml = Mustache.render(template, templateData)
    const specs = safeLoadAll(renderedYaml)
    const validSpecs = specs.filter((s) => s && s.kind && s.metadata)
    const created: KubernetesObject[] = []
    for (const spec of validSpecs) {
      spec.metadata = spec.metadata || {}
      spec.metadata.annotations = spec.metadata.annotations || {}
      delete spec.metadata.annotations['kubectl.kubernetes.io/last-applied-configuration']
      spec.metadata.annotations['kubectl.kubernetes.io/last-applied-configuration'] = JSON.stringify(spec)
      try {
        await client.read(spec)
        const response = await client.patch(spec)
        created.push(response.body)
      } catch (e) {
        const response = await client.create(spec)
        created.push(response.body)
      }
    }
    return created
  }

  public async waitForPodToBeReady(namespace: string, podName: string, timeout = 180): Promise<boolean> {
    let waitedTime = 0
    // eslint-disable-next-line no-constant-condition
    while (true) {
      const podInfo = await this.getPodFromNamespace(podName, namespace)
      if (podInfo?.status != 'Running') {
        console.log(' ', podInfo?.status)
        if (waitedTime >= timeout) {
          return false
        } else {
          waitedTime += 1
          await sleep(1000)
        }
      } else {
        return true
      }
    }
  }

  private async unwrapResponse<TBody>(wrapped: Promise<{ body: TBody }>): Promise<TBody> {
    const unwrapped = await wrapped
    return unwrapped.body
  }
}
