#!/usr/bin/env bash
#
# Creates a local HTTPS certificate so a phone on the same Wi-Fi can use calls.
#
#   bash scripts/make-dev-cert.sh [lan-ip]      (defaults to 192.168.1.3)
#
# Output in ./certificates (git-ignored):
#   dev-ca.pem            - the local certificate authority. Install THIS on the phone.
#   dev-cert.pem/.key     - the server certificate `npm run dev:https` serves.
#
# The CA is reused on later runs, so after an IP change you only re-run this script; the
# phone does not need the CA installed again.
set -euo pipefail

IP="${1:-192.168.1.3}"
DIR="$(cd "$(dirname "$0")/.." && pwd)/certificates"
mkdir -p "$DIR"
cd "$DIR"

# Git Bash would otherwise rewrite "/CN=..." into a Windows path.
export MSYS_NO_PATHCONV=1

if [[ ! -f dev-ca.pem || ! -f dev-ca.key ]]; then
  # Extensions come from a file rather than `req -x509 -addext`: OpenSSL 1.1's default
  # config already adds basicConstraints, and a duplicate makes the CA invalid.
  cat > dev-ca.ext <<EOF
basicConstraints=critical,CA:TRUE
keyUsage=critical,keyCertSign,cRLSign
subjectKeyIdentifier=hash
EOF
  openssl req -new -nodes -newkey rsa:2048 -sha256 \
    -keyout dev-ca.key -out dev-ca.csr -subj "/CN=Two and Two Dev CA"
  openssl x509 -req -in dev-ca.csr -signkey dev-ca.key -out dev-ca.pem \
    -days 825 -sha256 -extfile dev-ca.ext
  rm -f dev-ca.csr dev-ca.ext
fi

cat > dev-cert.ext <<EOF
authorityKeyIdentifier=keyid
subjectKeyIdentifier=hash
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:localhost,IP:127.0.0.1,IP:${IP}
EOF

openssl req -new -nodes -newkey rsa:2048 -sha256 \
  -keyout dev-key.pem -out dev-cert.csr -subj "/CN=${IP}"
# iOS rejects server certificates valid for more than 825 days.
openssl x509 -req -in dev-cert.csr -CA dev-ca.pem -CAkey dev-ca.key -CAcreateserial \
  -out dev-cert.pem -days 825 -sha256 -extfile dev-cert.ext
rm -f dev-cert.csr dev-cert.ext

# Served by the dev server so the phone can download it straight from Safari.
cp dev-ca.pem "$DIR/../public/dev-ca.crt"

echo
echo "Certificate for localhost and ${IP} written to ${DIR}"
echo "On the phone, open https://${IP}:3000/dev-ca.crt and install it (see README: Testing on a phone)."
