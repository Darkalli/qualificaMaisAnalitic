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

A API fica em `http://localhost:8080` por padrão. Na IDE, execute `com.QualificaMaisAnaliticApplication` com o processamento de anotações habilitado para Lombok e MapStruct.

O Flyway cria o schema completo com `V1__create_initial_schema.sql` e o Hibernate valida as tabelas. A V1 consolidada exige um banco vazio; não atualiza instalações com as migrações antigas. Veja os [detalhes de migração](docs/guia-tecnico.md#preparar-o-postgresql).

## API

| Recurso | Rota base | Operações |
| --- | --- | --- |
| Pessoas | `/api/person` | Cadastro, atualização, listagem, busca por CPF e exclusão |
| Cursos | `/api/course` | Cadastro, atualização, listagem, busca por nome e exclusão |
| Turmas | `/api/courseClass` | Cadastro, atualização, consulta por curso e exclusão |
| Presenças | `/api/presence` | Registro/atualização por pessoa + aula; consultas por pessoa ou aula/curso |
| Inscrições | `/api/register` | Criação, consulta por CPF ou CPF/curso e exclusão |

POST e PATCH recebem JSON. Criações retornam **201**, consultas e atualizações **200**, e exclusões **204**.

Consulte as [rotas completas e exemplos](docs/guia-tecnico.md#api-http): algumas URLs repetem o recurso, como `/api/person/person/{cpf}`, e os GETs de presença por curso/data e de inscrição por CPF/curso exigem corpo JSON. Endereços não têm endpoints próprios.

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

O projeto está em desenvolvimento. Ainda faltam autenticação, tratamento padronizado de erros da API, validação completa das entradas, regras de conflito de horários e unicidade de presenças. As respostas HTTP usam entidades JPA diretamente.

A criação e a edição de aulas consultam duplicidade por curso/dia, considerando a própria aula e campos omitidos. A V1 também garante curso/dia obrigatórios e únicos no banco, inclusive em gravações simultâneas. Veja os [detalhes da regra de aulas](docs/guia-tecnico.md#uma-aula-por-curso-e-dia).

Veja o [guia técnico](docs/guia-tecnico.md) para estrutura do código, exemplos de requisições, configuração e cobertura dos testes.
