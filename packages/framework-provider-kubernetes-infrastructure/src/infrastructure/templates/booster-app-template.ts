export const boosterAppPod = {
  name: 'booster',
  template: `apiVersion: apps/v1
kind: Deployment
metadata:
  namespace: {{ namespace }}
  name: boosterapp
  labels:
    app: booster
spec:
  replicas: 2
  strategy:
    type: "RollingUpdate"
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 50%
  selector:
    matchLabels:
      app: booster
  template:
    metadata:
      labels:
        app: booster
      annotations:
        {{#timestamp}}
        booster/deployed: "{{ timestamp }}"
        {{/timestamp}}
        dapr.io/enabled: "true"
        dapr.io/app-id: "booster"
        dapr.io/port: "3000"
    spec:
      containers:
      - name: booster
        image: boostercloud/boosterkubernetes:1.0.0
        env:
        - name: BOOSTER_ENV
          value: {{ environment }}
        - name: DB_HOST
          value: {{ dbHost }}
        - name: DB_USER
          value: {{ dbUser }}
        - name: DB_PASSWORD
          value: {{ dbPass }}
        ports:
        - containerPort: 3000
        readinessProbe:
          httpGet:
              path: /ready
              port: 3000
          initialDelaySeconds: 15
          periodSeconds: 5
          successThreshold: 1
        volumeMounts:
          - mountPath: /data/appCode
            name: app-code
        imagePullPolicy: Always
      initContainers:
        - name: init-myservice
          image: busybox:1.28
          env:
          - name: BOOSTER_ENV
            value: {{ environment }}
          command: ['sh', '-c', "while [ ! -f /data/appCode/boosterCode.zip ]; do echo Waiting for user code tobe uploaded to the storage; sleep 5; done"]
          volumeMounts:
            - mountPath: /data/appCode
              name: app-code
      volumes:
      - name: app-code
        persistentVolumeClaim:
          claimName: {{ clusterVolume }} `,
}
