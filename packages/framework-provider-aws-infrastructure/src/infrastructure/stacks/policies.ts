import { PolicyStatement } from '@aws-cdk/aws-iam'

export const createPolicyStatement = (resources?: string[], actions?: string[]) => {
  return new PolicyStatement({
    resources,
    actions,
  })
}
