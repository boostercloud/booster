export const stateStore = {
  name: 'statestore',
  template: `apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: statestore
  namespace: {{ namespace }}
  annotations:
    booster/created: "true"
spec:
  type: state.redis
  version: v1
  metadata:
  - name: redisHost
    value: {{{ eventStoreHost }}}
  - name: redisUser
    value: {{ eventStoreUsername }} 
  - name: redisPassword
    value: {{ eventStorePassword }} 
  - name: actorStateStore
    value: "true"`,
}
