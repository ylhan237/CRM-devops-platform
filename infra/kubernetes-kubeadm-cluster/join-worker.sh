#!/usr/bin/env bash
#
# Join a worker node to the CRM cluster.
#
#   ./join-worker.sh <control-plane-ip>
#
# Not needed for the single-machine cluster that init-control-plane.sh sets up by
# default. It exists because the alternative is a lab that cannot grow, and because
# the thing that makes a second node work is a list of things that are easy to forget:
#
#   - containerd must use SystemdCgroup, the same as the control plane
#   - the worker must be able to reach the control plane on 6443, which means the NSG
#     has to allow it between the two machines and not only from your own address
#   - swap off
#   - the token is valid 24 hours and is single use
#
# Every one of those has its own failure message, and none of them says "you forgot
# the token".

set -euo pipefail

log()  { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[avertissement] %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m[echec] %s\033[0m\n' "$*" >&2; exit 1; }

CONTROL_PLANE="${1:-}"
[ -n "${CONTROL_PLANE}" ] || die "usage : $0 <adresse-du-control-plane>"
# A hostname works too, and an address that resolves is friendlier than one that does
# not: it turns "connection refused" into "no such host", which names the cause.
getent hosts "${CONTROL_PLANE}" >/dev/null || die "${CONTROL_PLANE} ne resout pas depuis cette machine. Verifier le DNS ou passer l'adresse privee du control plane."

log "Verification des prerequis"
[ "$(id -u)" -eq 0 ] || die "à lancer en root : sudo $0 ${CONTROL_PLANE}"
command -v kubeadm  >/dev/null || die "kubeadm absent."
command -v docker  >/dev/null || die "docker absent. L'hote vient-il de l'infra Terraform ?"

# The version has to match the control plane exactly. kubeadm refuses a mismatch, and
# the message it gives names the version and not the configuration, which is the part
# that actually needs fixing.
CP_VERSION="$(ssh -o StrictHostKeyChecking=no "azureuser@${CONTROL_PLANE}" \
  'sudo kubeadm version -o short' 2>/dev/null || true)"
LOCAL_VERSION="$(kubeadm version -o short 2>/dev/null || echo unknown)"
log "  control plane : ${CP_VERSION:-inconnu}"
log "  ce noeud      : ${LOCAL_VERSION}"
if [ -n "${CP_VERSION}" ] && [ "${CP_VERSION}" != "${LOCAL_VERSION}" ]; then
  die "versions differentes. Installer la meme version sur ce noeud :
  apt-get install -y kubelet=${CP_VERSION#v} kubeadm=${CP_VERSION#v} kubectl=${CP_VERSION#v}
  apt-mark hold kubelet kubeadm kubectl"
fi

log "Verification du driver de cgroup de containerd"
if [ -f /etc/containerd/config.toml ] && ! grep -q 'SystemdCgroup = true' /etc/containerd/config.toml; then
  die "containerd n'est pas en SystemdCgroup=true. Meme correction que sur le control plane :
  sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
  systemctl restart containerd"
fi

log "Verification du swap"
if swapon --show --noheadings | grep -q .; then
  swapoff -a
  sed -ri 's/^([^#].*\sswap\s)/#\1/' /etc/fstab
  log "swap desactive"
fi

log "Test de la portee du control plane sur 6443"
# Checked before asking for a token. A token is single use and expires in 24 hours, so
# burning one on a connection that cannot work wastes the only copy the operator has.
if ! timeout 5 bash -c "cat < /dev/null > /dev/tcp/${CONTROL_PLANE}/6443" 2>/dev/null; then
  die "${CONTROL_PLANE}:6443 injoignable.
  Deux causes probables :
    - le groupe de securite du control plane n'ouvre 6443 que depuis votre adresse, et
      la machine du worker en est une autre. Ouvrir 6443 entre les deux machines dans
      infra/terraform-azure-vms-and-acr/main.tf (application_ports ne le fait que pour
      l'adresse publique du control plane).
    - le control plane n'ecoute que sur l'adresse privee. C'est voulu : les manifests
      et l'Ingress passent par 6443 uniquement pour l'administration."
fi
log "6443 joignable"

log "Arret du kubelet avant le join"
systemctl stop kubelet || true

log "Demande du token d'ajout"
# Generated on the control plane rather than copied by hand: the CA hash in the command
# is what stops a worker from joining a different cluster, and it is easy to copy wrong.
JOIN_COMMAND="$(ssh -o StrictHostKeyChecking=no "azureuser@${CONTROL_PLANE}" \
  'sudo kubeadm token create --print-join-command')"

[ -n "${JOIN_COMMAND}" ] || die "la generation du token a echoue sur le control plane."

log "kubeadm join"
# shellcheck disable=SC2086
${JOIN_COMMAND}

log "Redemarrage du kubelet"
systemctl restart kubelet

log "Attente que le noeud soit Ready (60 s)"
for i in $(seq 1 30); do
  name="$(hostname)"
  status="$(kubectl get node "${name}" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || true)"
  if [ "${status}" = "True" ]; then
    log "noeud ${name} Ready apres $((i * 2)) s"
    exit 0
  fi
  sleep 2
done

warn "le noeud n'est pas Ready apres 60 s. Diagnostics :"
echo "  kubectl get nodes"
echo "  kubectl describe node \$(hostname) | tail -30"
echo "  journalctl -u kubelet -n 50"
echo
warn "cause la plus frequente sur un VNet : le worker n'est pas sur le bon sous-reseau,"
echo "  ou le routage entre les deux machines n'est pas ouvert. Un noeud qui rejoint et"
echo "  reste NotReady sur un routage casse donne exactement ces symptomes."
exit 1
