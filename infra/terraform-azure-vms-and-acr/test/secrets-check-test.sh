#!/usr/bin/env bash
#
# Tests for the secret presence check in .github/workflows/terraform.yml.
#
#   bash test/secrets-check-test.sh
#
# Extracted from the workflow's "Check the Azure credentials are present" step, because
# that step is the one place where a silent pass would be expensive: a check that
# reports success when a secret is absent means the credentials stay unverified and
# the first person to find out is the one running apply against a real subscription.
#
# This is exactly the gap that let AZURE_REGISTRY and its two siblings go missing while
# the repository looked correctly configured.

set -uo pipefail

pass=0
fail=0

# The body of the step, with the echo of values removed. Values are never printed in
# the real step either, and a test that printed them would teach someone to relax that.
run_check() {
  # shellcheck disable=SC2317
  (
    set -uo pipefail
    missing=()
    for name in ARM_SUBSCRIPTION_ID ARM_TENANT_ID ARM_CLIENT_ID ARM_CLIENT_SECRET; do
      value="${!name}"
      if [ -z "${value}" ]; then
        missing+=("${name}")
      fi
    done
    if [ ${#missing[@]} -gt 0 ]; then
      echo "FAIL:${missing[*]}"
      exit 1
    fi
    if ! printf '%s' "${ARM_SUBSCRIPTION_ID}" | grep -Eq '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'; then
      echo "FAIL:not-a-uuid:${#ARM_SUBSCRIPTION_ID}"
      exit 1
    fi
    echo "OK"
    exit 0
  ) 2>/dev/null
}

expect() { # attendu_obtenu description
  if [ "$1" = "$2" ]; then
    printf '  ok     %s\n' "$3"
    pass=$((pass + 1))
  else
    printf '  ECHEC  %s\n        attendu: %s\n        obtenu : %s\n' "$3" "$1" "$2"
    fail=$((fail + 1))
  fi
}

UUID="12345678-1234-1234-1234-123456789abc"

echo "Tous les secrets presents et valides"
got="$(ARM_SUBSCRIPTION_ID="$UUID" ARM_TENANT_ID="tenant.onmicrosoft.com" \
       ARM_CLIENT_ID="client" ARM_CLIENT_SECRET="secret" run_check)"
expect "OK" "${got}" "un abonnement UUID et trois secrets non vides passent"

echo
echo "Un secret absent est nomme, pas seulement compte"
got="$(ARM_SUBSCRIPTION_ID="$UUID" ARM_TENANT_ID="t" \
       ARM_CLIENT_ID="client" ARM_CLIENT_SECRET="" run_check)"
expect "FAIL:ARM_CLIENT_SECRET" "${got}" "ARM_CLIENT_SECRET vide est signale par son nom"

got="$(ARM_SUBSCRIPTION_ID="" ARM_TENANT_ID="t" \
       ARM_CLIENT_ID="c" ARM_CLIENT_SECRET="s" run_check)"
expect "FAIL:ARM_SUBSCRIPTION_ID" "${got}" "ARM_SUBSCRIPTION_ID vide est signale"

echo
echo "Plusieurs secrets absents sont tous listes"
got="$(ARM_SUBSCRIPTION_ID="" ARM_TENANT_ID="" \
       ARM_CLIENT_ID="" ARM_CLIENT_SECRET="" run_check)"
expect "FAIL:ARM_SUBSCRIPTION_ID ARM_TENANT_ID ARM_CLIENT_ID ARM_CLIENT_SECRET" "${got}" \
  "les quatre absents sont listes dans l'ordre, ce qui permet de tout corriger d'un coup"

echo
echo "Un secret present mais vide est traite comme absent"
got="$(ARM_SUBSCRIPTION_ID="$UUID" ARM_TENANT_ID="t" \
       ARM_CLIENT_ID=" " ARM_CLIENT_SECRET="s" run_check)"
# A space is not empty as far as -z is concerned, so this PASSES the presence check.
# That is the workflow's real behaviour, and the test records it rather than asserting
# something the workflow does not do.
expect "OK" "${got}" "une espace passe la presence : le controle est -z, pas un trim"

echo
echo "Un abonnement qui n'est pas un UUID est refuse avant d'appeler Azure"
got="$(ARM_SUBSCRIPTION_ID="my-subscription" ARM_TENANT_ID="t" \
       ARM_CLIENT_ID="c" ARM_CLIENT_SECRET="s" run_check)"
expect "FAIL:not-a-uuid:15" "${got}" "un nom au lieu d'un UUID echoue avec sa longueur"

got="$(ARM_SUBSCRIPTION_ID="0000" ARM_TENANT_ID="t" ARM_CLIENT_ID="c" \
       ARM_CLIENT_SECRET="s" run_check)"
expect "FAIL:not-a-uuid:4" "${got}" "un identifiant trop court echoue"

got="$(ARM_SUBSCRIPTION_ID="${UUID} " ARM_TENANT_ID="t" ARM_CLIENT_ID="c" \
       ARM_CLIENT_SECRET="s" run_check)"
expect "FAIL:not-a-uuid:37" "${got}" "une espace finale fait echouer le UUID, ce qui est le comportement voulu"

echo
printf '%d ok, %d echec\n' "${pass}" "${fail}"
[ "${fail}" -eq 0 ] || exit 1
