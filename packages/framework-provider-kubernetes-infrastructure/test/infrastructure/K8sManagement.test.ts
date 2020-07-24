import {
  CoreV1Api,
  KubeConfig,
  V1Pod,
  V1ObjectMeta,
  V1Namespace,
  V1NodeStatus,
  V1Node,
  V1Service,
  V1ServiceStatus,
  V1LoadBalancerStatus,
  V1PersistentVolumeClaim,
  KubernetesObjectApi,
  V1PodStatus,
  V1Secret,
} from '@kubernetes/client-node'
import { K8sManagement } from '../../src/infrastructure/k8s-sdk/K8sManagement'
import { replace, fake, restore } from 'sinon'
import { expect } from '../expect'
import { boosterAppPod } from '../../src/infrastructure/templates/boosterApp'
describe('Users want to interact with K8s cluster', () => {
  const NAMESPACE_NAME = 'nameSpace_test'
  const NAMESPACE_NAME_NON_EXIST = 'non_existing_namespace'
  const POD_NAME = 'pod_test'
  const POD_NAME_NON_EXIST = 'non_existing_pod'
  const NODE_NAME = 'node1_test'
  const SERVICE_NAME = 'service'
  const SERVICE_IP = '129.129.129.0'
  const PVC_NAME = 'pvc_test'
  const TEMPLATE_VALUES = {
    timestamp: 'time',
    namespace: NAMESPACE_NAME,
    clusterVolume: PVC_NAME,
    environment: 'environment',
  }
  const SECRET_NAME = 'secretName'
  const SECRET_DATA = { sensibleData: 'sensibleInfo' }

  const namespace = new V1Namespace()
  const metadata = new V1ObjectMeta()
  metadata.name = NAMESPACE_NAME
  namespace.metadata = metadata
  const namespaceList = { response: {}, body: { items: [namespace] } }

  const pod = new V1Pod()
  const metadataPod = new V1ObjectMeta()
  metadataPod.name = POD_NAME
  metadataPod.labels = { app: POD_NAME }
  pod.metadata = metadataPod
  const podStatus = new V1PodStatus()
  podStatus.phase = 'Running'
  pod.status = podStatus
  const podList = { response: {}, body: { items: [pod] } }

  const node = new V1Node()
  const metadataNode = new V1ObjectMeta()
  metadataNode.name = NODE_NAME
  metadataNode.labels = { 'openwhisk-role': 'invoker', 'node-role.kubernetes.io/master': '' }
  node.metadata = metadataNode
  const nodeStatus = new V1NodeStatus()
  nodeStatus.addresses = [{ address: '192.168.1.1', type: 'InternalIP' }]
  node.status = nodeStatus
  const node2 = new V1Node()
  const metadataNode2 = new V1ObjectMeta()
  metadataNode.name = NODE_NAME
  node2.metadata = metadataNode2
  const nodeList = { response: {}, body: { items: [node, node2] } }
  let k8sManager: K8sManagement

  const service = new V1Service()
  const metadataService = new V1ObjectMeta()
  metadataService.name = SERVICE_NAME
  metadataService.namespace = NAMESPACE_NAME
  metadataService.labels = { app: SERVICE_NAME }
  const serviceLoadBalancer = new V1LoadBalancerStatus()
  serviceLoadBalancer.ingress = [{ ip: SERVICE_IP }]
  const statusService = new V1ServiceStatus()
  statusService.loadBalancer = serviceLoadBalancer
  service.status = statusService
  service.metadata = metadataService
  const serviceList = { response: {}, body: { items: [service] } }

  const volumeClaim = new V1PersistentVolumeClaim()
  const volumeMetadata = new V1ObjectMeta()
  volumeMetadata.name = PVC_NAME
  volumeMetadata.namespace = NAMESPACE_NAME
  volumeMetadata.labels = { app: PVC_NAME }
  volumeClaim.metadata = volumeMetadata
  const volumeClaimList = { response: {}, body: { items: [volumeClaim] } }
  const clientRead = { response: {}, body: { items: [new KubernetesObjectApi()] } }

  const secretValue = new V1Secret()
  secretValue.data = SECRET_DATA
  const secretMeta = new V1ObjectMeta()
  secretMeta.name = SECRET_NAME
  secretValue.metadata = secretMeta
  const secret = { response: {}, body: secretValue }

  beforeEach(() => {
    replace(KubeConfig.prototype, 'makeApiClient', fake.returns(new CoreV1Api()))
    replace(KubernetesObjectApi, 'makeApiClient', fake.returns(new KubernetesObjectApi()))
    replace(CoreV1Api.prototype, 'listNamespace', fake.resolves(namespaceList))
    replace(CoreV1Api.prototype, 'listNamespacedPod', fake.resolves(podList))
    replace(CoreV1Api.prototype, 'createNamespace', fake.resolves(namespace))
    replace(CoreV1Api.prototype, 'deleteNamespace', fake.resolves(namespace))
    replace(CoreV1Api.prototype, 'listNode', fake.resolves(nodeList))
    replace(CoreV1Api.prototype, 'listNamespacedService', fake.resolves(serviceList))
    replace(CoreV1Api.prototype, 'listPersistentVolumeClaimForAllNamespaces', fake.resolves(volumeClaimList))
    replace(KubernetesObjectApi.prototype, 'read', fake.resolves(clientRead))
    replace(KubernetesObjectApi.prototype, 'create', fake.resolves(new KubernetesObjectApi()))
    replace(CoreV1Api.prototype, 'readNamespacedSecret', fake.resolves(secret))

    k8sManager = new K8sManagement()
  })

  afterEach(() => {
    restore()
  })

  it('and list all pods', async () => {
    const clusterResponse = await k8sManager.getAllPodsInNamespace(NAMESPACE_NAME)
    expect(clusterResponse.length).to.be.equal(1)
    expect(clusterResponse[0].name).to.be.equal(POD_NAME)
  })

  it('and list all namespaces', async () => {
    const clusterResponse = await k8sManager.getAllNamespaces()
    expect(clusterResponse.length).to.be.equal(1)
    expect(clusterResponse[0].name).to.be.equal(NAMESPACE_NAME)
  })

  it('and list all cluster nodes', async () => {
    const clusterResponse = await k8sManager.getAllNodesInCluster()
    expect(clusterResponse.length).to.be.equal(2)
    expect(clusterResponse[0].name).to.be.equal(NODE_NAME)
  })

  it('and they want to create a namespace', async () => {
    const clusterResponse = await k8sManager.createNamespace(NAMESPACE_NAME)
    expect(clusterResponse).to.be.true
  })

  it('and they want to delete a namespace', async () => {
    const clusterResponse = await k8sManager.deleteNamespace(NAMESPACE_NAME)
    expect(clusterResponse).to.be.true
  })

  it('and they want to get a specific namespace', async () => {
    const clusterResponse = await k8sManager.getNamespace(NAMESPACE_NAME)
    expect(clusterResponse?.name).to.be.equal(NAMESPACE_NAME)
  })

  it('they try to search a non existing namespace', async () => {
    const clusterResponse = await k8sManager.getNamespace(NAMESPACE_NAME_NON_EXIST)
    expect(clusterResponse).to.be.equal(undefined)
  })

  it('they want to get a specific pod', async () => {
    const clusterResponse = await k8sManager.getPodFromNamespace(NAMESPACE_NAME, POD_NAME)
    expect(clusterResponse?.name).to.be.equal(POD_NAME)
  })

  it('they try to search a non existing pod', async () => {
    const clusterResponse = await k8sManager.getPodFromNamespace(NAMESPACE_NAME, POD_NAME_NON_EXIST)
    expect(clusterResponse).to.be.equal(undefined)
  })

  it('they want to get the main node node', async () => {
    const clusterResponse = await k8sManager.getMainNode()
    expect(clusterResponse?.name).to.be.equal(NODE_NAME)
  })

  it('they want to get the list of services in a namespace', async () => {
    const clusterResponse = await k8sManager.getAllServicesInNamespace(NAMESPACE_NAME)
    expect(clusterResponse.length).to.be.equal(1)
  })

  it('they want to get the list of volume claims in a namespace', async () => {
    const clusterResponse = await k8sManager.getAllVolumeClaimFromNamespace(NAMESPACE_NAME)
    expect(clusterResponse.length).to.be.equal(1)
  })

  it('they want to get one service from a namespace', async () => {
    const clusterResponse = await k8sManager.getServiceFromNamespace(NAMESPACE_NAME, SERVICE_NAME)
    expect(clusterResponse?.labels?.['app']).to.be.equal(SERVICE_NAME)
  })

  it('they want to get a claim from a namespace', async () => {
    const clusterResponse = await k8sManager.getVolumeClaimFromNamespace(NAMESPACE_NAME, PVC_NAME)
    expect(clusterResponse?.name).to.be.equal(PVC_NAME)
  })

  it('they want to get apply a template into the cluster', async () => {
    const clusterResponse = await k8sManager.applyTemplate(boosterAppPod.template, TEMPLATE_VALUES)
    expect(clusterResponse.length).to.be.equal(1)
  })

  it('they want to wait for a pod to be ready', async () => {
    const clusterResponse = await k8sManager.waitForPodToBeReady(NAMESPACE_NAME, POD_NAME)
    expect(clusterResponse).to.be.not.undefined
  })

  it('they want to wait for a service to be ready', async () => {
    const clusterResponse = await k8sManager.waitForServiceToBeReady(NAMESPACE_NAME, SERVICE_NAME)
    expect(clusterResponse).to.be.not.undefined
  })

  it('they want to get a secret', async () => {
    const clusterResponse = await k8sManager.getSecret(NAMESPACE_NAME, SECRET_NAME)
    expect(clusterResponse).to.be.not.undefined
    expect(clusterResponse?.data).to.be.equal(SECRET_DATA)
    expect(clusterResponse?.name).to.be.equal(SECRET_NAME)
  })
})
