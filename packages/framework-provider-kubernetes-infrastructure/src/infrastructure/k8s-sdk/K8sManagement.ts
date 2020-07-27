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

  /**
   * Get a list including all available pods in an specific namespace
   *
   * @param {string} namespace
   * @returns {Promise<Array<Pod>>}
   * @memberof K8sManagement
   */
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

  /**
   * Get a list including all available services in an specific namespace
   *
   * @param {string} namespace
   * @returns {Promise<Array<Service>>}
   * @memberof K8sManagement
   */
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

  /**
   * get a list including all Persistent Volume Claim in an specific namespace
   *
   * @param {string} namespace
   * @returns {Promise<Array<VolumeClaim>>}
   * @memberof K8sManagement
   */
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

  /**
   * get a list including all namespaces inside your cluster
   *
   * @returns {Promise<Array<Namespace>>}
   * @memberof K8sManagement
   */
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

  /**
   * create a namespace inside your cluster
   *
   * @param {string} name
   * @returns {Promise<boolean>}
   * @memberof K8sManagement
   */
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

  /**
   * delete an existing namespace inside your cluster
   *
   * @param {string} name
   * @returns {Promise<boolean>}
   * @memberof K8sManagement
   */
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

  /**
   * get the information from a specific namespace inside your cluster
   *
   * @param {string} name
   * @returns {(Promise<Namespace | undefined>)}
   * @memberof K8sManagement
   */
  public async getNamespace(name: string): Promise<Namespace | undefined> {
    const namespaces = await this.getAllNamespaces()
    return namespaces.find((namespace) => {
      return namespace?.name === name
    })
  }

  /**
   * get a specific pod included in the provided namespace. The search is performed using the label `app` from your pod
   *
   * @param {string} namespace
   * @param {string} podName
   * @returns {(Promise<Pod | undefined>)}
   * @memberof K8sManagement
   */
  public async getPodFromNamespace(namespace: string, podName: string): Promise<Pod | undefined> {
    const pods = await this.getAllPodsInNamespace(namespace)
    return pods.find((pod) => {
      return pod?.labels?.['app'] === podName
    })
  }

  /**
   * get a specific service in the provided namespace. The search is performed using the label `app` from your service
   *
   * @param {string} namespace
   * @param {string} serviceName
   * @returns {(Promise<Pod | undefined>)}
   * @memberof K8sManagement
   */
  public async getServiceFromNamespace(namespace: string, serviceName: string): Promise<Pod | undefined> {
    const services = await this.getAllServicesInNamespace(namespace)
    return services.find((service) => {
      return service?.labels?.['app'] === serviceName
    })
  }

  /**
   * get a specific persistent volume claim from the provided namespace.
   *
   * @param {string} namespace
   * @param {string} volumeClaim
   * @returns {(Promise<VolumeClaim | undefined>)}
   * @memberof K8sManagement
   */
  public async getVolumeClaimFromNamespace(namespace: string, volumeClaim: string): Promise<VolumeClaim | undefined> {
    const claims = await this.getAllVolumeClaimFromNamespace(namespace)
    return claims.find((claim) => {
      return claim?.name === volumeClaim
    })
  }

  /**
   * get a list of all nodes that are running inside your cluster
   *
   * @returns {Promise<Array<Node>>}
   * @memberof K8sManagement
   */
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

  /**
   * get the main node of your cluster
   *
   * @returns {(Promise<Node | null>)}
   * @memberof K8sManagement
   */
  public async getMainNode(): Promise<Node | null> {
    const clusterNodes = await this.getAllNodesInCluster()
    return clusterNodes.find((node) => node.mainNode) ?? null
  }

  /**
   * apply the provided template to the cluster. This method will try to render the provided template using the provided data and apply the result to the cluster
   *
   * @param {string} template
   * @param {TemplateValues} templateData
   * @returns {Promise<Array<KubernetesObject>>}
   * @memberof K8sManagement
   */
  public async applyTemplate(template: string, templateData: TemplateValues): Promise<Array<KubernetesObject>> {
    const renderedYaml = Mustache.render(template, templateData)
    return await this.applyYamlString(renderedYaml)
  }

  /**
   * apply a string to the cluster. This method allow the user to pass a string containing a yaml definition and apply it to the cluster
   *
   * @param {string} yaml
   * @returns {Promise<Array<KubernetesObject>>}
   * @memberof K8sManagement
   */
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

  /**
   * This method will wait for a pod to be ready or will reject with an error if the pod is not ready after the provided timeout time
   *
   * @param {string} namespace
   * @param {string} podName
   * @param {number} [timeout=180000]
   * @returns {(Promise<Pod | undefined>)}
   * @memberof K8sManagement
   */
  public waitForPodToBeReady(namespace: string, podName: string, timeout = 180000): Promise<Pod | undefined> {
    return waitForIt(
      () => this.getPodFromNamespace(namespace, podName),
      (podInfo) => podInfo?.status === 'Running',
      `Unable to get the pod ${podName} in status Running, please check your cluster for more information`,
      timeout
    )
  }

  /**
   * This method will wait for a service to be ready or will reject with an error if the service is not ready after the provided timeout time
   *
   * @param {string} namespace
   * @param {string} serviceName
   * @param {number} [timeout=180000]
   * @returns {(Promise<Service | undefined>)}
   * @memberof K8sManagement
   */
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

  /**
   * Get a secret value from the cluster. This method returns the secret encoded in base64 string
   *
   * @param {string} namespace
   * @param {string} secretName
   * @returns {(Promise<Secret | undefined>)}
   * @memberof K8sManagement
   */
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

  /**
   * Exect a raw kubectl command in your cluster, the user only need to write the command without the `kubectl`
   * for example: `kubectl apply -f file.yaml` will be `execRawCommand('apply -f file.yaml')`
   * @param {string} command
   * @returns {Promise<any>}
   * @memberof K8sManagement
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public async execRawCommand(command: string): Promise<any> {
    return exec(`kubectl ${command}`)
  }

  private async unwrapResponse<TBody>(wrapped: Promise<{ body: TBody }>): Promise<TBody> {
    const unwrapped = await wrapped
    return unwrapped.body
  }
}
