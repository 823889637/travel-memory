FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_AMAP_JS_API_KEY
ARG VITE_AMAP_SECURITY_JS_CODE
ARG VITE_AMAP_SERVICE_HOST
ENV VITE_AMAP_JS_API_KEY=$VITE_AMAP_JS_API_KEY
ENV VITE_AMAP_SECURITY_JS_CODE=$VITE_AMAP_SECURITY_JS_CODE
ENV VITE_AMAP_SERVICE_HOST=$VITE_AMAP_SERVICE_HOST
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
