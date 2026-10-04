# Cluster kubeadm

Stage 4.2 of `note.md` §4.3: a single-node Kubernetes cluster on the machine
`infra/terraform-azure-vms-and-acr` provisions.

Branch `infra/kubernetes-kubeadm-cluster`.

## Usage

```bash
ssh azureuser@<ip-public>
cd infra/kubernetes-kubeadm-cluster

# The public address goes into the certificate SANs. Without it the kubeconfig
# cannot be used from another machine.
export CRM_PUBLIC_IP=<ip-public>

sudo ./init-control-plane.sh
```

The script refuses to continue on a machine that is not ready, rather than
producing a cluster that is half up. A `kubeadm init` that fails two thirds of the
way through leaves a state that has to be torn down by hand, and the most common
causes are all things this checks first.

## Five things this checks before touching anything

**containerd uses `SystemdCgroup=true`.** If it does not, the kubelet puts the
node in a state that reports `Ready` while every pod fails with `failed to create
containerd task` or `OOMKilled`. That message blames memory for a driver
mismatch, and it costs hours if taken at face value.

**No swap.** The kubelet refuses to start with it on. The failure appears minutes
later complaining about the kubelet rather than about swap.

**The pod network does not overlap the VNet.** Terraform creates `10.20.0.0/16`
and the pod network is `10.244.0.0/16`. An overlap does not fail loudly: nodes
come up, pods get addresses the VNet router also hands out, and pod-to-pod traffic
leaves the machine and returns to the wrong place. Once `kubeadm init` has run, the
only remedy is to rebuild the cluster, so this is checked first.

**The addresses are substituted.** Both `REPLACE_WITH_` markers in
`kubeadm-config.yaml` are replaced before kubeadm reads the file, and the script
refuses to run if either survives. Left in place, kubeadm accepts them happily and
produces a cluster whose API server points at a host named
`REPLACE_WITH_PRIVATE_IP`.

**The API server advertises the private address.** Not the public one. The public
address is a Standard load balancer in front of the VM, and pointing the API server
at its own load balancer is a loop whose symptom is a node that never finishes
joining.

## Two things that look broken and are not

**Nodes are `NotReady` right after `kubeadm init`.** There is no CNI yet, so pod
networking does not exist. The script installs flannel and waits for `Ready`. This
is the single most common "kubeadm seems wrong" moment.

**The MySQL claim stays `Pending` without a StorageClass.** kubeadm deliberately
names no provisioner, because a storage backend is a choice an operator makes. The
script installs `local-path` and — this is the step that is easy to forget — marks
it as the **default** class, since a provisioner that is installed but not default
gives exactly the same `Pending` claim as no provisioner at all.

## What was verified, and what was not

Both scripts pass `bash -n`. `test/overlap-test.sh` covers the CIDR arithmetic in
`lib-cidr.sh` and passes **17 cases**, including the one that justifies the file:

```
10.0.0.0/16  and  10.0.1.0/24   really do overlap
```

and the base address of the first is **not** inside the second, so any check
phrased as "is A's base address in B" passes a configuration that will misroute
pod traffic. The arithmetic is plain bash rather than a `python3` call, because a
preflight check that fails for a reason unrelated to the cluster is a check people
learn to skip. The test sources `lib-cidr.sh` rather than carrying a copy, so it
tests the code that actually runs.

Two defects were found by writing and re-reading rather than by reading once:
`apiServer:` was written **twice** in the same `ClusterConfiguration` mapping, which
is a duplicate YAML key, and the kubelet ConfigMap patch was being assembled by
splicing shell into a string literal — which produces malformed JSON as soon as the
file contains a quote or a backslash anywhere, including in a comment. It is
assembled by a JSON encoder now.

**No cluster was created.** Everything here is checked as far as it can be without
a machine: the script refuses to run if `python3`, `kubeadm` or Docker is missing,
and it verifies the containerd configuration and the CIDR overlap, but
`kubeadm init` itself has not run and no pod has been scheduled.

## Growing beyond one machine

`join-worker.sh <adresse-control-plane>` exists because a lab that cannot grow is
not much of a lab. The version on the worker has to match the control plane exactly,
and the worker has to reach port 6443 — which the Terraform NSG does not currently
allow between machines, since `application_ports` opens 6443 to the public address
only. That is the change to make before trying, and the script checks reachability
*before* asking for a token, because a token is single use and expires in 24 hours
and there is no reason to burn one on a connection that cannot work.
