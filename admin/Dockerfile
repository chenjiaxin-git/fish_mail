FROM node:22.21.1-bookworm-slim AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM nginx:1.28-alpine
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80 443
