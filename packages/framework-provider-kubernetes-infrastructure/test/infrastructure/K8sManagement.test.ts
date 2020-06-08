import { CoreV1Api, KubeConfig, V1Pod, V1ObjectMeta, V1Namespace } from '@kubernetes/client-node'
import { K8sManagement } from '../../src/infrastructure/k8s-sdk/K8sManagement'
import { replace, fake } from 'sinon'
import { expect } from '../expect'

describe('K8s cluster interaction', () => {
  const namespaceName = 'nameSpace_test'
  const nonExistingNamespace = 'non_existing_namespace'
  const podName = 'pod_test'
  const nonExistingPod = 'non_existing_pod'

  const namespace = new V1Namespace()
  const metadata = new V1ObjectMeta()
  metadata.name = namespaceName
  namespace.metadata = metadata
  const namespaceList = { response: {}, body: { items: [namespace] } }

  const pod = new V1Pod()
  const metadataPod = new V1ObjectMeta()
  metadataPod.name = podName
  pod.metadata = metadataPod
  const podList = { response: {}, body: { items: [pod] } }

  replace(KubeConfig.prototype, 'makeApiClient', fake.returns(new CoreV1Api()))
  replace(CoreV1Api.prototype, 'listNamespace', fake.resolves(namespaceList))
  replace(CoreV1Api.prototype, 'listNamespacedPod', fake.resolves(podList))
  replace(CoreV1Api.prototype, 'createNamespace', fake.resolves(namespace))
  replace(CoreV1Api.prototype, 'deleteNamespace', fake.resolves(namespace))

  const k8sManager = new K8sManagement()

  it('list all pods', async () => {
    const clusterResponse = await k8sManager.getAllPodsInNamespace('test')
    expect(clusterResponse.length).to.be.equal(1)
    expect(clusterResponse[0].name).to.be.equal(podName)
  })

  it('list all namespaces', async () => {
    const clusterResponse = await k8sManager.getAllNamespaces()
    expect(clusterResponse.length).to.be.equal(1)
    expect(clusterResponse[0].name).to.be.equal(namespaceName)
  })

  it('create a namespace', async () => {
    const clusterResponse = await k8sManager.createNamespace(namespaceName)
    expect(clusterResponse).to.be.true
  })

  it('delete a namespace', async () => {
    const clusterResponse = await k8sManager.deleteNamespace(namespaceName)
    expect(clusterResponse).to.be.true
  })

  it('get a specific namespace', async () => {
    const clusterResponse = await k8sManager.getNamespace(namespaceName)
    expect(clusterResponse?.name).to.be.equal(namespaceName)
  })

  it('search a non existing namespace', async () => {
    const clusterResponse = await k8sManager.getNamespace(nonExistingNamespace)
    expect(clusterResponse).to.be.equal(null)
  })

  it('get a specific pod', async () => {
    const clusterResponse = await k8sManager.getPodFromNamespace(podName, namespaceName)
    expect(clusterResponse?.name).to.be.equal(podName)
  })

  it('search a non existing pod', async () => {
    const clusterResponse = await k8sManager.getPodFromNamespace(nonExistingPod, namespaceName)
    expect(clusterResponse).to.be.equal(null)
  })
})
