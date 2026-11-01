#!/usr/bin/env bash
# Removes the local cluster and the tunnel, to start over with up.sh.
# Touches only the community profile: .env, other Minikube profiles and the deploy branch stay.
# To pause and keep the data use: minikube stop -p community
set -euo pipefail

PROFILE=community
NGROK_CONTAINER=community-ngrok

if [ "${1:-}" != "--yes" ]; then
  read -r -p "Delete cluster '$PROFILE' with its database? [y/N] " answer
  case "$answer" in
    y | Y) ;;
    *)
      echo "Cancelled"
      exit 0
      ;;
  esac
fi

docker rm -f "$NGROK_CONTAINER" > /dev/null 2>&1 || true
minikube delete -p "$PROFILE"
