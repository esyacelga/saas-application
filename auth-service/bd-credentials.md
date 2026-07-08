postgres:
    image: postgres:16-alpine
    container_name: gym-app-saas-db
    environment:
      POSTGRES_DB: "gym-app-saas"
      POSTGRES_USER: "administrador"
      POSTGRES_PASSWORD: "seya1922"