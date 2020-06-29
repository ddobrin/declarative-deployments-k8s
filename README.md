# Patterns for Declarative Deployments in Kubernetes

The demo applications in this repo are leveraging Spring Cloud Kubernetes and *DO NOT* use a service discovery mechanism (Consul, Eureka, etc). It runs in any Kubernetes distribution.

## Why do anything?
Isolated environments can be provisioned in a self-service manner with minimal human intervention through the Kubernetes scheduler, however, with a growing number of microservices, continually updating and replacing them with newer versions becomes an increasing burden. 

Provisioning processes allow for some or no downtime, however at the expense of increased resource consumption. 

We need to be aware that manual or scripted processes are error prone, respectively effort-intensive, which could bottleneck the software release process.


## What are Declarative Deployments?
The concept of Deployments in Kubernetes encapsulates the upgrade and rollback processes of a group of containers and makes its execution a repeatable and automated activity. 

It allows us to describe how an application should be updated, based on different strategies and allowing for the fine-tuning of the various aspects of the deployment process


## How to explore Declarative Deployment Models:
1. [Demo - Initial Setup](#1)
2. [Demo - Install the Demo Client app](#2)

### Zero downtime app deployments
3. [Rolling Deployments](#3)
4. [Fixed Deployments](#4)
5. [Blue-Green Deployments](#5)

### Deployments with app downtime 
6. [Canary Deployments](#6)


<a name="1"></a>
## Demo Setup

#### Clean-up previously deployed resources, if any
```shell
# clean-up previous images
docker images | grep message-service
docker images | grep message-service | awk '{print $3}' | xargs docker rmi -f

docker images | grep billboard-client
docker images | grep billboard-client | awk '{print $3}' | xargs docker rmi -f
```

#### Set up demo artifacts
```shell
# clone the Git repo
git clone git@github.com:ddobrin/declarative-deployments-k8s.git
cd declarative-deployments-k8s/

# build the source code
mvn clean package

# build images with tags 1.0 and 1.1 - for rolling, fixed, canary
cd build
./build-images.sh 

triathlonguy/message-service                                                                    1.0                      fcc3c30ee3c5        2 minutes ago       302MB
triathlonguy/message-service                                                                    1.1                      fcc3c30ee3c5        2 minutes ago       302MB
triathlonguy/billboard-client                                                                   1.0                      47d6fe4af2dd        2 minutes ago       285MB
triathlonguy/billboard-client                                                                   1.1                      47d6fe4af2dd        2 minutes ago       285MB

# build images with tags blue and greeen - for blue-green deployments
./build-images-bluegreen.sh

triathlonguy/message-service                                                                    1.0                      fcc3c30ee3c5        6 minutes ago       302MB
triathlonguy/message-service                                                                    1.1                      fcc3c30ee3c5        6 minutes ago       302MB
triathlonguy/message-service                                                                    blue                     fcc3c30ee3c5        6 minutes ago       302MB
triathlonguy/message-service                                                                    green                    fcc3c30ee3c5        6 minutes ago       302MB
triathlonguy/billboard-client                                                                   1.0                      47d6fe4af2dd        6 minutes ago       285MB
triathlonguy/billboard-client                                                                   1.1                      47d6fe4af2dd        6 minutes ago       285MB
triathlonguy/billboard-client                                                                   blue                     47d6fe4af2dd        6 minutes ago       285MB
triathlonguy/billboard-client                                                                   green                    47d6fe4af2dd        6 minutes ago       285MB
```
#### Setup initial K8s resources for the demo
```shell
kubectl apply -f configmap.yaml
kubectl apply -f security.yaml

# substitute values for username and password
kubectl apply -f dockercred.yaml 
```

<a name="2"></a>
# Demo - Install the Demo Client app

__Please note:__
* this demo client is to be installed after an initial deployment for one of the 4 declarative deployment demo's illustrated in this repo.

The client app is a billboard-client app displaying quotes provided by the message-service.

#### Set-up steps
```shell
# client app K8s resources
cd <repo_root>/billboard-client/k8s

# deploy the client app
kubectl apply -f deployment.yml 

# expose the app with a LoadBalancer type of service
kubectl apply -f service.yml 

# validate that the deployment is successful
kubectl get deploy

# example
NAME               READY   UP-TO-DATE   AVAILABLE   AGE
billboard-client   1/1     1            1           15h
message-service    3/3     3            3           15h

# validate that the service is available and has a Public IP assigned to it
kubectl get svc

# example
NAME               TYPE           CLUSTER-IP   EXTERNAL-IP     PORT(S)          AGE
billboard-client   LoadBalancer   10.0.14.43   34.70.147.241   8080:30423/TCP   15h
message-service    NodePort       10.0.3.237   <none>          8080:31787/TCP   15h 
```

#### Testing the back-end service using the client app
The service can be queried at the external IP and the exposed port 8080 :
```shell
curl <external IP>:8080/message

# example
> curl 34.70.147.241:8080/message
Service version: 1.1 - Quote: The shortest answer is doing -- Lord Herbert
```

#### Access the billboard-client in the browser - at the ExternalIP of the service and the associated port
![Client app](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/v1.0.png)  

__Please note:__
* The service for the client app will be available to route requests to the message-service and does not have to be restarted while testing the resepective deployment strategies

#### Client-app cleanup 
```shell
kubectl delete deploy billboard-client
```

<a name="3"></a>
# Rolling Deployment

#### Pros:
* Zero downtime during the update process
* Ability to control the rate of a new container rollout

#### Cons:
* During the update, two versions of the container are running at the same time

#### How does it work:
* Deployment creates a new ReplicaSet and the respective Pods
* Deployment replaces the old containers with the previous service version with the new ones
* Deployment allows you to control the range of available and excess Pods

![Rolling Deployment - Prior to Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/RD1.png)  


![Rolling Deployment - During Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/RD2.png)  

![Rolling Deployment - Post Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/RD3.png)  

#### The Deployment configuration
The Deployment uses the `RollingUpdate` strategy and allows full control over hopw many instances are unavailable at any given moment in time:
```yaml
kind: Deployment
metadata:
  name: message-service
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate: 
      maxSurge: 1
      maxUnavailable: 1
  selector:
    matchLabels:
      app: message-service
  template:
    metadata:
      labels:
        app: message-service
...        
```

The Service selects all nodes for the message-service matching the label:
```yaml
kind: Service
metadata:
  labels:
    app: message-service
  name: message-service
  namespace: default
spec:
...
  selector:
    app: message-service
  type: NodePort
```

#### Clean-up resources before running this demo
```shell
kubectl get deploy
kubectl get svc

# delete existing resources
kubectl delete deploy message-service
```

#### Start deploying the resources for the Rolling Deployment demo
```shell
# deploy v 1.0
> cd <repo_root>/message-service/k8s/rolling
> kubectl deployment-rolling-1.0.yml

# validate deployment
> k get pod
NAME                               READY   STATUS    RESTARTS   AGE
message-service-5566ff65cd-crf2j   1/1     Running   0          39s
message-service-5566ff65cd-f4b46   1/1     Running   0          39s
message-service-5566ff65cd-q4kd7   1/1     Running   0          39s

> k get deploy
NAME              READY   UP-TO-DATE   AVAILABLE   AGE
message-service   3/3     3            3           47s

# deploy service exposing a NodePort
kubectl apply -f service.yml

# access the endpoints on the node and run some curl requests
kubectl get ep
# example
NAME              ENDPOINTS                                         AGE
kubernetes        35.222.17.85:443                                  2d5h
message-service   10.24.0.38:8080,10.24.1.51:8080,10.24.1.52:8080   16s

# you can access the service also via the NodePort IP of the Service
kubectl get svc
# example
NAME              TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)          AGE
kubernetes        ClusterIP   10.0.0.1     <none>        443/TCP          2d5h
message-service   NodePort    10.0.3.237   <none>        8080:31787/TCP   15m

# run an NGINX pod to access the message-service using the NodePort
kubectl run nginx --restart=Never --rm --image=nginx -it -- bash
curl <select-one-message-service-endpoint>
curl <select-one-message-service-endpoint>/quotes
exit

# example 
# Service version indicates : version 1.0 at this time
# using pod endpoint
root@nginx:/# curl 10.24.0.38:8080
{"id":3,"quote":"Service version: 1.0 - Quote: Failure is success in progress","author":"Anonymous"}

root@nginx:/# curl 10.0.3.237:8080
# Service version indicates : version 1.0 at this time
# using service NodePort IP
{"id":5,"quote":"Service version: 1.0 - Quote: The shortest answer is doing","author":"Lord Herbert"}

# Update the deployment to version 1.1 
# for demo purposes, an environment variable is also modified to indicate the version
# to update only the image, the K8s set image command can be used
kubectl apply -f deployment-rolling-1.1.yml 

# example - how to update the image in a deployment
set image deployment message-service message-service=triathlonguy/message-service:1.1


# from the NGINX instance, run the curl command again the service again
# note that version is 1.1
# using service NodePort IP
# Service version indicates : version 1.1 at this time
root@nginx:/# curl 10.0.3.237:8080
{"id":2,"quote":"Service version: 1.1 - Quote: While there's life, there's hope","author":"Marcus Tullius Cicero"
```

#### Clean-up resources after running this demo for rolling deployments
```shell
kubectl get deploy
kubectl get svc

# delete existing resources
kubectl delete deploy message-service
```

<a name="4"></a>
# Fixed Deployment

#### Pros:
* Single version serves requests at any moment in time
* Simpler process for service consumers, as they do not have to handle multiple versions at the same time

#### Cons:
* There is downtime while old containers are stopped, and the new ones are starting

#### How does it work:
* Deployment stops first all containers deployed for the old version
* Clients experience an outage, as no service is available to process requests
* Deployment creates the new containers
* Client accesses requests serviced by the new version

![Fixed Deployment - Prior to Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/FD1.png)  

![Fixed Deployment - During Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/FD2.png)  

![Fixed Deployment - Post Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/FD3.png)  

#### The Deployment configuration
The Deployment uses the `Recreate` strategy as it terminates all pods from a deployment before creating the pods for the new version:
```yaml
kind: Deployment
metadata:
  name: message-service
spec:
  replicas: 3
  strategy:
    type: Recreate
  selector:
    matchLabels:
      app: message-service
...        
```

The Service selects all nodes for the message-service matching the label:
```yaml
kind: Service
metadata:
  labels:
    app: message-service
  name: message-service
  namespace: default
spec:
...
  selector:
    app: message-service
  type: NodePort
```

#### Clean-up resources before running this demo
```shell
kubectl get deploy
kubectl get svc

# delete existing resources
kubectl delete deploy message-service
```
#### Start deploying the resources for the Fixed Deployment demo
```shell
# deploy v 1.0
> cd <repo_root>/message-service/k8s/fixed
> kubectl apply -f deployment-fixed-1.0.yml

# validate deployment
> k get pod
# example
NAME                                READY   STATUS    RESTARTS   AGE
message-service-5566ff65cd-c5872    1/1     Running   0          19m
message-service-5566ff65cd-gvcqj    1/1     Running   0          19m
message-service-5566ff65cd-mxqmj    1/1     Running   0          19m

> k get deploy
# example
NAME              READY   UP-TO-DATE   AVAILABLE   AGE
message-service   3/3     3            3           47s

# deploy service exposing a NodePort
kubectl apply -f service.yml

# access the endpoints on the node and run some curl requests
kubectl get ep
# example
NAME               ENDPOINTS                                         AGE
billboard-client   10.24.1.62:8080                                   17h
kubernetes         35.222.17.85:443                                  2d23h
message-service    10.24.0.41:8080,10.24.1.63:8080,10.24.1.64:8080   17h

# you can access the service also via the NodePort IP of the Service
kubectl get svc
# example
NAME               TYPE           CLUSTER-IP   EXTERNAL-IP     PORT(S)          AGE
billboard-client   LoadBalancer   10.0.14.43   34.70.147.241   8080:30423/TCP   17h
kubernetes         ClusterIP      10.0.0.1     <none>          443/TCP          2d23h
message-service    NodePort       10.0.3.237   <none>          8080:31787/TCP   17h

# run an NGINX pod to access the message-service using the NodePort
kubectl run nginx --restart=Never --rm --image=nginx -it -- bash
curl <select-one-message-service-endpoint>
curl <select-one-message-service-endpoint>/quotes
exit

# example 
# Service version indicates : version 1.0 at this time
# using pod endpoint
root@nginx:/# curl 10.24.0.41:8080
{"id":3,"quote":"Service version: 1.0 - Quote: Failure is success in progress","author":"Anonymous"}

root@nginx:/# curl 10.0.3.237:8080
# Service version indicates : version 1.0 at this time
# using service NodePort IP
{"id":5,"quote":"Service version: 1.0 - Quote: The shortest answer is doing","author":"Lord Herbert"}

# Update the deployment to version 1.1 
# for demo purposes, an environment variable is also modified to indicate the version
# to update only the image, the K8s set image command can be used
kubectl apply -f deployment-fixed-1.1.yml 

# example - how to update the image in a deployment
set image deployment message-service message-service=triathlonguy/message-service:1.1
```

#### Observe how all pods are being shut down, befoire the pods for the new version are being started
```shell
> kubectl get pod
NAME                                READY   STATUS        RESTARTS   AGE
billboard-client-6f6d7858d9-qhmv9   1/1     Running       0          17h
message-service-5566ff65cd-c5872    1/1     Terminating   0          28m
message-service-5566ff65cd-gvcqj    1/1     Terminating   0          28m
message-service-5566ff65cd-mxqmj    1/1     Terminating   0          28m

message-service-58744dd6c9-jsprc    0/1     Pending       0          0s
message-service-58744dd6c9-jsprc    0/1     ContainerCreating   0          0s
message-service-58744dd6c9-r5bkq    0/1     Pending             0          0s
message-service-58744dd6c9-r5bkq    0/1     ContainerCreating   0          0s
message-service-58744dd6c9-bxswq    0/1     Pending             0          0s
message-service-58744dd6c9-bxswq    0/1     ContainerCreating   0          0s
message-service-58744dd6c9-r5bkq    1/1     Running             0          2s
message-service-58744dd6c9-bxswq    1/1     Running             0          2s
message-service-58744dd6c9-jsprc    1/1     Running             0          2s

billboard-client-6f6d7858d9-qhmv9   1/1     Running   0          17h
message-service-58744dd6c9-bxswq    1/1     Running   0          25s
message-service-58744dd6c9-jsprc    1/1     Running   0          25s
message-service-58744dd6c9-r5bkq    1/1     Running   0          25s
```

#### Observe the updated deployment by running a fresh curl request
```shell
# from the NGINX instance, run the curl command again the service again
# note that version is 1.1
# using service NodePort IP
# Service version indicates : version 1.1 at this time
root@nginx:/# curl 10.0.3.237:8080
{"id":2,"quote":"Service version: 1.1 - Quote: While there's life, there's hope","author":"Marcus Tullius Cicero"
```

#### Clean-up resources after running this demo for fixed deployments
```shell
kubectl get deploy
kubectl get svc

# delete existing resources
kubectl delete deploy message-service
kubectl delete svc message-service
```

<a name="5"></a>
# Blue-Green Deployment

#### Pros:
* Single version serves requests at any moment in time
* Zero downtime during the update
* Allows precise control of switching to the new version

#### Cons:
* Requires 2x capacity while both blue and green versions are up
* Manual intervention for switch

#### How does it work :
* A second Deployment is created manually for the new version (green)
* The new version (green) does not serve client requests yet and can be tested internally to validate the deployment
* The Service Selector in K8s is being updated to route traffic to the new version (green), followed by the removal of the old (blue) Deployment

![Blue-Green Deployment - Prior to Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/BGD1.png)  

![Blue-Green Deployment - During Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/BGD2.png)  

![Blue-Green Deployment - Post Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/BGD3.png)  

#### The Deployment configuration
The Deployment does not provide a specific strategy, as the service exposing the deployment is the K8s resource participating in the deployment process which selects which pod instances are exposed to client requests. In this excerpt, 2 labels are used, `message-service` and `blue`:
```yaml
kind: Deployment
metadata:
  name: message-service-blue
spec:
  replicas: 3
  selector:
    matchLabels:
      app: message-service
  template:
    metadata:
      labels:
        app: message-service
        version: blue
...        
```

The Service selects all nodes for the message-service matching multiple labels: the app + the version. This allows the selection of the pods matching a specific version. This excerpt has the Service match 2 labels `message-service` and 'blue`:
```yaml
kind: Service
metadata:
  labels:
    app: message-service
  name: message-service
  namespace: default
spec:
...
  selector:
    app: message-service
    version: blue
  type: NodePort
```

#### Clean-up resources before running this Blue-green demo
```shell
kubectl get deploy
kubectl get svc

# delete existing resources
kubectl delete deploy message-service-blue
kubectl delete deploy message-service-green
```

#### Start deploying the resources for the Blue-green Deployment demo
```shell
# deploy v 1.0
> cd <repo_root>/message-service/k8s/bluegreen
> kubectl apply -f deployment-blue.yml

# validate deployment
> kubectl get pod
# example
NAME                                     READY   STATUS    RESTARTS   AGE
billboard-client-6f6d7858d9-qhmv9        1/1     Running       0          20h
message-service-blue-5f77f88f4f-hk99f    1/1     Running       0          3s
message-service-blue-5f77f88f4f-j6hvg    1/1     Running       0          3s
message-service-blue-5f77f88f4f-lgq9x    1/1     Running       0          3s

> kubectl get deploy
NAME                   READY   UP-TO-DATE   AVAILABLE   AGE
billboard-client       1/1     1            1           20h
message-service-blue   3/3     3            3           20s

# deploy service exposing a NodePort
kubectl apply -f service-blue.yml

# access the endpoints on the node and run some curl requests
kubectl get ep
# example
NAME               ENDPOINTS                                         AGE
billboard-client   10.24.1.62:8080                                   20h
kubernetes         35.222.17.85:443                                  3d2h
message-service    10.24.0.47:8080,10.24.0.48:8080,10.24.1.75:8080   6s

# you can access the service also via the NodePort IP of the Service
kubectl get svc
# example
# note the selector matching the label "blue"
NAME               TYPE           CLUSTER-IP    EXTERNAL-IP     PORT(S)          AGE    SELECTOR 
billboard-client   LoadBalancer   10.0.14.43    34.70.147.241   8080:30423/TCP   21h    app=billboard-client
kubernetes         ClusterIP      10.0.0.1      <none>          443/TCP          3d3h   <none>
message-service    NodePort       10.0.11.113   <none>          8080:32072/TCP   18m    app=message-service,version=blue

# run an NGINX pod to access the message-service using the NodePort
kubectl run nginx --restart=Never --rm --image=nginx -it -- bash
curl <select-one-message-service-endpoint>
curl <select-one-message-service-endpoint>/quotes
exit

# example 
# Service version indicates : version "blue" at this time
# using pod endpoint
root@nginx:/#  curl 10.24.0.47:8080
{"id":3,"quote":"Service version: 1.0 - Quote: Failure is success in progress","author":"Anonymous"}

root@nginx:/# curl 10.0.11.113:8080
# Service version indicates : version 1.0 at this time
# using service NodePort IP
{"id":5,"quote":"Service version: 1.0 - Quote: The shortest answer is doing","author":"Lord Herbert"}
```

#### Update the deployment to version "green" 
```shell
# for demo purposes, an environment variable is also modified to indicate the version "green"
# to update only the image, the K8s set image command can be used
kubectl apply -f deployment-green.yml 

# example - how to update the image in a deployment
set image deployment message-service message-service=triathlonguy/message-service:green
```

#### Observe the newly added green deployment, in parallel with the blue deployment

```shell
> kubectl get deploy
# example
NAME                    READY   UP-TO-DATE   AVAILABLE   AGE
billboard-client        1/1     1            1           21h
message-service-blue    3/3     3            3           10m
message-service-green   3/3     3            3           4s

> kubectl get pod
# example
NAME                                     READY   STATUS    RESTARTS   AGE
billboard-client-6f6d7858d9-qhmv9        1/1     Running   0          21h
message-service-blue-5f77f88f4f-hk99f    1/1     Running   0          12m
message-service-blue-5f77f88f4f-j6hvg    1/1     Running   0          12m
message-service-blue-5f77f88f4f-lgq9x    1/1     Running   0          12m
message-service-green-5b745bd4f6-2k9zz   1/1     Running   0          2m15s
message-service-green-5b745bd4f6-n29gw   1/1     Running   0          2m15s
message-service-green-5b745bd4f6-pp44h   1/1     Running   0          2m15s

# from the NGINX instance, run the curl command again the service again
# note that version is still "blue"
# using service NodePort IP
# Service version indicates : version blue at this time
root@nginx:/# curl 10.0.11.113:8080
{"id":2,"quote":"Service version: blue - Quote: While there's life, there's hope","author":"Marcus Tullius Cicero"}

# you can use also the external IP of the billboard-client
> curl 34.70.147.241:8080/message
# example
Service version: blue - Quote: Failure is success in progress -- Anonymous
```

#### The service still matches the same pods with selector "blue"

#### Switch traffic by updating the service definition to use the selector "green"
```yaml
apiVersion: v1
kind: Service
metadata:
 ...
  labels:
    app: message-service
  name: message-service
  namespace: default
  resourceVersion: "1043830"
  selfLink: /api/v1/namespaces/default/services/message-service
  uid: 41f79216-b260-11ea-bb6b-42010a800107
spec:
  clusterIP: 10.0.11.113
  externalTrafficPolicy: Cluster
  ports:
  - nodePort: 32072
    port: 8080
    protocol: TCP
    targetPort: 8080
  selector:
    app: message-service
    version: green
 ...
```

```shell
> kubectl apply -f service-green.yml
# example
# Note the selector matching the label "green"
NAME               TYPE           CLUSTER-IP    EXTERNAL-IP     PORT(S)          AGE    SELECTOR
billboard-client   LoadBalancer   10.0.14.43    34.70.147.241   8080:30423/TCP   21h    app=billboard-client
kubernetes         ClusterIP      10.0.0.1      <none>          443/TCP          3d3h   <none>
message-service    NodePort       10.0.11.113   <none>          8080:32072/TCP   18m    app=message-service,version=green

# using the external IP of the billboard-client, we can validate that the traffic has moved to the green deployment
> curl 34.70.147.241:8080/message
Service version: green - Quote: Success demands singleness of purpose -- Vincent Lombardi

# from within the NGINX instance, we can use the NodePort IP of the message-service
> curl 10.0.11.113:8080
{"id":2,"quote":"Service version: green - Quote: While there's life, there's hope","author":"Marcus Tullius Cicero"}
```

#### At this time, the Blue deployment can safely be removed
```shell
> kubectl delete deploy message-service-blue
```

#### Clean-up resources after running this demo for rolling deployments
```shell
kubectl get deploy
kubectl get svc

# delete existing resources
kubectl delete deploy message-service-blue
kubectl delete deploy message-service-green
kubectl delete svc message-service
```

<a name="6"></a>
# Canary Deployment


#### Pros:
* Reduces the risk of a new service version by controlling access to the new version to a subset of consumers
* Allows precise control of full switch to the new version

#### Cons:
* Manual intervention for switch
* Consumers failing to handle multiple versions simultaneously see failures

#### How does it work:
* A second Deployment is created manually for the new version (canary) with a small set of instances
* Some of the client requests are now redirected to the canary version
* Once there is confidence that the canary version works as expected, traffic is fully scaled up for the canary version and scaled to zero for the old one

![Canary Deployment - Prior to Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/CD1.png)  

![Canary Deployment - During Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/CD2.png)  

![Canary Deployment - Post Deployment](https://github.com/ddobrin/declarative-deployments-k8s/blob/master/images/CD3.png)  

#### The Deployment configuration
The Deployment does not provide a specific strategy, as the service exposing the deployment is the K8s resource participating in the deployment process which selects which pod instances are exposed to client requests. In this excerpt, 2 labels are used, `message-service` and `1.0`, the canary deployment will use `message-service` and `canary`, which will allow the Service to match pods by a single label `message-service`:
```yaml
# initial deployment
kind: Deployment
metadata:
  name: message-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: message-service
  template:
    metadata:
      labels:
        app: message-service
        version: "1.0"
...        
# canary deployment
kind: Deployment
metadata:
  name: message-service-canary
spec:
  replicas: 1
  selector:
    matchLabels:
      app: message-service
  template:
    metadata:
      labels:
        app: message-service
        version: canary
...
```

The Service selects all nodes for the message-service matching the labels for the app:
```yaml
kind: Service
metadata:
  labels:
    app: message-service
  name: message-service
  namespace: default
spec:
...
  selector:
    app: message-service
  type: NodePort
```

#### Clean-up resources before running this Canary Deployment demo
```shell
kubectl get deploy
kubectl get svc

# delete existing resources
kubectl delete deploy message-service
```
#### Start deploying the resources for the Canary Deployment demo
```shell
# deploy v 1.0
> cd <repo_root>/message-service/k8s/canary
> kubectl apply -f deployment-1.0.yml

# validate deployment
> kubectl get pod
# example
NAME                                READY   STATUS    RESTARTS   AGE
billboard-client-6f6d7858d9-qhmv9   1/1     Running   0          24h
message-service-5566ff65cd-6mpgv    1/1     Running   0          23s
message-service-5566ff65cd-jqxkq    1/1     Running   0          23s
message-service-5566ff65cd-mjp4q    1/1     Running   0          23s

> kubectl get deploy
# example
NAME               READY   UP-TO-DATE   AVAILABLE   AGE
billboard-client   1/1     1            1           24h
message-service    3/3     3            3           46s

# deploy service exposing a NodePort
> kubectl apply -f service-canary.yml 

# access the endpoints on the node and run some curl requests
> kubectl get ep
# example
NAME               ENDPOINTS                                         AGE
billboard-client   10.24.1.62:8080                                   24h
kubernetes         35.222.17.85:443                                  3d6h
message-service    10.24.0.53:8080,10.24.1.81:8080,10.24.1.82:8080   20s

# you can access the service also via the NodePort IP of the Service
> kubectl get svc
# example
NAME               TYPE           CLUSTER-IP   EXTERNAL-IP     PORT(S)          AGE
billboard-client   LoadBalancer   10.0.14.43   34.70.147.241   8080:30423/TCP   24h
kubernetes         ClusterIP      10.0.0.1     <none>          443/TCP          3d6h
message-service    NodePort       10.0.15.51   <none>          8080:30296/TCP   57s

# access the message-service using the public IP of the billboard-client
> curl 34.70.147.241:8080/message
Service version: 1.0 - Quote: Never, never, never give up -- Winston Churchill

# run an NGINX pod to access the message-service using the NodePort
> kubectl run nginx --restart=Never --rm --image=nginx -it -- bash
curl <select-one-message-service-endpoint>
curl <select-one-message-service-endpoint>/quotes
exit

# example 
# Service version indicates : version 1.0 at this time
# using pod endpoint
root@nginx:/# curl 10.24.0.53:8080
{"id":5,"quote":"Service version: 1.0 - Quote: The shortest answer is doing","author":"Lord Herbert"}

root@nginx:/# curl curl 10.0.15.51:8080
# Service version indicates : version 1.0 at this time
# using service NodePort IP
{"id":4,"quote":"Service version: 1.0 - Quote: Success demands singleness of purpose","author":"Vincent Lombardi"}
```
#### Scale down version 1.0 of the service from 3 to 2 replicas and deploy the canary version, v 1.1, with one replica
#### The number of instances running at the same time remains the same

```shell
> kubectl scale deploy message-service --replicas=2
> kubectl get pod
# example
NAME                                READY   STATUS        RESTARTS   AGE
billboard-client-6f6d7858d9-qhmv9   1/1     Running       0          24h
message-service-5566ff65cd-6mpgv    1/1     Running       0          9m11s
message-service-5566ff65cd-jqxkq    1/1     Running       0          9m11s
message-service-5566ff65cd-mjp4q    1/1     Terminating   0          9m11s

NAME                                READY   STATUS    RESTARTS   AGE
billboard-client-6f6d7858d9-qhmv9   1/1     Running   0          24h
message-service-5566ff65cd-6mpgv    1/1     Running   0          15m
message-service-5566ff65cd-jqxkq    1/1     Running   0          15m

# Create the deployment with version 1.1 with 1 single replica, to maintain the previous replica count
# for demo purposes, an environment variable is also modified to indicate the version
> kubectl apply -f deployment-canary.yml 
# example
NAME                                      READY   STATUS    RESTARTS   AGE
billboard-client-6f6d7858d9-qhmv9         1/1     Running   0          25h
message-service-5566ff65cd-6mpgv          1/1     Running   0          36m
message-service-5566ff65cd-jqxkq          1/1     Running   0          36m
message-service-canary-747cbf84b7-jtrqx   1/1     Running   0          5s
```

#### Requests will be load-balanced between the old version (1.0) and the new one (canary) according to the ratio of the running pods
```shell
# run an NGINX pod to access the message-service using the NodePort
> kubectl run nginx --restart=Never --rm --image=nginx -it -- bash
curl <select-one-message-service-endpoint>
curl <select-one-message-service-endpoint>/quotes
exit

# example 
# Service version indicates : version 1.0 at this time
# using pod endpoint
root@nginx:/# curl 10.24.1.81:8080 
{"id":5,"quote":"Service version: 1.0 - Quote: The shortest answer is doing","author":"Lord Herbert"}

# Access the canary service : version "canary" at this time
root@nginx:/# curl 10.24.1.83:8080
{"id":3,"quote":"Service version: canary - Quote: Failure is success in progress","author":"Anonymous"}

# exit the NGINX instance
exit

# accessing the service using the load balanced billboard-client service illustrates how requests are load balanced against the 1.0 respectively canary versions
> curl  34.70.147.241:8080/message
Service version: 1.0 - Quote: Never, never, never give up -- Winston Churchill
> curl  34.70.147.241:8080/message
Service version: canary - Quote: The shortest answer is doing -- Lord Herbert
> curl  34.70.147.241:8080/message
Service version: 1.0 - Quote: Success demands singleness of purpose -- Vincent Lombardi
```

#### The canary deployment can be scaled up to the original number of instances for version 1.0
#### Version 1.0 instances can be deleted at this time, without downtime to service clients
```shell
> kubectl scale deploy message-service-canary --replicas=3
> kubectl delete deploy message-service-canary

# example
NAME                                      READY   STATUS        RESTARTS   AGE
billboard-client-6f6d7858d9-qhmv9         1/1     Running       0          2d20h
message-service-5566ff65cd-6mpgv          1/1     Terminating   0          43h
message-service-5566ff65cd-jqxkq          1/1     Terminating   0          43h
message-service-canary-747cbf84b7-b5xgj   1/1     Running       0          12s
message-service-canary-747cbf84b7-jtrqx   1/1     Running       0          43h
message-service-canary-747cbf84b7-sjdm4   1/1     Running       0          12s
...
NAME                                      READY   STATUS    RESTARTS   AGE
billboard-client-6f6d7858d9-qhmv9         1/1     Running   0          2d20h
message-service-canary-747cbf84b7-b5xgj   1/1     Running   0          73s
message-service-canary-747cbf84b7-jtrqx   1/1     Running   0          43h
message-service-canary-747cbf84b7-sjdm4   1/1     Running   0          73s

# number of pods reflect the desired replicas for the canary version of the service
# the instances for the previous version have been deleted
```

#### Clean-up resources after running this demo for canary deployments
```shell
kubectl get deploy
kubectl get svc

# delete existing resources
kubectl delete deploy message-service
kubectl delete svc message-service
```