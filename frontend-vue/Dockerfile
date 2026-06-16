FROM node:22.18-bookworm-slim

WORKDIR /app

ENV CI=true
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0

RUN corepack enable && corepack prepare pnpm@11.2.2 --activate

COPY . .

RUN pnpm install --frozen-lockfile

EXPOSE 5777

CMD ["pnpm", "dev:ele", "--host", "0.0.0.0"]
