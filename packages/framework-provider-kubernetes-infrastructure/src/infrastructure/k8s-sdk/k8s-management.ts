import { CoreV1Api, KubeConfig, KubernetesObject, KubernetesObjectApi } from '@kubernetes/client-node'
import { Node, Namespace, Pod, Service, VolumeClaim, Secret } from './types'
import * as Mustache from 'mustache'
import { safeLoadAll } from 'js-yaml'
import { waitForIt } from '../utils'
import { TemplateValues } from '../templates/template-types'
import * as util from 'util'
import { IncomingMessage } from 'http'
const exec = util.promisify(require('child_process').exec)

export class K8sManagement {
  private kube: KubeConfig
  private k8sClient: CoreV1Api
  private kubectlCommand = 'kubectl'

  constructor() {
    this.kube = new KubeConfig()
    this.kube.loadFromDefault()
    this.k8sClient = this.kube.makeApiClient(CoreV1Api)
  }

  /**
   * get a list including all available pods in an specific namespace
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
   * get a list including all available services in an specific namespace
   */
  public async getAllServicesInNamespace(namespace: string): Promise<Array<Service>> {
    const response = await this.unwrapResponse(this.k8sClient.listNamespacedService(namespace))
    return response.items.map((item) => {
      return {
        name: item.metadata?.name,
        namespace: item.metadata?.namespace ?? 'default',
        labels: item.metadata?.labels ?? {},
        ip: item.status?.loadBalancer?.ingress?.[0]?.ip ?? '',
      }
    })
  }

  /**
   * get a list including all Persistent Volume Claim in an specific namespace
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
   */
  public async createNamespace(name: string): Promise<boolean> {
    const namespace = {
      metadata: {
        name: name,
      },
    }
    return this.k8sClient.createNamespace(namespace).then(
      () => true,
      () => false
    )
  }

  /**
   * delete an existing namespace inside your cluster
   */
  public async deleteNamespace(name: string): Promise<boolean> {
    return this.k8sClient.deleteNamespace(name).then(
      () => true,
      () => false
    )
  }

  /**
   * get the information from a specific namespace inside your cluster
   */
  public async getNamespace(name: string): Promise<Namespace | undefined> {
    const namespaces = await this.getAllNamespaces()
    return namespaces.find((namespace) => {
      return namespace?.name === name
    })
  }

  /**
   * get a specific pod included in the provided namespace. The search is performed using the label `app` from your pod
   */
  public async getPodFromNamespace(namespace: string, podName: string): Promise<Pod | undefined> {
    const pods = await this.getAllPodsInNamespace(namespace)
    return pods.find((pod) => {
      return pod?.labels?.['app'] === podName
    })
  }

  /**
   * get a specific service in the provided namespace. The search is performed using the label `app` from your service
   */
  public async getServiceFromNamespace(namespace: string, serviceName: string): Promise<Pod | undefined> {
    const services = await this.getAllServicesInNamespace(namespace)
    return services.find((service) => {
      return service?.labels?.['app'] === serviceName
    })
  }

  /**
   * get a specific persistent volume claim from the provided namespace.
   */
  public async getVolumeClaimFromNamespace(namespace: string, volumeClaim: string): Promise<VolumeClaim | undefined> {
    const claims = await this.getAllVolumeClaimFromNamespace(namespace)
    return claims.find((claim) => {
      return claim?.name === volumeClaim
    })
  }

  /**
   * get a list of all nodes that are running inside your cluster
   */
  public async getAllNodesInCluster(): Promise<Array<Node>> {
    const response = await this.unwrapResponse(this.k8sClient.listNode())
    return response.items.map((item) => {
      const internalIP = item.status?.addresses?.find((address) => {
        return address.type === 'InternalIP'
      })
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
   */
  public async getMainNode(): Promise<Node | undefined> {
    const clusterNodes = await this.getAllNodesInCluster()
    return clusterNodes.find((node) => node?.mainNode)
  }

  /**
   * apply the provided template to the cluster. This method will try to render the provided template using the provided data and apply the result to the cluster
   */
  public async applyTemplate(template: string, templateData: TemplateValues): Promise<Array<KubernetesObject>> {
    const renderedYaml = Mustache.render(template, templateData)
    return await this.applyYamlString(renderedYaml)
  }

  /**
   * check if a yaml spec exists inside the cluster
   */
  //disabling linter here because spec has type any when we parse the yaml file with Kubernetes client :(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public async existsResourceSpec(spec: any): Promise<boolean> {
    const client = KubernetesObjectApi.makeApiClient(this.kube)
    try {
      await client.read(spec)
      return true
    } catch (e) {
      return false
    }
  }

  /**
   * apply a string to the cluster. This method allow the user to pass a string containing a yaml definition and apply it to the cluster
   */
  public async applyYamlString(yaml: string): Promise<Array<KubernetesObject>> {
    const client = KubernetesObjectApi.makeApiClient(this.kube)
    const specs = safeLoadAll(yaml)
    const validSpecs = specs.filter((s) => s?.kind && s?.metadata)
    const created: KubernetesObject[] = []
    for (const spec of validSpecs) {
      spec.metadata = spec.metadata || {}
      spec.metadata.annotations = spec.metadata.annotations || {}
      delete spec.metadata.annotations['kubectl.kubernetes.io/last-applied-configuration']
      spec.metadata.annotations['kubectl.kubernetes.io/last-applied-configuration'] = JSON.stringify(spec)
      const resourceExists = await this.existsResourceSpec(spec)
      let response: { body: KubernetesObject; response: IncomingMessage }
      if (resourceExists) {
        response = await client.replace(spec)
      } else {
        response = await client.create(spec)
      }
      created.push(response.body)
    }
    return created
  }

  /**
   * wait for a pod to be ready or throw an error if the pod is not ready after the provided timeout time
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
   * wait for a service to be ready or throw and error if the service is not ready after the provided timeout time
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
   * get a secret value from the cluster. This method returns the secret encoded in base64 string
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
   * exec a raw kubectl command in your cluster, the user only need to write the command without the `kubectl`
   * for example: `kubectl apply -f file.yaml` will be `execRawCommand('apply -f file.yaml')`
   */
  public async execRawCommand(command: string): Promise<{ stderr?: string; stdout?: string }> {
    return await exec(`${this.kubectlCommand} ${command}`)
  }

  private async unwrapResponse<TBody>(wrapped: Promise<{ body: TBody }>): Promise<TBody> {
    return (await wrapped).body
  }
}
