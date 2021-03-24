import { CoreV1Api, KubeConfig, KubernetesObject, KubernetesObjectApi } from '@kubernetes/client-node'
import { Node, Namespace, Pod, Service, VolumeClaim, Secret } from './types'
import * as Mustache from 'mustache'
import { safeLoadAll } from 'js-yaml'
import { waitForIt } from '../utils'
import { TemplateValues } from '../templates/template-types'
import * as util from 'util'
import { IncomingMessage } from 'http'
import { Logger } from '@boostercloud/framework-types'
import { scopeLogger } from '../../helpers/logger'
const exec = util.promisify(require('child_process').exec)

export class K8sManagement {
  private kube: KubeConfig
  private k8sClient: CoreV1Api
  private kubectlCommand = 'kubectl'
  private logger: Logger

  constructor(logger: Logger) {
    this.kube = new KubeConfig()
    this.kube.loadFromDefault()
    this.k8sClient = this.kube.makeApiClient(CoreV1Api)
    this.logger = scopeLogger('K8sManagement', logger)
  }

  /**
   * get a list including all available pods in an specific namespace
   */
  public async getAllPodsInNamespace(namespace: string): Promise<Array<Pod>> {
    const l = scopeLogger('getAllPodsInNamespace', this.logger)
    l.debug('Unwrapping response')
    const response = await this.unwrapResponse(this.k8sClient.listNamespacedPod(namespace))
    l.debug('Mapping over response items, with length', response.items.length)
    return response.items.map((item) => {
      l.debug(`Got item called ${item.metadata?.name ?? 'UNDEFINED'}`)
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
    const l = scopeLogger('getAllServicesInNamespace', this.logger)
    l.debug('Calling `k8sClient.listNamespacedService(', namespace, ')`')
    const response = await this.unwrapResponse(this.k8sClient.listNamespacedService(namespace))
    l.debug('Got items with length', response.items.length)
    return response.items.map((item) => {
      l.debug('Transforming item named', item.metadata?.name ?? 'UNDEFINED')
      return {
        name: item.metadata?.name,
        namespace: item.metadata?.namespace ?? 'default',
        labels: item.metadata?.labels ?? {},
        ip: item.status?.loadBalancer?.ingress?.[0]?.ip ?? item.status?.loadBalancer?.ingress?.[0]?.hostname ?? '', //TODO: Report to the user a failure because his cluster is not providing an IP or hostname for services in K8s
      }
    })
  }

  /**
   * get a list including all Persistent Volume Claim in an specific namespace
   */
  public async getAllVolumeClaimFromNamespace(namespace: string): Promise<Array<VolumeClaim>> {
    const l = scopeLogger('getAllVolumeClaimFromNamespace', this.logger)
    l.debug('Calling `k8sClient.listPersistentVolumeClaimForAllNamespaces()`')
    const response = await this.unwrapResponse(this.k8sClient.listPersistentVolumeClaimForAllNamespaces())
    l.debug('Got', response.items.length, 'items')
    l.debug('Filtering items called', namespace, 'and transforming them')
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
    const l = scopeLogger('getAllNamespaces', this.logger)
    l.debug('Listing namespaces using `k8sClient.listNamespace()`')
    const response = await this.unwrapResponse(this.k8sClient.listNamespace())
    l.debug('Processing namespaces')
    return response.items.map((item) => {
      l.debug('Got namespace called', item.metadata?.name ?? 'UNDEFINED')
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
    const l = scopeLogger('createNamespace', this.logger)
    const namespace = {
      metadata: {
        name: name,
      },
    }
    l.debug('Creating namespace using `this.k8sClient.createNamespace`')
    return this.k8sClient.createNamespace(namespace).then(
      () => {
        l.debug('Namespace created successfully')
        return true
      },
      () => {
        l.debug("Couldn't create namespace")
        return false
      }
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
    const l = scopeLogger('getNamespace', this.logger)
    l.debug('Getting all namespaces')
    const namespaces = await this.getAllNamespaces()
    l.debug('Attempting to find a namespace called', name, 'in array of', namespaces.length, 'elements')
    return namespaces.find((namespace) => {
      return namespace?.name === name
    })
  }

  /**
   * get a specific pod included in the provided namespace. The search is performed using the label `app` from your pod
   */
  public async getPodFromNamespace(namespace: string, podName: string): Promise<Pod | undefined> {
    const l = scopeLogger('getPodFromNamespace', this.logger)
    l.debug('Get all pods in namespace')
    const pods = await this.getAllPodsInNamespace(namespace)
    l.debug('Attempting to find pod called', podName)
    return pods.find((pod) => {
      return pod?.labels?.['app'] === podName
    })
  }

  /**
   * get a specific service in the provided namespace. The search is performed using the label `app` from your service
   */
  public async getServiceFromNamespace(namespace: string, serviceName: string): Promise<Pod | undefined> {
    const l = scopeLogger('getServiceFromNamespace', this.logger)
    l.debug('Getting all services from namespace')
    const services = await this.getAllServicesInNamespace(namespace)
    l.debug('Attempting to find service named', serviceName)
    return services.find((service) => {
      return service?.labels?.['app'] === serviceName
    })
  }

  /**
   * get a specific persistent volume claim from the provided namespace.
   */
  public async getVolumeClaimFromNamespace(namespace: string, volumeClaim: string): Promise<VolumeClaim | undefined> {
    const l = scopeLogger('getVolumeClaimFromNamespace', this.logger)
    l.debug('Getting all volume claims')
    const claims = await this.getAllVolumeClaimFromNamespace(namespace)
    l.debug('Trying to find a claim called', volumeClaim)
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
    const l = scopeLogger('applyTemplate', this.logger)
    l.debug('Rendering template')
    const renderedYaml = Mustache.render(template, templateData)
    l.debug('Rendered YAML:\n', renderedYaml)
    l.debug('Applying YAML string')
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
    const l = scopeLogger('applyYamlString', this.logger)
    l.debug('Making API client')
    const client = KubernetesObjectApi.makeApiClient(this.kube)
    l.debug('Safe loading')
    const specs = safeLoadAll(yaml)
    l.debug('Filtering specs')
    const validSpecs = specs.filter((s) => s?.kind && s?.metadata)
    const created: KubernetesObject[] = []
    for (const spec of validSpecs) {
      spec.metadata = spec.metadata || {}
      spec.metadata.annotations = spec.metadata.annotations || {}
      delete spec.metadata.annotations['kubectl.kubernetes.io/last-applied-configuration']
      spec.metadata.annotations['kubectl.kubernetes.io/last-applied-configuration'] = JSON.stringify(spec)
      l.debug('Checking if resource exists')
      const resourceExists = await this.existsResourceSpec(spec)
      let response: { body: KubernetesObject; response: IncomingMessage }
      if (resourceExists) {
        l.debug('Resource exists, replacing')
        response = await client.replace(spec)
      } else {
        l.debug("Resource doesn't exist, creating")
        response = await client.create(spec)
      }
      l.debug('Pushing response.body')
      created.push(response.body)
    }
    return created
  }

  /**
   * wait for a pod to be ready or throw an error if the pod is not ready after the provided timeout time
   */
  public waitForPodToBeReady(namespace: string, podName: string, timeout = 180000): Promise<Pod | undefined> {
    const l = scopeLogger('waitForPodToBeReady', this.logger)
    l.debug('Waiting for it')
    return waitForIt(
      async () => {
        l.debug('Getting pod from namespace')
        return this.getPodFromNamespace(namespace, podName)
      },
      (podInfo) => {
        l.debug('Got podInfo status', podInfo ? podInfo.status : 'UNDEFINED')
        return podInfo?.status === 'Running'
      },
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
    const l = scopeLogger('waitForServiceToBeReady', this.logger)
    l.debug('waiting')
    return waitForIt(
      () => {
        l.debug('Getting service from namespace')
        return this.getServiceFromNamespace(namespace, serviceName)
      },
      (serviceInfo) => {
        l.debug('Got service info IP', serviceInfo ? serviceInfo.ip : 'UNDEFINED')
        return serviceInfo?.ip !== ''
      },
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

  public async setClusterContext(context: string): Promise<void> {
    await this.execRawCommand(`config use-context ${context}`)
  }

  /**
   * exec a raw kubectl command in your cluster, the user only need to write the command without the `kubectl`
   * for example: `kubectl apply -f file.yaml` will be `execRawCommand('apply -f file.yaml')`
   */
  public async execRawCommand(command: string): Promise<{ stderr?: string; stdout?: string }> {
    const l = scopeLogger('execRawCommand', this.logger)
    const cmd = `${this.kubectlCommand} ${command}`
    l.debug('Executing', cmd)
    return await exec(cmd)
  }

  private async unwrapResponse<TBody>(wrapped: Promise<{ body: TBody }>): Promise<TBody> {
    return (await wrapped).body
  }
}
