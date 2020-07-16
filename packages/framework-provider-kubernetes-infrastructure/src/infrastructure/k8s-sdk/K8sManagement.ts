import { CoreV1Api, KubeConfig, KubernetesObject, KubernetesObjectApi } from '@kubernetes/client-node'
import { Node, Namespace, Pod, Service, VolumeClaim, Secret } from './models'
import * as Mustache from 'mustache'
import { safeLoadAll } from 'js-yaml'
import { waitForIt } from '../utils'
import { TemplateValues } from '../templates/templateInterface'
import util = require('util')
const exec = util.promisify(require('child_process').exec)

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

  public async getAllServicesInNamespace(namespace: string): Promise<Array<Service>> {
    const response = await this.unwrapResponse(this.k8sClient.listNamespacedService(namespace))
    return response.items.map((item) => {
      return {
        name: item.metadata?.name,
        namespace: item.metadata?.namespace ?? 'default',
        labels: item.metadata?.labels ?? {},
        ip: item.status?.loadBalancer?.ingress?.[0].ip ? item.status.loadBalancer.ingress[0].ip : '',
      }
    })
  }

  public async getAllVolumeClaimFromNamespace(namespace: string): Promise<Array<VolumeClaim>> {
    const response = await this.unwrapResponse(this.k8sClient.listPersistentVolumeClaimForAllNamespaces())
    return response.items
      .filter((item) => item.metadata?.namespace === namespace)
      .map((item) => {
        return {
          name: item.metadata?.name,
          status: item.status?.phase,
          labels: item.metadata?.labels ?? {},
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

  public async getPodFromNamespace(namespace: string, podName: string): Promise<Pod | undefined> {
    const pods = await this.getAllPodsInNamespace(namespace)
    return pods.find((pod) => {
      return pod?.labels?.['app'] === podName
    })
  }

  public async getServiceFromNamespace(namespace: string, serviceName: string): Promise<Pod | undefined> {
    const pods = await this.getAllServicesInNamespace(namespace)
    return pods.find((pod) => {
      return pod?.labels?.['app'] === serviceName
    })
  }

  public async getVolumeClaimFromNamespace(namespace: string, volumeClaim: string): Promise<VolumeClaim | undefined> {
    const pods = await this.getAllVolumeClaimFromNamespace(namespace)
    return pods.find((pod) => {
      return pod?.name === volumeClaim
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

  public async getMainNode(): Promise<Node | null> {
    const clusterNodes = await this.getAllNodesInCluster()
    return clusterNodes.find((node) => node.mainNode) ?? null
  }

  public async applyTemplate(template: string, templateData: TemplateValues): Promise<Array<KubernetesObject>> {
    const renderedYaml = Mustache.render(template, templateData)
    return await this.applyYamlString(renderedYaml)
  }

  public async applyYamlString(yaml: string): Promise<Array<KubernetesObject>> {
    const client = KubernetesObjectApi.makeApiClient(this.kube)
    const specs = safeLoadAll(yaml)
    const validSpecs = specs.filter((s) => s && s.kind && s.metadata)
    const created: KubernetesObject[] = []
    for (const spec of validSpecs) {
      spec.metadata = spec.metadata || {}
      spec.metadata.annotations = spec.metadata.annotations || {}
      delete spec.metadata.annotations['kubectl.kubernetes.io/last-applied-configuration']
      spec.metadata.annotations['kubectl.kubernetes.io/last-applied-configuration'] = JSON.stringify(spec)
      try {
        await client.read(spec)
        const response = await client.replace(spec)
        created.push(response.body)
      } catch (e) {
        const response = await client.create(spec)
        created.push(response.body)
      }
    }
    return created
  }

  public waitForPodToBeReady(namespace: string, podName: string, timeout = 180000): Promise<Pod | undefined> {
    return waitForIt(
      () => this.getPodFromNamespace(namespace, podName),
      (podInfo) => podInfo?.status === 'Running',
      `Unable to get the pod ${podName} in status Running, please check your cluster for more information`,
      timeout
    )
  }

  public waitForServiceToBeReady(
    namespace: string,
    serviceName: string,
    timeout = 180000
  ): Promise<Service | undefined> {
    return waitForIt(
      () => this.getServiceFromNamespace(namespace, serviceName),
      (serviceInfo) => serviceInfo?.ip !== '',
      `Unable to get the service ${serviceName} in status Running, please check your cluster for more information`,
      timeout
    )
  }

  public async getSecret(namespace: string, secretName: string): Promise<Secret | undefined> {
    const secret = await this.unwrapResponse(this.k8sClient.readNamespacedSecret(secretName, namespace))
    if (!secret) {
      return
    }
    return {
      name: secret.metadata?.name,
      data: secret.data,
    }
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public async execRawCommand(command: string): Promise<any> {
    return exec(`kubectl ${command}`)
  }

  private async unwrapResponse<TBody>(wrapped: Promise<{ body: TBody }>): Promise<TBody> {
    const unwrapped = await wrapped
    return unwrapped.body
  }
}
