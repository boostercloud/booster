import { CoreV1Api, KubeConfig, V1Pod, V1ObjectMeta, V1Namespace, V1NodeStatus, V1Node } from '@kubernetes/client-node'
import { K8sManagement } from '../../src/infrastructure/k8s-sdk/K8sManagement'
import { replace, fake, restore } from 'sinon'
import { expect } from '../expect'

describe('Users want to interact with K8s cluster', () => {
  const NAMESPACE_NAME = 'nameSpace_test'
  const NAMESPACE_NAME_NON_EXIST = 'non_existing_namespace'
  const POD_NAME = 'pod_test'
  const POD_NAME_NON_EXIST = 'non_existing_pod'
  const NODE_NAME = 'node1_test'
  const OPENWHISK_INVOKER_ROLE = 'invoker'
  const OPENWHISK_CORE_ROLE = 'core'

  const namespace = new V1Namespace()
  const metadata = new V1ObjectMeta()
  metadata.name = NAMESPACE_NAME
  namespace.metadata = metadata
  const namespaceList = { response: {}, body: { items: [namespace] } }

  const pod = new V1Pod()
  const metadataPod = new V1ObjectMeta()
  metadataPod.name = POD_NAME
  pod.metadata = metadataPod
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

  beforeEach(() => {
    replace(KubeConfig.prototype, 'makeApiClient', fake.returns(new CoreV1Api()))
    replace(CoreV1Api.prototype, 'listNamespace', fake.resolves(namespaceList))
    replace(CoreV1Api.prototype, 'listNamespacedPod', fake.resolves(podList))
    replace(CoreV1Api.prototype, 'createNamespace', fake.resolves(namespace))
    replace(CoreV1Api.prototype, 'deleteNamespace', fake.resolves(namespace))
    replace(CoreV1Api.prototype, 'listNode', fake.resolves(nodeList))
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
    expect(clusterResponse).to.be.equal(null)
  })

  it('they want to get a specific pod', async () => {
    const clusterResponse = await k8sManager.getPodFromNamespace(POD_NAME, NAMESPACE_NAME)
    expect(clusterResponse?.name).to.be.equal(POD_NAME)
  })

  it('they try to search a non existing pod', async () => {
    const clusterResponse = await k8sManager.getPodFromNamespace(POD_NAME_NON_EXIST, NAMESPACE_NAME)
    expect(clusterResponse).to.be.equal(null)
  })

  it('they want to list all nodes with invoker openwhisk role', async () => {
    const clusterResponse = await k8sManager.getAllNodesWithOpenWhiskRole(OPENWHISK_INVOKER_ROLE)
    expect(clusterResponse.length).to.be.equal(1)
    expect(clusterResponse[0].name).to.be.equal(NODE_NAME)
  })

  it('they want to get the main node node', async () => {
    const clusterResponse = await k8sManager.getMainNode()
    expect(clusterResponse?.name).to.be.equal(NODE_NAME)
  })

  it('they want to list all nodes with non existing openwhisk role', async () => {
    const clusterResponse = await k8sManager.getAllNodesWithOpenWhiskRole(OPENWHISK_CORE_ROLE)
    expect(clusterResponse.length).to.be.equal(0)
  })
})
