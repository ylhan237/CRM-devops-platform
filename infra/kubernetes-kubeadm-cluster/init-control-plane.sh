#!/usr/bin/env bash
#
# Bring up the control plane of the CRM cluster on this machine.
#
#   sudo ./init-control-plane.sh
#
# Assumes the machine came from infra/terraform-azure-vms-and-acr, so it already has
# Docker, containerd, kubelet, kubeadm and kubectl, held by apt-mark.
#
# Deliberately refuses to continue on a machine that is not ready, rather than
# producing a cluster that is half up. A kubeadm init that fails two thirds of the way
# through leaves a state that has to be torn down by hand, and the most common cause
# is one of the checks below.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="${SCRIPT_DIR}/kubeadm-config.yaml"
KUBELET_CONFIG="${SCRIPT_DIR}/kubelet-config.yaml"

# The addresses. Read from the interface rather than passed in, because a value typed
# at the prompt is a value that gets typed wrong.
PRIVATE_IP="$(ip -4 -o route get 1.1.1.1 | awk '{print $7; exit}')"
PUBLIC_IP="${CRM_PUBLIC_IP:-}"
KUBERNETES_VERSION="v1.31.14"

log()  { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[avertissement] %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m[echec] %s\033[0m\n' "$*" >&2; exit 1; }

# ------------------------------------------------------------------ preflight ----

[ "$(id -u)" -eq 0 ] || die "à lancer en root : sudo ./init-control-plane.sh"

log "Verification des prerequis"
command -v kubeadm  >/dev/null || die "kubeadm absent. L'hote vient-il de l'infra Terraform ?"
command -v kubectl  >/dev/null || die "kubectl absent."
command -v docker  >/dev/null || die "docker absent. L'hote vient-il de l'infra Terraform ?"

installed="$(kubeadm version -o short 2>/dev/null || echo unknown)"
log "kubeadm installe : ${installed}"
case "${installed}" in
  "${KUBERNETES_VERSION}") : ;;
  *) warn "version installee differente de ${KUBERNETES_VERSION}. kubeadm exigera peut-etoire une version identique sur tous les noeuds." ;;
esac

# containerd must be using the systemd cgroup driver, or every pod fails with an
# error that names the runtime and blames memory. Checked here rather than discovered
# during rollout.
log "Verification du driver de cgroup de containerd"
containerd_config="/etc/containerd/config.toml"
if [ ! -f "${containerd_config}" ]; then
  die "${containerd_config} absent : containerd n'est pas installe comme l'attend kubeadm."
fi
if ! grep -q 'SystemdCgroup = true' "${containerd_config}"; then
  die "containerd n'est pas en SystemdCgroup=true.
  Le kubelet est configure en systemd, donc sans cela chaque pod echoue avec
  'failed to create containerd task' ou 'OOMKilled', ce qui blames la memoire a tort.
  Corriger :
    sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' ${containerd_config}
    systemctl restart containerd"
fi
log "containerd utilise bien SystemdCgroup"

# Swap. The kubelet refuses to start with it on, and the failure appears minutes later
# with a message about the kubelet rather than about swap.
log "Verification du swap"
if swapon --show --noheadings | grep -q .; then
  warn "du swap est actif. Le kubelet refuse de demarrer avec, il est donc desactive :"
  swapoff -a
  sed -ri 's/^([^#].*\sswap\s)/#\1/' /etc/fstab
  log "swap desactive et rendu persistant"
else
  log "aucun swap actif"
fi

log "Verification des URL de telechargement"
# Checked before kubeadm init, not after. A 404 on a pinned asset fails at the point of
# use, which is after the control plane is running and halfway through tearing it down
# again. Two of these three versions were wrong when this script was first written:
# local-path-provisioner turned out to be versioned 0.0.x and not 4.x, which 404s, and
# flannel's current release was three minors ahead of the tag that had been assumed.
# A version that does not exist is a silent failure until it is not.
FLANNEL_URL="https://github.com/flannel-io/flannel/releases/download/${FLANNEL_VERSION}/kube-flannel.yml"
STORAGE_URL="https://raw.githubusercontent.com/rancher/local-path-provisioner/${STORAGE_VERSION}/deploy/local-path-storage.yaml"

for url in "${FLANNEL_URL}" "${STORAGE_URL}"; do
  code="$(curl -fsS -o /dev/null -w '%{http_code}' -L --max-time 30 "${url}" 2>/dev/null || echo 000)"
  if [ "${code}" != "200" ]; then
    die "URL introuvable (HTTP ${code}) :
  ${url}
  Le tag epingle n'existe probablement pas. Le corriger, ou prendre le dernier tag :
    gh api repos/rancher/local-path-provisioner/tags --jq '.[].name' | head -3
    gh api repos/flannel-io/flannel/releases --jq '.[].tag_name' | head -3"
  fi
  log "  200 ${url}"
