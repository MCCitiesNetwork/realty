# syntax=docker/dockerfile:1
#
# Builds the explorer and serves it with nginx, fronting the API on the same
# origin. See nginx.conf for why that removes the need for CORS.

# ---- build stage --------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Dependencies first: this layer is reused unless the manifests change, so an
# edit to src/ does not reinstall node_modules.
COPY realty-web/realty-explorer/package.json realty-web/realty-explorer/package-lock.json ./
RUN npm ci

COPY realty-web/realty-explorer/ ./
# The API client is generated from the spec in the sibling module, so that file
# has to be in the build context even though nothing else here reads it.
COPY realty-web/realty-rest/src/main/resources/openapi.yaml ../realty-rest/src/main/resources/openapi.yaml

RUN npm run build

# ---- runtime stage -------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

COPY --from=build /app/dist /usr/share/nginx/html
COPY realty-web/realty-explorer/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
