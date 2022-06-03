import { CoreV1Api, KubeConfig, KubernetesObject, KubernetesObjectApi } from '@kubernetes/client-node'
import { Node, Namespace, Pod, Service, VolumeClaim, Secret } from './types'
import * as Mustache from 'mustache'
import { safeLoadAll } from 'js-yaml'
import { waitForIt } from '../utils'
import { DaprTemplateRoles, TemplateValues } from '../templates/template-types'
import * as util from 'util'
import { IncomingMessage } from 'http'
import { BoosterConfig } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
const exec = util.promisify(require('child_process').exec)

export class K8sManagement {
  private kube: KubeConfig
  private k8sClient: CoreV1Api
  private kubectlCommand = 'kubectl'

  constructor(readonly config: BoosterConfig) {
    this.kube = new KubeConfig()
    this.kube.loadFromDefault()
    this.k8sClient = this.kube.makeApiClient(CoreV1Api)
  }

  /**
   * get a list including all available pods in an specific namespace
   */
  public async getAllPodsInNamespace(namespace: string): Promise<Array<Pod>> {
    const logger = getLogger(this.config, 'K8sManagement#getAllPodsInNamespace')
    logger.debug('Unwrapping response')
    const response = await this.unwrapResponse(this.k8sClient.listNamespacedPod(namespace))
    logger.debug('Mapping over response items, with length', response.items.length)
    return response.items.map((item) => {
      logger.debug(`Got item called ${item.metadata?.name ?? 'UNDEFINED'}`)
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
    const logger = getLogger(this.config, 'K8sManagement#getAllServicesInNamespace')
    logger.debug('Calling `k8sClient.listNamespacedService(', namespace, ')`')
    const response = await this.unwrapResponse(this.k8sClient.listNamespacedService(namespace))
    logger.debug('Got items with length', response.items.length)
    const mainNode = await this.getMainNode()
    // We differentiate two provider groups:
    // 1. Minikube provider (local)
    // 2. Cloud providers
    const minikubeProvider = mainNode?.name === 'minikube'
    return response.items.map((item) => {
      let ip = ''
      let port = undefined
      if (minikubeProvider && mainNode?.ip) {
        // Minikube services work like => <minikube_ip>:<minikube_specific_service_nodePort>
        ip = mainNode?.ip
        port = item.spec?.ports?.[0]?.nodePort?.toString()
      } else {
        ip = item.status?.loadBalancer?.ingress?.[0]?.ip ?? item.status?.loadBalancer?.ingress?.[0]?.hostname ?? ''
      }

      logger.debug('Transforming item named', item.metadata?.name ?? 'UNDEFINED')
      return {
        name: item.metadata?.name,
        namespace: item.metadata?.namespace ?? 'default',
        labels: item.metadata?.labels ?? {},
        ip: ip, //TODO: Report to the user a failure because his cluster is not providing an IP or hostname for services in K8s
        port: port,
      }
    })
  }

  /**
   * get a list including all Persistent Volume Claim in an specific namespace
   */
  public async getAllVolumeClaimFromNamespace(namespace: string): Promise<Array<VolumeClaim>> {
    const logger = getLogger(this.config, 'K8sManagement#getAllVolumeClaimFromNamespace')
    logger.debug('Calling `k8sClient.listPersistentVolumeClaimForAllNamespaces()`')
    const response = await this.unwrapResponse(this.k8sClient.listPersistentVolumeClaimForAllNamespaces())
    logger.debug('Got', response.items.length, 'items')
    logger.debug('Filtering items called', namespace, 'and transforming them')
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
    const logger = getLogger(this.config, 'K8sManagement#getAllNamespaces')
    logger.debug('Listing namespaces using `k8sClient.listNamespace()`')
    const response = await this.unwrapResponse(this.k8sClient.listNamespace())
    logger.debug('Processing namespaces')
    return response.items.map((item) => {
      logger.debug('Got namespace called', item.metadata?.name ?? 'UNDEFINED')
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
    const logger = getLogger(this.config, 'K8sManagement#createNamespace')
    const namespace = {
      metadata: {
        name: name,
      },
    }
    logger.debug('Creating namespace using `this.k8sClient.createNamespace`')
    return this.k8sClient.createNamespace(namespace).then(
      () => {
        logger.debug('Namespace created successfully')
        return true
      },
      () => {
        logger.debug("Couldn't create namespace")
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
    const logger = getLogger(this.config, 'K8sManagement#getNamespace')
    logger.debug('Getting all namespaces')
    const namespaces = await this.getAllNamespaces()
    logger.debug('Attempting to find a namespace called', name, 'in array of', namespaces.length, 'elements')
    return namespaces.find((namespace) => {
      return namespace?.name === name
    })
  }

  /**
   * get a specific pod included in the provided namespace. The search is performed using the label `app` from your pod
   */
  public async getPodFromNamespace(namespace: string, podName: string): Promise<Pod | undefined> {
    const logger = getLogger(this.config, 'K8sManagement#getPodFromNamespace')
    logger.debug('Get all pods in namespace')
    const pods = await this.getAllPodsInNamespace(namespace)
    logger.debug('Attempting to find pod called', podName)
    return pods.find((pod) => {
      return pod?.name?.includes('redis')
        ? pod.labels?.['app.kubernetes.io/name'] === podName
        : pod.labels?.['app'] === podName
    })
  }

  /**
   * get a specific service in the provided namespace. The search is performed using the label `app` from your service
   */
  public async getServiceFromNamespace(namespace: string, serviceName: string): Promise<Pod | undefined> {
    const logger = getLogger(this.config, 'K8sManagement#getServiceFromNamespace')
    logger.debug('Getting all services from namespace')
    const services = await this.getAllServicesInNamespace(namespace)
    logger.debug('Attempting to find service named', serviceName)
    return services.find((service) => {
      return service?.labels?.['app'] === serviceName
    })
  }

  /**
   * get a specific persistent volume claim from the provided namespace.
   */
  public async getVolumeClaimFromNamespace(namespace: string, volumeClaim: string): Promise<VolumeClaim | undefined> {
    const logger = getLogger(this.config, 'K8sManagement#getVolumeClaimFromNamespace')
    logger.debug('Getting all volume claims')
    const claims = await this.getAllVolumeClaimFromNamespace(namespace)
    logger.debug('Trying to find a claim called', volumeClaim)
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
  public async applyTemplate(
    template: string,
    templateData: TemplateValues | DaprTemplateRoles
  ): Promise<Array<KubernetesObject>> {
    const logger = getLogger(this.config, 'K8sManagement#applyTemplate')
    logger.debug('Rendering template')
    const renderedYaml = Mustache.render(template, templateData)
    logger.debug('Rendered YAML:\n', renderedYaml)
    logger.debug('Applying YAML string')
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
    const logger = getLogger(this.config, 'K8sManagement#applyYamlString')
    logger.debug('Making API client')
    const client = KubernetesObjectApi.makeApiClient(this.kube)
    logger.debug('Safe loading')
    const specs = safeLoadAll(yaml)
    logger.debug('Filtering specs')
    const validSpecs = specs.filter((s) => s?.kind && s?.metadata)
    const created: KubernetesObject[] = []
    for (const spec of validSpecs) {
      spec.metadata = spec.metadata || {}
      spec.metadata.annotations = spec.metadata.annotations || {}
      delete spec.metadata.annotations['kubectl.kubernetes.io/last-applied-configuration']
      spec.metadata.annotations['kubectl.kubernetes.io/last-applied-configuration'] = JSON.stringify(spec)
      logger.debug('Checking if resource exists')
      const resourceExists = await this.existsResourceSpec(spec)
      let response: { body: KubernetesObject; response: IncomingMessage }
      if (resourceExists) {
        logger.debug('Resource exists, replacing')
        response = await client.replace(spec)
      } else {
        logger.debug("Resource doesn't exist, creating")
        response = await client.create(spec)
      }
      logger.debug('Pushing response.body')
      created.push(response.body)
    }
    return created
  }

  /**
   * wait for a pod to be ready or throw an error if the pod is not ready after the provided timeout time
   */
  public waitForPodToBeReady(namespace: string, podName: string, timeout = 180000): Promise<Pod | undefined> {
    const logger = getLogger(this.config, 'K8sManagement#waitForPodToBeReady')
    logger.debug('Waiting for it')
    return waitForIt(
      async () => {
        logger.debug('Getting pod from namespace')
        return this.getPodFromNamespace(namespace, podName)
      },
      (podInfo) => {
        logger.debug('Got podInfo status', podInfo ? podInfo.status : 'UNDEFINED')
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
    const logger = getLogger(this.config, 'K8sManagement#waitForServiceToBeReady')
    logger.debug('waiting')
    return waitForIt(
      () => {
        logger.debug('Getting service from namespace')
        return this.getServiceFromNamespace(namespace, serviceName)
      },
      (serviceInfo) => {
        logger.debug('Got service info IP', serviceInfo ? serviceInfo.ip : 'UNDEFINED')
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
    this.kube.loadFromDefault()
    this.k8sClient = this.kube.makeApiClient(CoreV1Api)
  }

  /**
   * exec a raw kubectl command in your cluster, the user only need to write the command without the `kubectl`
   * for example: `kubectl apply -f file.yaml` will be `execRawCommand('apply -f file.yaml')`
   */
  public async execRawCommand(command: string): Promise<{ stderr?: string; stdout?: string }> {
    const logger = getLogger(this.config, 'K8sManagement#execRawCommand')
    const cmd = `${this.kubectlCommand} ${command}`
    logger.debug('Executing', cmd)
    return await exec(cmd)
  }

  private async unwrapResponse<TBody>(wrapped: Promise<{ body: TBody }>): Promise<TBody> {
    return (await wrapped).body
  }
}
