#!/usr/bin/env bash
#
# Tests for the CIDR arithmetic in lib-cidr.sh.
#
#   bash test/overlap-test.sh
#
# Sourced from ../lib-cidr.sh rather than carrying its own copy: a test with its own
# implementation of the thing it tests proves only that the copy is correct.
#
# Case 7 is the one the file exists for. 10.0.0.0/16 and 10.0.1.0/24 really do overlap,
# and the base address of the first is NOT inside the second, so any check phrased as
# "is A's base address in B" passes a configuration that will misroute pod traffic.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib-cidr.sh
source "${SCRIPT_DIR}/../lib-cidr.sh"

fail=0
pass=0

report() { # attendu obtenu cidr description
  if [ "$1" -eq "$2" ]; then
    printf '  ok     %-22s %-22s %s\n' "$3" "" "$4"
    pass=$((pass + 1))
  else
    printf '  ECHEC  %-22s %-22s attendu=%s obtenu=%s  %s\n' "$3" "" "$1" "$2" "$4"
    fail=$((fail + 1))
  fi
}

check_overlap() { # cidrA cidrB attendu(1=chevauche) description
  local got=1
  overlaps "$1" "$2" && got=0
  if [ "${got}" -eq 0 ] && [ "$3" -eq 0 ]; then
    printf '  ok     %-22s %-22s %s\n' "$1" "$2" "$4"; pass=$((pass + 1))
  elif [ "${got}" -eq 1 ] && [ "$3" -eq 1 ]; then
    printf '  ok     %-22s %-22s %s\n' "$1" "$2" "$4"; pass=$((pass + 1))
  else
    printf '  ECHEC  %-22s %-22s attendu=%s obtenu=%s  %s\n' "$1" "$2" "$3" "$got" "$4"
    fail=$((fail + 1))
  fi
}

check_normalised() { # cidr attendu(0=normalise) description
  local got=1
  cidr_is_normalised "$1" && got=0
  report "$2" "${got}" "$1" "$3"
}

echo "Les plages reelles du projet, qui doivent etre disjointes"
check_overlap 10.20.0.0/16  10.244.0.0/16 1 "VNet Terraform contre pods"
check_overlap 10.20.0.0/16  10.96.0.0/12  1 "VNet Terraform contre services"
check_overlap 10.244.0.0/16 10.96.0.0/12  1 "pods contre services"

echo
echo "Chevauchements reels, qui doivent etre detectes"
check_overlap 10.20.0.0/16  10.20.1.0/24  0 "le sous-reseau du VNet est dans le VNet"
check_overlap 10.244.0.0/16 10.244.0.0/24 0 "un sous-reseau de pods dans les pods"
check_overlap 10.96.0.0/12  10.96.0.0/16  0 "un sous-reseau de services dans les services"
check_overlap 10.20.0.0/16  10.0.0.0/8    0 "le VNet entier a l'interieur des pods"
check_overlap 0.0.0.0/0    10.20.0.0/16  0 "la plage par defaut contre tout"

echo
echo "Le cas qui distingue un chevauchement d'une contenance"
check_overlap 10.0.0.0/16  10.0.1.0/24    0 "base addresse hors de l'autre plage, mais ranges qui se croisent"
check_overlap 10.0.1.0/24  10.0.0.0/16    0 "l'ordre des arguments ne change pas le resultat"

echo
echo "Plages disjointes"
check_overlap 10.0.0.0/24   10.1.0.0/24    1 "deux /24 distincts"
check_overlap 192.168.0.0/24 10.0.0.0/8    1 "prive contre public"
check_overlap 172.16.0.0/12  10.244.0.0/16  1 "plage entiere contre les pods"

echo
echo "Normalisation : une CIDR doit commencer a son adresse de base"
check_normalised 10.20.0.0/16  0 "base correcte"
check_normalised 10.244.0.0/16 0 "base correcte"
check_normalised 10.0.1.5/24   1 "base au milieu du bloc, CIDR non normalisee"
check_normalised 192.168.1.1/32 0 "un hote unique avec l'adresse de base"

echo
printf '%d ok, %d echec\n' "${pass}" "${fail}"
[ "${fail}" -eq 0 ] || exit 1
