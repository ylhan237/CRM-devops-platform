# Stage 1: Build Angular application
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build -- --configuration production

# Stage 2: Serve with Nginx
FROM nginx:alpine

# The nginx image renders /etc/nginx/templates/*.template into
# /etc/nginx/conf.d/*.conf at startup, substituting the ${...} placeholders with
# environment variables. That indirection is what makes the upstream resolve
# lazily instead of at configuration load time.
COPY --from=build /app/dist/crm/browser /usr/share/nginx/html
COPY templates/default.conf.template /etc/nginx/templates/default.conf.template

# Docker's embedded DNS. Only present on a user-defined network, which is why it
# is overridable: under Kubernetes this must be the CoreDNS Service IP, read
# from /etc/resolv.conf.
ENV NGINX_RESOLVER=127.0.0.11

# Service name of the API gateway, resolved through DNS on every request rather
# than pinned to an address at startup.
ENV API_GATEWAY_HOST=api-gateway
ENV API_GATEWAY_PORT=8060

EXPOSE 80

# Reports on nginx itself, not on the gateway, so the container is marked
# healthy even while the API gateway is briefly unavailable.
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]