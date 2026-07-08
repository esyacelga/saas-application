# ── Build stage ───────────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# VITE_* vars are baked into the JS bundle at build time
ARG VITE_API_AUTH_URL=http://localhost:8080/api/v1
ARG VITE_API_PLATFORM_URL=http://localhost:8081/api/v1
ARG VITE_API_CORE_URL=http://localhost:8083/api/v1
ARG VITE_API_ATTENDANCE_URL=http://localhost:8084/api/v1
ARG VITE_APP_NAME=GymAdmin
ARG VITE_CLIENT_APP_URL=http://localhost:5174
ARG VITE_API_BASE_URL=${VITE_API_AUTH_URL}
ARG VITE_AVATAR_HOMBRE_URL=https://res.cloudinary.com/dmlco6mio/image/upload/v1781449426/hombre_avatar_ej8fbn.png
ARG VITE_AVATAR_MUJER_URL=https://res.cloudinary.com/dmlco6mio/image/upload/v1781449480/mujer_avatar_02_pkgg19.png
ARG VITE_AVATAR_LOGO_COMPANY=

ENV VITE_API_AUTH_URL=$VITE_API_AUTH_URL \
    VITE_API_PLATFORM_URL=$VITE_API_PLATFORM_URL \
    VITE_API_CORE_URL=$VITE_API_CORE_URL \
    VITE_API_ATTENDANCE_URL=$VITE_API_ATTENDANCE_URL \
    VITE_APP_NAME=$VITE_APP_NAME \
    VITE_CLIENT_APP_URL=$VITE_CLIENT_APP_URL \
    VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_AVATAR_HOMBRE_URL=$VITE_AVATAR_HOMBRE_URL \
    VITE_AVATAR_MUJER_URL=$VITE_AVATAR_MUJER_URL \
    VITE_AVATAR_LOGO_COMPANY=$VITE_AVATAR_LOGO_COMPANY

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
