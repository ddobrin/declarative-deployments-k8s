# Demo for Declarative Deployments in Kubernetes

The demo applications in this repo are leveraging Spring Cloud Kubernetes and *DO NOT* use a service discovery mechanism (Consul, Eureka, etc). It runs in any Kubernetes distribution.

## The Kubernetes model for connecting containers
Once you have a continuously running, replicated application you can expose it on a network. Before discussing the Kubernetes approach to networking, it is worthwhile to contrast it with the “normal” way networking works with Docker.

By default, Docker uses host-private networking, so containers can talk to other containers only if they are on the same machine. In order for Docker containers to communicate across nodes, there must be allocated ports on the machine’s own IP address, which are then forwarded or proxied to the containers. This obviously means that containers must either coordinate which ports they use very carefully or ports must be allocated dynamically.

Coordinating port allocations across multiple developers or teams that provide containers is very difficult to do at scale, and exposes users to cluster-level issues outside of their control. Kubernetes assumes that pods can communicate with other pods, regardless of which host they land on. Kubernetes gives every pod its own cluster-private IP address, so you do not need to explicitly create links between pods or map container ports to host ports. This means that containers within a Pod can all reach each other’s ports on localhost, and all pods in a cluster can see each other without NAT. The rest of this document elaborates on how you can run reliable services on such a networking model.

How to explore the declarative models:
1. [Demo - Initial Setup](#1)
2. [Demo - Install demo Client app](#2)

### Zero downtime app deployments
3. [Rolling Deployments](#3)
4. [Fixed Deployments](#4)
5. [Blue-Green Deployments](#5)

### Deployments with app downtime 
6. [Canary Deployments](#6)


<a name="1"></a>
## Demo Setup
```shell
# clean-up previous images
docker images | grep message-service
docker images | grep message-service | awk '{print $3}' | xargs docker rmi -f

docker images | grep billboard-client
docker images | grep billboard-client | awk '{print $3}' | xargs docker rmi -f

```shell
# clone the Git repo
git clone git@github.com:ddobrin/declarative-deployments-k8s.git
cd declarative-deployments-k8s/

# build the code
mvn clean package

# build images with tags 1.0 and 1.1 - for rolling, fixed, canary
cd build
./build-images.sh 

triathlonguy/message-service                                                                    1.0                      fcc3c30ee3c5        2 minutes ago       302MB
triathlonguy/message-service                                                                    1.1                      fcc3c30ee3c5        2 minutes ago       302MB
triathlonguy/billboard-client                                                                   1.0                      47d6fe4af2dd        2 minutes ago       285MB
triathlonguy/billboard-client                                                                   1.1                      47d6fe4af2dd        2 minutes ago       285MB

# build images with tags blue and greeen - rof blue-green deployments
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
# Demo - Install demo Client app

#### Please note :
* this demo client is to be installed after an initial deployment for one of the 4 declarative deployment demo's illustrated in this repo.

The client app is a billboard-client app displaying quotes provided by the message-service:
```shell
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

The service can be queried at the external IP and the exposed port 8080
```shell
curl <external IP>:8080/message

# example
> curl 34.70.147.241:8080/message
Service version: 1.1 - Quote: The shortest answer is doing -- Lord Herbert
```

#### Please note:
* The service for the client app will be available to route requests to the message-service and does not have to be restarted after testing various deployment strategies

The Client app clean-up : 
```shell
kubectl delete deploy billboard-client
```

<a name="3"></a>
# Rolling Deployment

Clean-up resources before running this demo :
```shell
kubectl get deploy
kubectl get svc

# delete existing resources
kubectl delete deploy message-service
```

Start deploying the resources for the demo :
```shell
# deploy v 1.0
cd <repo_root>/message-service/k8s/rolling
kubectl deployment-rolling-1.0.yml

# validate deployment
dandobrin@vmwin-008:~/work/demo/declarative-deployments-k8s/message-service/k8s/rolling:>k get pod
NAME                               READY   STATUS    RESTARTS   AGE
message-service-5566ff65cd-crf2j   1/1     Running   0          39s
message-service-5566ff65cd-f4b46   1/1     Running   0          39s
message-service-5566ff65cd-q4kd7   1/1     Running   0          39s
dandobrin@vmwin-008:~/work/demo/declarative-deployments-k8s/message-service/k8s/rolling:>k get deploy
NAME              READY   UP-TO-DATE   AVAILABLE   AGE
message-service   3/3     3            3           47s
dandobrin@vmwin-008:~/work/demo/declarative-deployments-k8s/message-service/k8s/rolling:>

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
NAME              TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)          AGE
kubernetes        ClusterIP   10.0.0.1     <none>        443/TCP          2d5h
message-service   NodePort    10.0.3.237   <none>        8080:31787/TCP   15m

# run an NGINX pod
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
{"id":5,"quote":"Service version: 1.0 - Quote: The shortest answer is doing","author":"Lord Herbert"}root@nginx:/# curl 10.0.3.237:31787

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

Clean-up resources before running this demo :
```shell
kubectl get deploy
kubectl get svc

# delete existing resources
kubectl delete deploy message-service
```

<a name="4"></a>
# Fixed Deployment


<a name="5"></a>
# Blue-Green Deployment

<a name="6"></a>
# Canary Deployment

