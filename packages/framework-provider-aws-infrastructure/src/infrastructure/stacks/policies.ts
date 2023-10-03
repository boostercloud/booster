import { Effect, PolicyStatement } from 'aws-cdk-lib/aws-iam'

export const createPolicyStatement = (
  resources?: string[],
  actions?: string[],
  effect: Effect = Effect.ALLOW
): PolicyStatement => {
  return new PolicyStatement({
    resources,
    actions,
    effect,
  })
}
