FROM node:22.18-bookworm-slim AS builder

ENV NODE_OPTIONS=--max-old-space-size=8192
ENV TZ=Asia/Shanghai
ENV CI=true
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
ENV VITE_BASE=/finance/
ENV VITE_GLOB_CONTEXT_PATH=/finance
ENV VITE_GLOB_API_CONTEXT_PATH=/finance-api
ENV VITE_COMPRESS=none
ENV VITE_PWA=false
ENV VITE_ROUTER_HISTORY=hash
ENV VITE_INJECT_APP_LOADING=true
ENV VITE_ARCHIVER=true
ENV VITE_APP_TITLE=理财投资AI知识库
ENV VITE_APP_NAMESPACE=vben-web-ele

RUN corepack enable && corepack prepare pnpm@11.2.2 --activate

WORKDIR /app

COPY . /app

RUN pnpm install --frozen-lockfile
RUN rm -rf apps/web-ele/dist apps/web-ele/dist.zip \
  && VITE_APP_STORE_SECURE_KEY=please-replace-me-with-your-own-key pnpm -F @vben/web-ele build

FROM nginx:1.27-alpine AS production

ENV BACKEND_PROXY_TARGET=http://finance-service:8081

COPY scripts/deploy/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=builder /app/apps/web-ele/dist /usr/share/nginx/html/finance

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
