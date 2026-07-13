FROM nginx:1.27-alpine

ENV BACKEND_PROXY_TARGET=http://finance-service:8081

COPY scripts/deploy/default.conf.template /etc/nginx/templates/default.conf.template
COPY apps/web-ele/dist /usr/share/nginx/html/finance

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
