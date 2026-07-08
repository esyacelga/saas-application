# ── Build stage ───────────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# VITE_* vars are baked into the JS bundle at build time
ARG VITE_AUTH_API_URL=http://localhost:8080/api/v1
ARG VITE_CORE_API_URL=http://localhost:8083/api/v1
ARG VITE_ATTENDANCE_API_URL=http://localhost:8084/api/v1
ARG VITE_GOOGLE_CLIENT_ID=
ARG VITE_FACEBOOK_APP_ID=

ENV VITE_AUTH_API_URL=$VITE_AUTH_API_URL \
    VITE_CORE_API_URL=$VITE_CORE_API_URL \
    VITE_ATTENDANCE_API_URL=$VITE_ATTENDANCE_API_URL \
    VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID \
    VITE_FACEBOOK_APP_ID=$VITE_FACEBOOK_APP_ID

RUN npx vite build

# ── Serve stage ────────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine

COPY --from=builder /app/dist /usr/share/nginx/html

# SPA fallback: all routes serve index.html
RUN printf 'server {\n\
    listen 80;\n\
    root /usr/share/nginx/html;\n\
    index index.html;\n\
    location / {\n\
        try_files $uri $uri/ /index.html;\n\
    }\n\
}\n' > /etc/nginx/conf.d/default.conf

EXPOSE 80
