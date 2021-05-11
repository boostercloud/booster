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
  type: state.mongodb
  version: v1
  metadata:
  - name: host
    value: {{{ eventStoreHost }}}
  - name: username
    value: {{ eventStoreUsername }} 
  - name: password
    secretKeyRef:
      name: eventstore-mongodb
      key: mongodb-root-password
  - name: actorStateStore
    value: "true"`,
}
