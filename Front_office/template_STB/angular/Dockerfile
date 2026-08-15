# --- Étape 1 : build ---
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* yarn.lock* ./
RUN npm ci

COPY . .
# Ne pas utiliser le script npm "build-prod" : il fixe --base-href sur le chemin de démo du
# thème d'origine (/demos/admin-templates/...), invalide une fois l'app servie à la racine.
RUN npx ng build --configuration production

# --- Étape 2 : image d'exécution (Nginx) ---
FROM nginx:1.27-alpine

# Traité par docker-entrypoint.d/20-envsubst-on-templates.sh au démarrage du conteneur :
# BACKEND_HOST / BACKEND_PORT sont injectés dans /etc/nginx/conf.d/default.conf
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
ENV BACKEND_HOST=backend \
    BACKEND_PORT=8080

# angular.json -> outputPath.browser = "" : le build est déposé directement dans dist/
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
