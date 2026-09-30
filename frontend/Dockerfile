# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
FROM node:24.19.0-alpine AS build
WORKDIR /work
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run format:check && npm run lint && npm test && npm run build
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /work/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
CMD ["nginx","-g","daemon off;"]
