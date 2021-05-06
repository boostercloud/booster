import { expect } from '../../../helper/expect'
import * as Kubernetes from '@kubernetes/client-node'
import { boosterKubernetesServices, kubernetesNamespace } from '../constants'
import { waitForIt } from '../../aws/utils'

describe('Kubernetes provider', () => {
  const kubernetesConfig = new Kubernetes.KubeConfig()
  kubernetesConfig.loadFromDefault()
  const k8sClient = kubernetesConfig.makeApiClient(Kubernetes.CoreV1Api)

  it('deploys the Booster application with accessible nodePort for fileuploader and booster services', async () => {
    const services = await k8sClient.listNamespacedService(kubernetesNamespace)
    const pods = await k8sClient.listNamespacedPod(kubernetesNamespace)

    expect(services.body.items).not.to.be.undefined
    expect(pods.body.items).not.to.be.undefined

    const serviceNames = services.body.items.map((item: Kubernetes.V1Service) => {
      expect(item.metadata?.namespace).to.equal(kubernetesNamespace)

      if (item?.metadata?.name === 'booster' || item?.metadata?.name === 'fileuploader') {
        expect(item?.spec?.ports?.length).to.equal(1)
        expect(item.spec?.ports?.[0]?.nodePort?.toString()).to.not.be.undefined
        // Only minikube uses NodePort type
        expect(item.spec?.type).to.equal('NodePort')
        expect(item.metadata?.labels?.app).to.equal(item?.metadata?.name)
      }

      return item?.metadata?.name
    })

    pods.body.items.map(async (pod: Kubernetes.V1Pod) => {
      await waitForIt(
        async () => {
          return Promise.resolve('Keep trying')
        },
        () => {
          return pod?.status?.phase === 'Running'
        }
      )

      expect(pod?.status?.phase).to.be.equal('Running')
    })

    expect(serviceNames).to.include.members(boosterKubernetesServices)
  })
})
