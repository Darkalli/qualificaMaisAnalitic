# Qualifica Mais Analitic

![Status](https://img.shields.io/badge/Status-Work%20In%20Progress-orange?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Google Sheets](https://img.shields.io/badge/Google%20Sheets-34A853?style=for-the-badge&logo=googlesheets&logoColor=white)

Backend para gerenciar pessoas, cursos, turmas, inscrições e presenças. Oferece uma API HTTP e importa inscrições do Google Sheets para o PostgreSQL, preservando os dados já cadastrados no sistema.

## Como executar

**Requisitos:** Java 25 e PostgreSQL. O projeto inclui o Maven Wrapper.

1. Crie um banco PostgreSQL vazio.
2. Na primeira configuração, copie o modelo abaixo. Preserve seu arquivo local se ele já existir.

   ```powershell
   Copy-Item application.properties.example src/main/resources/application.properties
   ```

3. Preencha `spring.datasource.url`, `spring.datasource.username` e `spring.datasource.password`. Para executar somente a API, defina `app.sheets.check-enabled=false`.
4. Inicie a aplicação:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

A API fica em `http://localhost:8080` por padrão. Na IDE, execute `com.QualificaMaisAnaliticApplication` com o processamento de anotações habilitado para Lombok e MapStruct. Para criar o primeiro usuário, configure `AUTH_BOOTSTRAP_USERNAME` e `AUTH_BOOTSTRAP_PASSWORD` no ambiente da execução antes de iniciar; remova essas variáveis após o cadastro inicial. Veja o [fluxo de login](docs/guia-tecnico.md#login-e-renovação-de-sessão).

O Flyway aplica V1–V4: estrutura inicial, campos obrigatórios, horários/status de aulas e usuários/sessões de acesso. A V3 usa horários sem data e exige início anterior ao fim. A V1 consolidada não converte instalações com o histórico antigo. Veja os [detalhes de migração](docs/guia-tecnico.md#preparar-o-postgresql).

## API

| Recurso | Rota base | Operações |
| --- | --- | --- |
| Pessoas | `/api/person` | Cadastro, atualização, listagem, busca por CPF e exclusão |
| Cursos | `/api/course` | Cadastro, atualização, listagem, busca por nome e exclusão |
| Turmas | `/api/courseClass` | Cadastro, atualização/status, consulta por curso e cancelamento |
| Presenças | `/api/presence` | Registro/atualização por pessoa + aula; consultas por pessoa ou aula/curso |
| Inscrições | `/api/register` | Criação, consulta por CPF ou CPF/curso e exclusão |
| Autenticação | `/api/auth` | Login, renovação, logout, usuário atual e cadastro de usuários de acesso |

POST e PATCH recebem JSON. Criações retornam **201**, consultas e atualizações **200**, e exclusões **204**.

As rotas de negócio exigem `Authorization: Bearer <token>`. Faça login em `POST /api/auth/login` com `username` e `password`. O token dura 15 dias; `POST /api/auth/refresh` valida o token atual, emite outro por mais 15 dias e invalida o anterior. `ADMIN` e `AGENT` têm as mesmas permissões. Swagger público em `/swagger-ui/index.html`, com botão **Authorize**.

Erros retornam JSON com `status` e `message`: **400** para dados inválidos, **401** para login/token ausente, inválido ou expirado, **403** para acesso negado, **404** para registro não encontrado, **409** para conflitos e **500** para falhas inesperadas. Detalhes no [guia de respostas](docs/guia-tecnico.md#respostas-e-limites-atuais).

Consulte as [rotas completas e exemplos](docs/guia-tecnico.md#api-http): algumas URLs repetem o recurso, como `/api/person/person/{cpf}`, e os GETs de presença por curso/aula e de inscrição por CPF/curso exigem corpo JSON. Endereços não têm endpoints próprios.

## Importação do Google Sheets

1. Importe o [modelo de planilha](docs/modelo-cadastros.csv), usando `;` como separador.
2. Cadastre os cursos no sistema e preencha a coluna **ID do curso** com o ID correspondente.
3. Habilite a API Google Sheets e salve as credenciais OAuth de aplicativo desktop em `src/main/resources/credentials.json`.
4. Configure `app.sheets.spreadsheet-id` e `app.sheets.range` no arquivo local. O intervalo deve começar no cabeçalho, por exemplo `'Cadastros'!A1:Z`.
5. Execute `com.SheetsQuickstart` pela IDE e autorize o acesso no navegador. Essa execução apenas lê e valida, sem gravar no banco.

Para importar automaticamente, configure e reinicie a aplicação:

```properties
app.sheets.check-enabled=true
app.sheets.check-interval=5m
app.sheets.check-initial-delay=10s
```

**Regras principais:**

- Uma pessoa por CPF; uma inscrição por pessoa e ID do curso.
- Reimportações idênticas não duplicam cadastros.
- Alterações na planilha geram divergências; os dados salvos no sistema são preservados.
- Linhas inválidas são relatadas e descartadas. Curso inexistente ou falha de persistência reverte todo o lote de linhas válidas.
- A importação não cria cursos nem exclui cadastros quando uma linha é removida.

Os [formatos das colunas e opções de coleta](docs/guia-tecnico.md#cabeçalho-da-planilha) estão no guia técnico. Configurações privadas, credenciais e `tokens/` são ignorados pelo Git.

## Testes

```powershell
.\mvnw.cmd test
```

A suíte usa H2 em memória, Flyway e MockMvc, sem acessar o Google ou o banco de trabalho. Cobre serviços, API, migrações, importação, duplicidade e rollback.

Para executar apenas os testes da API:

```powershell
.\mvnw.cmd test "-Dtest=ApiControllerTests"
```

## Estado atual

Inscrição, importação e alteração de aulas verificam conflitos com bloqueios transacionais. Lotes bloqueiam cursos e pessoas em ordem fixa. Presença exige inscrição e aula ativa. Cancelamento preserva o histórico; aulas canceladas/adiadas não ocupam horário para inscrições. Erros da API têm tratamento centralizado. Campos obrigatórios, IDs positivos, e-mail, limites de texto e intervalo de datas são validados nos serviços. PATCH de endereço preserva campos omitidos e o vínculo atual. Autenticação com token implementada; restrições diferentes por perfil ficam para depois. As respostas de negócio usam entidades JPA diretamente; autenticação retorna DTOs sem senha/hash.

A criação e a edição de aulas consultam duplicidade por curso/dia, considerando a própria aula e campos omitidos. A V1 também garante curso/dia obrigatórios e únicos no banco, inclusive em gravações simultâneas. Veja os [detalhes da regra de aulas](docs/guia-tecnico.md#uma-aula-por-curso-e-dia).

Veja o [guia técnico](docs/guia-tecnico.md) para estrutura do código, exemplos de requisições, configuração e cobertura dos testes.
