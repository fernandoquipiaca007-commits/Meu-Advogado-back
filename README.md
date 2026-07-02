# Upwork Clone — Ambiente Local

Monorepo com o backend Spring Boot e o frontend React do projeto [activecourses/upwork-clone-backend](https://github.com/activecourses/upwork-clone-backend) e [activecourses/upwork-clone-frontend](https://github.com/activecourses/upwork-clone-frontend).

## Estrutura

```
upwork-clone/
├── backend/          # Spring Boot 3.3 + Gradle + PostgreSQL
├── frontend/         # React 18 + Vite + MUI
├── tools/            # JDK 21 e PostgreSQL portáteis (Windows)
├── scripts/          # Scripts de inicialização
├── docker-compose.yml
└── RELATORIO_TECNICO.md
```

## Status atual (ambiente configurado)

| Serviço      | URL                              | Status   |
|-------------|-----------------------------------|----------|
| Frontend    | http://127.0.0.1:5173             | Rodando  |
| Backend API | http://localhost:8080             | Rodando  |
| Swagger UI  | http://localhost:8080/swagger-ui/index.html | OK |
| PostgreSQL  | localhost:5432                    | Rodando  |

Bancos criados: `upwork` (padrão do projeto) e `upwork_clone`.

## Pré-requisitos

- **Windows** (configuração testada neste ambiente)
- **Node.js** 18+ e npm
- **Java 21** — incluído em `tools/jdk-21.0.11+10` ou instalação global
- **PostgreSQL 16** — incluído em `tools/pgsql` ou instalação global/Docker

> Docker Desktop não estava instalado nesta máquina. O ambiente foi levantado com JDK e PostgreSQL portáteis. Use `docker-compose.yml` quando Docker estiver disponível.

## Início rápido (Windows, sem Docker)

### 1. PostgreSQL

```powershell
.\scripts\start-postgres.ps1
```

### 2. Backend

```powershell
.\scripts\start-backend.ps1
```

Aguarde até `http://localhost:8080/api/test/all` retornar `Public Content.`

### 3. Frontend

```powershell
.\scripts\start-frontend.ps1
```

Acesse http://127.0.0.1:5173

## Início com Docker (quando Docker estiver instalado)

```powershell
cd upwork-clone
docker compose up --build
```

- PostgreSQL: porta `5432`
- Backend: porta `8080`
- Frontend: porta `5173`

## Configuração do banco

Arquivo: `backend/src/main/resources/env.properties`

```properties
POSTGRES_USER=postgres
POSTGRES_PASSWORD=root
POSTGRES_DB=upwork
```

Migrations Flyway em `backend/src/main/resources/db/migration/` são aplicadas automaticamente na subida do backend.

## Teste rápido da API

```powershell
# Endpoint público
Invoke-WebRequest http://localhost:8080/api/test/all

# Cadastro
$body = '{"firstName":"Joao","lastName":"Silva","email":"joao@test.com","password":"password123","roles":["ROLE_CLIENT"]}'
Invoke-RestMethod -Uri http://localhost:8080/api/auth/register -Method POST -Body $body -ContentType application/json

# Login (cookies JWT HttpOnly)
Invoke-WebRequest -Uri http://localhost:8080/api/auth/login -Method POST -Body '{"email":"joao@test.com","password":"password123"}' -ContentType application/json -SessionVariable s
```

## Observações importantes

1. O **frontend** hoje é majoritariamente UI (login/cadastro sem integração completa com a API).
2. O **backend** tem schema completo (proposals, payments, contracts), mas vários módulos ainda não têm controllers.
3. Usuários seed (V4) existem no banco, mas a senha plain-text não está documentada no repositório.
4. Proxy do Vite configurado em `frontend/vite.config.js` para `/api` → `http://localhost:8080`.

## Documentação completa

Consulte [RELATORIO_TECNICO.md](./RELATORIO_TECNICO.md) para endpoints, modelo de dados, fluxos, problemas encontrados e mapeamento para plataforma jurídica.

## Licença

MIT (repositórios originais activecourses).
