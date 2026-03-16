#!/bin/sh

set -eu

vault secrets enable transit || true
vault write "transit/keys/${VAULT_TRANSIT_KEY_NAME}" type="rsa-2048" || true
vault read "transit/keys/${VAULT_TRANSIT_KEY_NAME}"