done

log "Verification des adresses"
log "  adresse privee : ${PRIVATE_IP}"
[ -n "${PUBLIC_IP}" ] && log "  adresse publique : ${PUBLIC_IP}" \
                      || warn "  CRM_PUBLIC_IP non definie. Les SAN du certificat n'incluront que l'adresse privee, et le kubeconfig copie ailleurs echouera en x509."

# The overlap that does not fail loudly. Checked before kubeadm, because after kubeadm
# the only remedy is to rebuild the cluster.
log "Verification des plages reseau"
VNET_CIDR="10.20.0.0/16"       # ce que Terraform cree
POD_CIDR="10.244.0.0/16"
SVC_CIDR="10.96.0.0/12"
# From lib-cidr.sh, so test/overlap-test.sh exercises this code and not a copy of it.
# shellcheck source=lib-cidr.sh
source "${SCRIPT_DIR}/lib-cidr.sh"

for pair in "${POD_CIDR}:podSubnet" "${SVC_CIDR}:serviceSubnet"; do
  cidr="${pair%%:*}"; key="${pair##*:}"
  if overlaps "${VNET_CIDR}" "${cidr}"; then
    die "le VNet ${VNET_CIDR} chevauche ${key} (${cidr}). Les pods recevraient des adresses que le routeur du VNet distribue aussi, et le trafic entre pods sortirait de la machine pour revenir au mauvais endroit. Corriger networking.${key} dans kubeadm-config.yaml AVANT kubeadm init."
  fi
done
if overlaps "${POD_CIDR}" "${SVC_CIDR}"; then
  die "podSubnet ${POD_CIDR} et serviceSubnet ${SVC_CIDR} se chevauchent."
fi
log "  VNet        : ${VNET_CIDR}"
log "  pods        : ${POD_CIDR}"
log "  services    : ${SVC_CIDR}"
log "aucun chevauchement"

# ------------------------------------------------------------------ configure ----

log "Substitution des adresses dans la configuration"
: "${PUBLIC_IP:=${PRIVATE_IP}}"
cp "${CONFIG}" /tmp/kubeadm-config.yaml
sed -i "s/REPLACE_WITH_PRIVATE_IP/${PRIVATE_IP}/g" /tmp/kubeadm-config.yaml
sed -i "s/REPLACE_WITH_PUBLIC_IP/${PUBLIC_IP}/g"  /tmp/kubeadm-config.yaml
if grep -q REPLACE_WITH /tmp/kubeadm-config.yaml; then
  die "il reste un REPLACE_WITH_ dans la configuration. Les substitutions n'ont pas tous eu lieu."
fi
log "  advertiseAddress : ${PRIVATE_IP}"
log "  certSANs         : ${PRIVATE_IP}, ${PUBLIC_IP}"

log "Arret de kubelet pendant l'init"
# kubeadm initialises the kubelet itself. A running one holding the old configuration
# fights it.
systemctl stop kubelet || true
systemctl stop kubelet.socket 2>/dev/null || true

# --------------------------------------------------------------------- init ----

log "kubeadm init"
kubeadm init \
  --config /tmp/kubeadm-config.yaml \
  --upload-certs \
  --v=5 2>&1 | tee /var/log/kubeadm-init.log

# ------------------------------------------------------------------- kubeconfig ----

log "Installation de la kubeconfig pour root"
mkdir -p /root/.kube
install -o root -g root -m 600 /etc/kubernetes/admin.conf /root/.kube/config
echo "export KUBECONFIG=/etc/kubernetes/admin.conf" > /etc/profile.d/kubeconfig.sh
chmod 0644 /etc/profile.d/kubeconfig.sh

# ------------------------------------------------------------------- kubelet ----

log "Application de la configuration kubelet (cgroupDriver systemd)"
# The ConfigMap holds the kubelet configuration as one JSON string. Assembled with a
# JSON encoder rather than by splicing shell into a string literal: a quote or a
# backslash anywhere in the file, including in a comment, otherwise produces malformed
# JSON that kubectl rejects with a message about the patch and not about the file.
python3 - "${KUBELET_CONFIG}" > /tmp/kubelet-patch.json <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle:
    kubelet = handle.read()
json.dump({"data": {"kubelet": kubelet}}, sys.stdout)
PY
kubectl -n kube-system patch configmap kubelet-config --type merge \
  --patch-file /tmp/kubelet-patch.json

log "Redemarrage du kubelet avec la nouvelle configuration"
systemctl restart kubelet
sleep 5
if ! systemctl is-active --quiet kubelet; then
  die "le kubelet ne redemarre pas. Voir : journalctl -u kubelet -n 50"
