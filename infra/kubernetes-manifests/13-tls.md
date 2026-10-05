# TLS for the Ingress.
#
#   kubectl -n crm create secret tls crm-tls \
#     --cert=tls.crt --key=tls.key
#
# The Ingress in 10-ingress.yaml references the secret name crm-tls, so this is
# the name it must have.
#
# Generate a self-signed certificate, which is what a lab without a domain name
# can honestly do:
#
#   openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
#     -keyout tls.key -out tls.crt \
#     -subj "/CN=crm.local" \
#     -addext "subjectAltName=DNS:crm.local,DNS:localhost,IP:127.0.0.1"
#
# Add crm.local to /etc/hosts on the machine you browse from:
#
#   echo "127.0.0.1 crm.local" | sudo tee -a /etc/hosts
#
# Browsers will still refuse a self-signed certificate. That is the point worth
# being explicit about: this proves TLS terminates at the ingress, it does not
# prove the identity behind it. A certificate anyone accepts needs a domain name
# and cert-manager:
#
#   cert-manager.io/cluster-issuer: letsencrypt
#
# with the Ingress annotated for the ACME solver, and the self-signed step
# replaced by a Certificate and a ClusterIssuer. That is a change to make when
# there is a name to point at, not before.
#
# tls.crt and tls.key are credentials and are gitignored in this directory.