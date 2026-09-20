# syntax=docker/dockerfile:1

# -----------------------------------------------------------------------------
# Build stage
# -----------------------------------------------------------------------------
FROM node:24.15.0-bookworm AS build

WORKDIR /app

# Leverage Docker layer caching for dependencies
COPY package*.json ./
COPY scripts/set-env.js ./scripts/
RUN npm ci

# Build the application using environment-driven configuration
COPY . .
ARG ENERLYTICS_API_BASE_URL=/api
ARG ENERLYTICS_APP_NAME=Enerlytics
ENV ENERLYTICS_API_BASE_URL=${ENERLYTICS_API_BASE_URL}
ENV ENERLYTICS_APP_NAME=${ENERLYTICS_APP_NAME}
RUN npm run build

# -----------------------------------------------------------------------------
# Runtime stage
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY infrastructure/docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:80 | grep -q "Enerlytics" || exit 1