fi
log "kubelet actif"

# ------------------------------------------------------------------- control plane pod ----

# A control plane node is tainted NoSchedule on purpose: it is there to run the
# control plane, not the workloads. On a single-machine cluster there is nowhere else
# for the workloads to go, so the taint comes off. On a real cluster it stays, and this
# section is the only thing that should not be copied.
if [ "${SINGLE_NODE:-true}" = "true" ]; then
  log "Retrait du taint du noeud unique (SINGLE_NODE=true)"
  kubectl taint nodes --all node-role.kubernetes.io/control-plane- || true
  kubectl taint nodes --all node-role.kubernetes.io/master- || true
else
  log "Le taint du noeud control-plane est conserve (SINGLE_NODE=false)."
  log "Les workloads devront donc tourner sur des workers ; voir join-worker.sh."
fi

# ------------------------------------------------------------------------ CNI ----

# Without a CNI the nodes stay NotReady and `kubectl get nodes` never goes green. This
# is the single most common "kubeadm looks broken" moment, and it is not broken.
log "Installation du CNI (flannel, le plus proche des valeurs par defaut du projet)"
# v0.28.9 is the current release and its kube-flannel.yml asset is published on the
# release, not on a branch.
FLANNEL_VERSION="v0.28.9"
curl -fsSL "${FLANNEL_URL}" \
  -o /tmp/kube-flannel.yml
kubectl apply -f /tmp/kube-flannel.yml

log "Attente que les noeuds soient Ready (120 s)"
for i in $(seq 1 60); do
  ready="$(kubectl get nodes --no-headers 2>/dev/null | grep -c ' Ready ' || true)"
  if [ "${ready}" -ge 1 ]; then
    log "noeud Ready apres $((i * 2)) s"
    break
  fi
  [ "${i}" -eq 60 ] && die "le noeud n'est pas Ready apres 120 s. Voir : kubectl describe node ; kubectl get pods -A"
  sleep 2
done

# ----------------------------------------------------------------- storage ----

# Without a StorageClass, the MySQL claim in the manifests stays Pending forever. The
# kubeadm documentation names no provisioner, which is deliberate: a storage backend is
# a choice an operator makes, not something a cluster silently defaults to.
log "Installation du provisioner local-path (stockage local, une seule machine)"
# v0.0.37, and the zero matters: local-path-provisioner is versioned 0.0.x and the
# manifests it publishes are not what its Helm chart is for. A v4.x tag does not exist
# and the URL 404s, which is what happened the first time this was written.
STORAGE_VERSION="v0.0.37"
curl -fsSL "${STORAGE_URL}" \
  -o /tmp/local-path-storage.yaml
kubectl apply -f /tmp/local-path-storage.yaml

log "Definition de la StorageClass par defaut"
# Without a default class, every claim without an explicit storageClassName stays
# Pending even though a provisioner is installed. Setting `isDefaultClass` is the step
# that is easy to forget and produces exactly that symptom.
kubectl patch storageclass local-path -p '{"metadata":{"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'

# -------------------------------------------------------------------- verify ----

log "Verification finale"
echo
echo "  noeuds :"
kubectl get nodes -o wide
echo
echo "  systeme :"
kubectl get pods -A
echo
echo "  stockage :"
kubectl get storageclass
echo
echo "  kubelet cgroupDriver :"
kubectl -n kube-system get cm kubelet-config -o jsonpath='{.data.kubelet}' | grep cgroupDriver

ok=0
kubectl get nodes --no-headers 2>/dev/null | grep -q ' Ready ' || { warn "aucun noeud Ready"; ok=1; }
kubectl get storageclass local-path -o jsonpath='{.metadata.annotations.storageclass\.kubernetes\.io/is-default-class}' 2>/dev/null | grep -q true \
  || { warn "local-path n'est pas la StorageClass par defaut"; ok=1; }

if [ "${ok}" -eq 0 ]; then
  cat <<'EOF'

Cluster pret.

  Pour l'utiliser depuis cette machine :
    export KUBECONFIG=/etc/kubernetes/admin.conf
    kubectl get pods -A

  Depuis une autre machine, le kubeconfig contient l'adresse privee et ne joindra pas.
  EnSSH vers la machine, ou recuperer un kubeconfig avec l'adresse publique :

    ssh azureuser@<IP> sudo cat /etc/kubernetes/admin.conf > admin-remote.conf
    # puis remplacer server: https://<IP-PRIVE>:6443 par server: https://<IP-PUBLIQUE>:6443

  Ensuite, l'application :
    kubectl apply -k infra/kubernetes-manifests/
    ou
    helm install crm infra/helm-chart -n crm
EOF
else
  warn "Le cluster est monte mais la verification a signale des points ci-dessus."
fi
