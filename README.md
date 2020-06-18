# Demo for Declarative Deployments in Kubernetes

The demo applications in this repo are leveraging Spring Cloud Kubernetes and *DO NOT* use a service discovery mechanism (Consul, Eureka, etc). It runs in any Kubernetes distribution.

## The Kubernetes model for connecting containers
Once you have a continuously running, replicated application you can expose it on a network. Before discussing the Kubernetes approach to networking, it is worthwhile to contrast it with the “normal” way networking works with Docker.

By default, Docker uses host-private networking, so containers can talk to other containers only if they are on the same machine. In order for Docker containers to communicate across nodes, there must be allocated ports on the machine’s own IP address, which are then forwarded or proxied to the containers. This obviously means that containers must either coordinate which ports they use very carefully or ports must be allocated dynamically.

Coordinating port allocations across multiple developers or teams that provide containers is very difficult to do at scale, and exposes users to cluster-level issues outside of their control. Kubernetes assumes that pods can communicate with other pods, regardless of which host they land on. Kubernetes gives every pod its own cluster-private IP address, so you do not need to explicitly create links between pods or map container ports to host ports. This means that containers within a Pod can all reach each other’s ports on localhost, and all pods in a cluster can see each other without NAT. The rest of this document elaborates on how you can run reliable services on such a networking model.

How to explore the declarative models:
1. [Demo - Initial Setup](#1)
2. [Demo - install demo client](#2)

### Zero downtime app deployments
3. [Rolling deployments](#3)
4. [Fixed deployments](#4)
5. [Blue-Green deployments](#5)

### Deployments with app downtime 
6. [Canary deployments](#6)


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
#### Setup K8s resources
```shell
kubectl apply -f configmap.yaml
kubectl apply -f security.yaml

# substitute values for username and password
kubectl apply -f dockercred.yaml 
```
<a name="2"></a>
# Rolling Deployment
```shell
kubectl get deploy
kubectl get svc

# deploy v 1.0
cd .../message-service/k8s/rolling
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

# deploy service 
kubectl apply -f sevrice.yml

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
curl <select-one-endpoint>
curl <select-one-endpoint>/quotes
exit

# example 
# note that version is 1.0
root@nginx:/# curl 10.24.0.38:8080
{"id":3,"quote":"Service version: 1.0 - Quote: Failure is success in progress","author":"Anonymous"}

root@nginx:/# curl 10.0.3.237:8080
# note that version is 1.0
{"id":5,"quote":"Service version: 1.0 - Quote: The shortest answer is doing","author":"Lord Herbert"}root@nginx:/# curl 10.0.3.237:31787

# Update the deployment to version 1.1 
# for demo purposes, an environment variable is also modified to indicate the version
kubectl apply -f deployment-rolling-1.1.yml 

# from the NGINX instance, run the curl command again the service again
# note that version is 1.1
root@nginx:/# curl 10.0.3.237:8080
{"id":2,"quote":"Service version: 1.1 - Quote: While there's life, there's hope","author":"Marcus Tullius Cicero"
```