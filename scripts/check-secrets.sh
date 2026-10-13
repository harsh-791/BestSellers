#!/usr/bin/env bash
set -euo pipefail

if git grep -E -I -n 'AKIA[0-9A-Z]{16}|BEGIN (RSA |OPENSSH )?PRIVATE KEY' -- .; then
  echo "secret-like content found" >&2
  exit 1
fi

echo "no secret-like content found"
