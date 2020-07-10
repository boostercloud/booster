export const boosterApp = `kind: Service
apiVersion: v1
metadata:
  name: booster
  namespace: {{ namespace }}
  labels:
    app: booster
spec:
  selector:
    app: booster
  ports:
  - protocol: TCP
    port: 80
    targetPort: 3000
  type: LoadBalancer
 
---

apiVersion: apps/v1
kind: Deployment
metadata:
  namespace: {{ namespace }}
  name: boosterapp
  labels:
    app: booster
spec:
  replicas: 2
  selector:
    matchLabels:
      app: booster
  template:
    metadata:
      labels:
        app: booster
      annotations:
        dapr.io/enabled: "true"
        dapr.io/id: "booster"
        dapr.io/port: "3000"
    spec:
      containers:
      - name: booster
        image: braeder/testboosterkubernetes:latest
        env:
        - name: BOOSTER_ENV
          value: {{ environment }}
        ports:
        - containerPort: 3000
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
          command: ['sh', '-c', "while [ ! -f /data/appCode/boosterCode.zip ]; do echo Waiting for file; sleep 5; done"]
          volumeMounts: 
            - mountPath: /data/appCode
              name: app-code
      volumes: 
      - name: app-code
        persistentVolumeClaim: 
          claimName: {{ clusterVolume }} `
