# Helm chart

Stage 4.4 of `note.md` section 4.3. The same application as
`infra/kubernetes-manifests`, parameterised: nine Services and eight Deployments,
MySQL as a StatefulSet, probes everywhere, TLS on the Ingress.

Branch `infra/add-helm-chart`.

## Publishing it

`note.txt` 9 asks for a release and a publication once the chart exists. The
workflow `.github/workflows/publish-chart.yml` does that, on a `chart-v*` tag:

```bash
helm show chart infra/helm-chart | grep version   # say 0.1.0
# bump version in Chart.yaml, commit, then
git tag chart-v0.1.0
git push origin chart-v0.1.0
```

It packages the chart and attaches the `.tgz` to a GitHub release, which Helm reads
directly. Installing then needs no clone:

```bash
helm repo add crm https://ylhan237.github.io/CRM-devops-platform
helm repo update
helm install crm crm/crm --version 0.1.0
```

`version` and `appVersion` are separate on purpose. `appVersion` is the release the
images were published under; `version` is the packaging. Bumping only `appVersion`
gives new images with no change to the chart and therefore no chart release. The
workflow refuses to publish if the tag and `Chart.yaml` disagree, since
`--version 0.1.0` resolving to nothing is a bad way to find that out.

**Not a dedicated repository.** `note.txt` says one is an option. A separate repo
would mean publishing the chart from two places, and the chart refers to image
versions that live here. The releases URL already serves it; splitting it is a change
to make when two charts need independent versioning.

Three things are checked before anything is published, because a published artefact
is harder to retract than a fixed commit:

- `helm lint --strict`
- the rendered chart still produces **nine** workloads. `helm package` does not check
  this, and a chart that packages while rendering four of its nine Deployments is a
  release nobody notices until it is installed
- the `.tgz` contains no `tls.key`, `tls.crt`, `.env` or `terraform.tfvars`. The
  archive is the artefact, so its contents are inspected rather than the directory's:
  a `.helmignore` that stops matching ships a credential silently

Verified: `helm lint --strict` passes, `helm template` renders the nine workloads,
and `helm package` produces a 16-file archive with no credential in it.

## Why both, and not one

The plain manifests are the reference. They are readable top to bottom, which is
what you want when something is broken at three in the morning, and they are what
gets reviewed in a pull request because a diff of a rendered chart is unreadable.

The chart is what gets installed twice, or installed once and then overridden for a
second environment. `examples/dev-values.yaml` is that second environment: six
numbers changed, no port and no environment variable restated.

## Usage

```bash
helm lint .
helm template crm . --namespace crm > /tmp/rendered.yaml

kubectl create namespace crm
kubectl -n crm create secret generic crm-secrets \
  --from-literal=jwt-secret-key="$(openssl rand -base64 32)" \
  --from-literal=db-username=user-service \
  --from-literal=db-password='...' \
  --from-literal=mysql-root-password='...' \
  --from-literal=mysql-database=authdb \
  --from-literal=mysql-user=user-service \
  --from-literal=mysql-password='...' \
  --from-literal=mail-username='...' \
  --from-literal=mail-password='...'

helm install crm . --namespace crm

kubectl -n crm get pods -w
```

`helm install` prints the two or three things that are about to go wrong, in the
order they will go wrong. That is what `templates/NOTES.txt` is for, and it is
not a list of every object created: nobody reads eleven paragraphs of kind/name,
and the one line that matters is the one buried in the middle.

## Three decisions

**The Secret is not created by default.** A Secret rendered by Helm is stored
base64-encoded in the release object inside the cluster, so anyone who can read
releases can read the credentials, and `--debug` prints them to the terminal and
into whatever captured the output. A Secret made with `kubectl create secret` is
stored nowhere else. `secrets.create: true` exists for a throwaway lab and every
value in it is `required`, so a half-filled file fails at install rather than
producing a pod that cannot reach its database.

**`backendServices` is a map keyed by service name, not a list.** This is the
correction that mattered most, and it came out of a test rather than out of
thinking. Helm merges maps key by key and lists element by element, so with a
list an environment overriding `replicas` has to restate the whole list at the
same indexes. The first version of this file did exactly that and it worked,
because the override list happened to have the same length and the same order as
the defaults. Add a sixth service to `values.yaml` and every one of those
overrides silently applies to the wrong service. As a map, the override is
`backendServices: {auth-service: {replicas: 1}}` and nothing else can shift.
Verified by adding `audit-service` to an override and confirming the other five
kept their datasource URLs and ports.

**MySQL keeps its claim on uninstall.** `persistentVolumeClaimRetentionPolicy` is
`Retain` by default and not by omission. Without it `helm uninstall` takes the
claim with the StatefulSet, and the reinstall comes back with an empty database
that looks like a working one until a query returns nothing.

## What the chart refuses to do

Two `fail` guards, both tested:

| Values | Result |
|---|---|
| `mysql.persistence.enabled: false` and `allowEmptyDir: false` | exit 1, "A MySQL with no volume loses its data on every restart" |
| `secrets.create: true` with `jwtSecretKey` empty | exit 1, "secrets.jwtSecretKey must be set" with the command to generate one |

A third case is a real, reachable state rather than a refusal: with
`secrets.create: false` and `mysql.initScripts.grantUser` empty, the chart knows
neither the database account name nor its password, and a `.sql` file cannot read
an environment variable. The schema script is rendered, the grants are not, and
`NOTES.txt` prints the statements to run once. That is better than rendering
`GRANT ALL ... TO ''@'%'`, which fails at first boot with a message about
authentication when the real problem is an empty name.

## Two things that are weaker than they look

**The probes prove a port is open.** Only `service-discovery` carries the Spring
actuator starter, so `/actuator/health` answers there and on the other eight
workloads an HTTP probe would fail for a reason that has nothing to do with
whether they are healthy. Every other probe is a TCP socket. Adding the actuator
everywhere is the change to make if these ever need to be more than liveness.

**The TLS certificate is self-signed.** Browsers will refuse it, which is the
correct behaviour: this proves TLS terminates at the ingress, not that anything
stands behind it. A certificate anyone accepts needs a domain name and
cert-manager. And without the Secret the controller serves plain HTTP while
`kubectl get ingress` still shows the rule, so the downgrade is invisible from
the cluster side.

## Verified, and what was not

`helm lint` passes. `helm template` renders 21 documents by default and 22 with
`secrets.create: true`, and a structural check of every rendered document found
no problems: each Deployment's selector matches its pod labels and carries a
readiness probe, a liveness probe, resources and an image; the StatefulSet's
`volumeClaimTemplates` sits at the StatefulSet spec level rather than the pod
level, and declares a claim named `data` that the pod does not shadow; every
Service has a selector; the retention policy is present whenever persistence is.

The override semantics were tested by rendering with a sixth service added, and
both persistence paths were rendered and compared. Three failures were caught by
rendering rather than by reading: `merge` refusing key/value arguments, a
duplicate `annotations` key that would have dropped the nginx body-size limit
while the chart still looked correct, and `volumeClaimTemplates` indented under
the pod.

`helm template` was validated with Helm 3.16.3, downloaded for the purpose since
it was not installed.

**Nothing was installed.** There is no cluster reachable from the machine this was
written on, so nothing has been shown to schedule, start or serve. The two
missing pieces on a real cluster are a StorageClass, without which MySQL's claim
stays `Pending`, and the two Secrets, without which every pod stays `Pending`.
