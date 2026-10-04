#!/usr/bin/env bash
# CIDR arithmetic used by init-control-plane.sh.
#
# Sourced, not executed. The functions live here so the test below exercises the code
# that actually runs: a test carrying its own copy of the logic proves only that the
# copy is right.
#
#   source ./lib-cidr.sh

# Overlap, not containment.
#
# Testing whether one network's base address falls inside the other is a common and
# wrong shortcut: 10.0.0.0/16 and 10.0.1.0/24 do overlap, and the base address
# 10.0.0.0 is not inside 10.0.1.0/24, so that check passes a cluster whose pod traffic
# will be misrouted. `overlaps` compares the two address ranges, which is the actual
# question.
#
# Plain bash integer arithmetic, no python3 and no ipcalc. Ubuntu always has python3,
# but a preflight check that fails for a reason unrelated to the cluster is a check
# people learn to skip, and this one runs before anything else does.
_ip2int() {
  local a b c d
  IFS=. read -r a b c d <<< "${1}"
  printf '%s' $(( (a << 24) | (b << 16) | (c << 8) | d ))
}

# cidr -> "debut fin" as integers, the start aligned to the prefix length.
_cidr_bounds() {
  local ip="${1%%/*}" prefix="${1##*/}" mask start
  if [ "${prefix}" -eq 0 ]; then
    mask=0
  else
    mask=$(( (0xFFFFFFFF << (32 - prefix)) & 0xFFFFFFFF ))
  fi
  start=$(( $(_ip2int "${ip}") & mask ))
  printf '%s %s' "${start}" "$(( start | (0xFFFFFFFF - mask) ))"
}

overlaps() { # true when the two CIDR share at least one address
  local -a a b
  read -r a[0] a[1] <<< "$(_cidr_bounds "$1")"
  read -r b[0] b[1] <<< "$(_cidr_bounds "$2")"
  [ "${a[0]}" -le "${b[1]}" ] && [ "${b[0]}" -le "${a[1]}" ]
}

# A network is usable when its base address is inside it. A CIDR whose base address is
# outside its own range, such as 10.0.1.5/24, is accepted by most tools and rejected by
# some; refusing it here is cheaper than debugging it later.
cidr_is_normalised() {
  local -a b
  read -r b[0] b[1] <<< "$(_cidr_bounds "$1")"
  [ "$(_ip2int "${1%%/*}")" -eq "${b[0]}" ]
}
