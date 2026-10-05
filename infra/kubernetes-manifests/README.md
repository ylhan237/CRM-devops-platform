# Kubernetes manifests

Stage 4.3 of `note.md` section 4.3: the eight workloads, MySQL as a StatefulSet,
probes on everything, TLS on the Ingress, and an Ingress that gives the whole
application one address.

Branch `infra/add-kubernetes-manifests`.

## What is here

| File | What it creates |
|---|---|
| `00-namespace.yaml` | the `crm` namespace |
| `01-mysql.yaml` | MySQL as a **StatefulSet**, its headless Service and its claim |
| `02-service-discovery.yaml` | Eureka, registered first because the rest depends on it |
| `03` to `09` | auth, student, task, notification, event, gateway, frontend |
| `10-ingress.yaml` | one host, `/api` to the gateway and the rest to the frontend, over TLS |
| `11-configmap.yaml` | addresses and ports |
| `12-secrets.example.yaml` | the **shape** of the secret, never the secret |

Nine Services and eight Deployments, which is one Service more than workload:
MySQL's is headless and backs the StatefulSet.

## Applying it

```bash
# 1. The cluster has to exist first. See infra/kubernetes-kubeadm-cluster.

# 2. The secret. Created by hand and never written into this tree, so there is
#    no file of credentials sitting next to the code.
kubectl create namespace crm --dry-run=client -o yaml | kubectl apply -f -

kubectl -n crm create secret generic crm-secrets \
  --from-literal=jwt-secret-key="$(node -e "console.log(require('crypto').randomBytes(32).toString('base64'))")" \
  --from-literal=db-username=user-service \
  --from-literal=db-password='...' \
  --from-literal=mysql-root-password='...' \
  --from-literal=mysql-database=authdb \
  --from-literal=mysql-user=user-service \
  --from-literal=mysql-password='...' \
  --from-literal=mail-username='...' \
  --from-literal=mail-password='...'

# 3. TLS. See 13-tls.md.
kubectl -n crm create secret tls crm-tls --cert=tls.crt --key=tls.key

# 4. Everything else.
kubectl apply -k .

kubectl -n crm get pods -w
```

## Three probes, not two

`note.md` asks for "les liveness, readiness, startup probes". The first two are
enough to read a Deployment, and that is exactly the trap: **the startup probe is the
one that makes the other two safe to tune.**

While it is failing, Kubernetes does not run the liveness or the readiness probe at
all. So liveness can be aggressive without becoming a way to kill a container that is
still booting.

Without it, liveness has to carry an `initialDelaySeconds` long enough for the slowest
start. That is wrong in both directions: far too long for every restart after the
first, and not long enough on a cold VM waiting for a MySQL. These manifests carried
`initialDelaySeconds: 90`, which was the delay standing in for the probe that was
missing.

| Workload | startup | Grace |
|---|---|---|
| the 7 Spring services, the frontend | 5 × 12 s | 60 s |
| `service-discovery` | 10 × 10 s | 100 s |
| MySQL | 30 × 10 s | 5 min |

MySQL is the one where this is not negotiable: a first start initialises the data
directory, and the liveness probe was killing it. A container that has not opened its
port in 5 minutes is not slow, it is broken.

## Three decisions worth arguing about

**MySQL is a StatefulSet, not a Deployment.** A Deployment may replace its pod
and come back with an empty volume, because the volume follows the pod name and a
new pod has a new name. A StatefulSet gives the pod a stable identity and a claim
it keeps. `note.md` asks for a StatefulSet and this is the case that justifies
one.

**The probes are TCP sockets, not HTTP health checks.** Only `service-discovery`
carries the Spring actuator starter, so `/actuator/health` answers on that one
service and on no other. An HTTP probe on the six others would fail for a reason
that has nothing to do with whether they are healthy. A socket probe proves the
port is being accepted, which is weaker and is honest about being weaker. Adding
the actuator to every service is the change to make if these ever need to be more
than liveness.

**Two replicas only where it is safe.** The gateway and the frontend have no local
state, so two each. auth-service has two, but its upload directory is an
`emptyDir`, so a photo written by one replica is invisible to the other. That is
noted in the manifest rather than hidden, and it is the reason to move the
uploads to a claim if availability matters more than the photos.

## Two things that will stop the apply, both by design

**MySQL's claim stays `Pending`.** A kubeadm cluster has no default
StorageClass. Either create one, or point the claim at an existing one. This is
not worked around here because a provisioner has to be installed deliberately.

**The pods stay `Pending` without the secret.** Until step 2 above has been run,
every pod reports a missing `crm-secrets` key by name in its event, which says
more than an apply failure would.

## Verified, and what was not

`kubectl kustomize .` renders 22 documents without error. Every Deployment has a
readiness probe, a liveness probe, a resources block and a selector matching its
pod labels; every Service has a selector; and all sixteen configuration keys the
pods reference, twelve in the Secret and four in the ConfigMap, are declared
where they are looked up. No key is referenced without being declared.

Nothing was applied to a cluster, so nothing here has been proven to schedule,
start or serve. There is no cluster on the machine this was written on.