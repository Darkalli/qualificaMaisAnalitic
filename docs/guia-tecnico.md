# Guia técnico — Qualifica Mais Analitic

[Voltar ao README](../README.md)

Referência de configuração, API, importação e testes.

## Estrutura

- `com.controlers`: controllers REST de pessoa, curso, turma, presença e inscrição. Somente `AddressController` ainda não possui rotas.
- `com.services`: operações de cadastro, atualização, consulta e exclusão usadas pelos controllers, incluindo `RegisterService` para inscrição de pessoas em cursos existentes.
- `com.dtos` e `com.mappers`: entradas dos serviços e atualização parcial com MapStruct.
- `GoogleSheetsReader`: autentica com OAuth e lê a planilha com acesso somente de leitura.
- `RegisterSheetMapper`: identifica as colunas pelo cabeçalho e converte cada linha em `Register`, incluindo `Person` e seu `Address`.
- `RegisterCollectionService.collect()`: coordena a leitura e retorna `SheetImportResult`.
- `RegisterImportService.importRegisters()`: coleta a planilha e encaminha as linhas válidas para persistência.
- `RegisterPersistenceService`: grava o lote em uma transação, reutiliza a pessoa por CPF e resolve o curso pelo ID do catálogo.
- `PersonRepository`: consulta pessoas por CPF normalizado.
- `RegisterRepository`: consulta inscrições por ID ou pelo CPF da pessoa e ID do curso.
- `RegisterCollectionScheduler`: executa a importação periódica e registra o resumo, as divergências e os erros no log.
- `SheetImportResult`: contém `registers` válidos, `errors` com o número da linha e a primeira falha encontrada nela, e `ignoredRows` para linhas vazias.
- `SheetsProperties`: recebe a configuração local da coleta, compartilhada pelo Spring e pelo Quickstart.
- `SheetsQuickstart`: permite executar a coleta pela IDE sem iniciar o Spring/PostgreSQL.

A chamada `collect()` e o Quickstart continuam retornando objetos em memória, sem gravar. A chamada `importRegisters()` e o agendamento gravam no banco. Os IDs de `Register`, `Person` e `Address` são gerados pelo banco ao inserir.

## API HTTP

### Login e renovação de sessão

`User` é o usuário de acesso, separado de `Person` (cadastro de alunos). Possui `username`, senha com hash BCrypt e perfil `AGENT` ou `ADMIN`. Os dois perfis têm exatamente as mesmas permissões, inclusive cadastrar outros usuários. O Spring recebe `ROLE_AGENT`/`ROLE_ADMIN`; restrições futuras podem ser acrescentadas no `SecurityConfig` ou com `@PreAuthorize`, sem alterar a estrutura de usuários.

O primeiro usuário é criado na inicialização somente se `app_user` estiver vazia e `AUTH_BOOTSTRAP_USERNAME`/`AUTH_BOOTSTRAP_PASSWORD` estiverem configurados no ambiente. Também é possível preencher `app.auth.bootstrap.username` e `app.auth.bootstrap.password` no arquivo local ignorado pelo Git. Remova essas configurações após o primeiro cadastro. Não há senha padrão nem cadastro público; reiniciar a aplicação não redefine usuários existentes. O perfil inicial é `ADMIN`, com as mesmas permissões de `AGENT`.

Nome de usuário: 3–64 letras/números/ponto/hífen/sublinhado, normalizado para minúsculas e sem espaços nas extremidades. Senha: mínimo de 8 caracteres e máximo de 72 bytes UTF-8, sem normalização. A senha não é retornada pela API.

| Método | Rota | Autenticação e resultado |
| --- | --- | --- |
| POST | `/api/auth/login` | Pública. Recebe `username` e `password`; retorna token, tipo `Bearer`, `expiresAt` UTC e usuário. |
| POST | `/api/auth/refresh` | Bearer atual, sem corpo. Valida a sessão e devolve um token novo com mais 15 dias. O token anterior deixa de funcionar. |
| GET | `/api/auth/me` | Bearer. Retorna `id`, `username` e `role`; não renova o token. |
| POST | `/api/auth/logout` | Bearer, sem corpo. Revoga a sessão atual e retorna 204. Sessões de outros computadores continuam válidas. |
| POST | `/api/auth/register` | Bearer de ADMIN ou AGENT. Recebe `username`, `password` e `role`; retorna usuário sem senha/hash (201). |

Login:

```http
POST /api/auth/login
Content-Type: application/json

{"username":"operador","password":"sua-senha-configurada"}
```

Resposta (valores ilustrativos):

```json
{
  "token": "token-retornado-pela-api",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-17T12:00:00Z",
  "user": {"id": 1, "username": "operador", "role": "ADMIN"}
}
```

Ao ligar o PC e abrir o aplicativo:

1. Recuperar o token guardado no armazenamento seguro do sistema operacional.
2. Enviar `POST /api/auth/refresh` com `Authorization: Bearer <token>`, sem corpo JSON.
3. Se retornar 200, salvar imediatamente o novo token e substituir o antigo; usar o novo nas demais requisições.
4. Se retornar 401, apagar o token local e pedir usuário/senha. Falha de rede ou 5xx não significa credencial expirada: manter o token e tentar novamente.

Cada renovação válida inicia mais 15 dias, sem prazo total fixo para a conta permanecer conectada. Se o aplicativo ficar aberto por vários dias, renovar periodicamente (por exemplo, uma vez por dia); consultas normais não prolongam a validade. No instante exato de expiração, o token deixa de ser aceito, inclusive para renovação. Coordenar uma renovação por vez no cliente: duas renovações simultâneas com o mesmo token não produzem dois tokens válidos. Se a resposta da renovação se perder depois de concluída no servidor, será necessário entrar com senha novamente, pois o token anterior já foi invalidado.

Cadastro de outro usuário autenticado:

```json
{"username":"novo.agente","password":"senha-escolhida-pelo-usuario","role":"AGENT"}
```

Os tokens são valores aleatórios opacos de 256 bits; somente seu hash SHA-256 fica na tabela `auth_session`. A validade e o usuário vêm do banco, portanto a sessão sobrevive ao reinício da API. Não são usados cookies de login nem sessão HTTP; a API aceita somente o header Bearer e exige HTTPS na implantação para proteger senha/token em trânsito. As respostas de login/renovação têm `Cache-Control: no-store`.

O `TokenAuthenticationFilter` popula o contexto do Spring Security a cada requisição; o `AuthService` trava e revalida a sessão ao renovar ou sair. O funcionamento segue a [arquitetura de autenticação do Spring Security](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html). No Swagger, use **Authorize** e cole o token retornado; as páginas e a especificação permanecem públicas.

### Rotas de negócio

Após configurar o PostgreSQL conforme as seções seguintes, execute `com.QualificaMaisAnaliticApplication` ou `.\mvnw.cmd spring-boot:run`. O starter web inicia o servidor HTTP; a URL padrão é `http://localhost:8080`, salvo configuração local de porta/contexto. A inicialização também executa as migrações e pode iniciar a coleta agendada. Para testar somente a API, configure `app.sheets.check-enabled=false` no arquivo local. Inclua `Authorization: Bearer <token>` nos exemplos abaixo.

As rotas abaixo refletem o código atual. Os segmentos repetidos, como `/api/person/person/{cpf}`, fazem parte do contrato existente. POST e PATCH recebem JSON com `Content-Type: application/json`; as respostas contêm as entidades salvas.

| Método | Caminho | Entrada / comportamento | Sucesso |
| --- | --- | --- | --- |
| POST | `/api/person` | `AddPersonDto`: cadastra pessoa e endereço. | 201 + pessoa |
| PATCH | `/api/person` | `UpdatePersonDto`: identifica pelo campo `Cpf` do JSON e atualiza os campos fornecidos. | 200 + pessoa |
| GET | `/api/person` | Lista pessoas ordenadas por nome e, em empate, ID. | 200 + lista |
| GET | `/api/person/person/{cpf}` | Busca por CPF, aceitando máscara. | 200 + pessoa |
| DELETE | `/api/person/person/{id}` | Exclui pelo ID interno da pessoa. | 204 sem corpo |
| POST | `/api/course` | `AddCourseDto`: nome, descrição, início e fim. | 201 + curso |
| PATCH | `/api/course` | `UpdateCourseDto`: `courseId` e campos a atualizar. | 200 + curso |
| GET | `/api/course` | Lista cursos. | 200 + lista |
| GET | `/api/course/course/{name}` | Busca um curso pelo nome exato. | 200 + curso |
| DELETE | `/api/course/course/{id}` | Exclui pelo ID do curso. | 204 sem corpo |
| POST | `/api/courseClass` | `AddCourseClassDto`: dia, sessão, horários e `courseId` do curso existente. | 201 + turma |
| PATCH | `/api/courseClass` | `UpdateCourseClassDto`: `classId` e campos a atualizar. | 200 + turma |
| GET | `/api/courseClass/courseClass/{courseId}` | Lista turmas de um curso existente. | 200 + lista |
| DELETE | `/api/courseClass/courseClass/{id}` | Cancela a aula pelo ID, preservando o histórico. | 204 sem corpo |
| POST | `/api/presence` | `AddPresenceDto`: `personId`, `courseClassId` e `status`. O curso vem da aula. | 201 + presença |
| PATCH | `/api/presence` | `PresenceUpdateDto`: `personId`, `courseClassId` e novo `status`. | 200 + presença |
| GET | `/api/presence/presence/{personId}` | Lista presenças da pessoa. | 200 + lista |
| GET | `/api/presence` | **Corpo JSON** com `courseId` e `courseClassId`; filtra as presenças. | 200 + lista |
| POST | `/api/register` | `AddRegisterDto`: `personCpf`, `courseOfInterestId` e `registerDate`. | 201 + inscrição |
| GET | `/api/register/register/{cpf}` | Lista inscrições do CPF, aceitando máscara. | 200 + lista |
| GET | `/api/register` | **Corpo JSON** com `personCpf` e `courseOfInterestId`. | 200 + inscrição |
| DELETE | `/api/register` | Mesmo corpo da busca específica. | 204 sem corpo |

As consultas de presenças por aula/curso e de inscrição por CPF/curso usam `@RequestBody` em GET. Os parâmetros na URL não substituem o corpo obrigatório. A exclusão de inscrição também exige corpo JSON. A API declara 22 endpoints de negócio e cinco de autenticação.

### Exemplos de entrada

Pessoa (`POST /api/person`), com valores fictícios:

```json
{
  "fullName": "Pessoa Exemplo",
  "cpf": "012.345.678-90",
  "email": "pessoa@example.com",
  "personalPhone": "(11) 99999-0000",
  "personalPhoneHasWhatsapp": false,
  "address": {"street": "Rua Exemplo", "number": 42, "neighborhood": "Centro"},
  "gender": "FEMALE",
  "education": "HIGH_SCHOOL_COMPLETE",
  "workState": "ONLY_STUDYING",
  "disabilities": ["HEARING"]
}
```

A API usa os nomes dos enums no JSON. A aceitação de descrições e códigos descrita na seção de Sheets pertence ao mapper da planilha. CPF e telefones são normalizados pelos serviços. No PATCH de pessoa, a chave atual é `Cpf` com C maiúsculo, por exemplo `{"Cpf":"01234567890","email":"novo@example.com"}`. Telefones omitidos são preservados; `familyPhone` explicitamente vazio limpa esse contato opcional.

Criar um curso pelo PowerShell e obter seu ID para a planilha:

```powershell
$courseBody = @{
    name = 'Curso Exemplo'
    description = 'Introducao'
    start = '2026-10-01'
    finish = '2026-11-01'
} | ConvertTo-Json
$course = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/course' -Headers @{Authorization = "Bearer $token"} -ContentType 'application/json' -Body $courseBody
$course.id
```

Turma (`POST /api/courseClass`), substituindo `42` pelo ID retornado no cadastro do curso:

```json
{
  "day": "2026-10-01",
  "session": "Manhã",
  "start": "08:00:00",
  "finish": "10:00:00",
  "courseId": 42
}
```

Na criação, envie `courseId` diretamente; o formato antigo `course: {"id": ...}` não preenche esse campo. ID ausente retorna 400; ID não encontrado retorna 404. O PATCH de aula continua usando `course: {"id": ...}` quando houver troca de curso. Horários usam texto `HH:mm:ss`.

O Swagger fica em `/swagger-ui/index.html`, e a especificação em `/v3/api-docs`. O projeto usa springdoc 3.1.1, da linha compatível com Spring Boot 4 segundo a [documentação oficial](https://springdoc.org/). Os testes verificam a disponibilidade da UI e o schema de criação de aula com `courseId` e horários como strings.

Presença (`POST /api/presence`), usando IDs existentes de pessoa e aula:

```json
{"personId": 7, "courseClassId": 15, "status": "PRESENT"}
```

Os status disponíveis são `PRESENT`, `ABSENT` e `JUSTIFIED`. Criação e atualização identificam a presença por pessoa + aula, sem receber curso/data. O curso é obtido da aula na criação e o dia aparece em `courseClass.day` na resposta. Exemplo de atualização: `{"personId":7,"courseClassId":15,"status":"JUSTIFIED"}`. O GET de listagem recebe `{"courseId":42,"courseClassId":15}`; uma combinação incompatível retorna lista vazia. O DTO de filtro ainda conserva o nome `PresenceByDayAndCourseDto`, mas seu campo atual é `courseClassId`.

`PresenceService` verifica pessoa/aula existentes, inscrição da pessoa no curso da aula e duplicidade na criação; criação e atualização executam em transação. A V1 exige `course_class_id`, sua chave estrangeira e unicidade de pessoa/aula. A atualização altera apenas o status. A coluna `course_id` de presença é preenchida a partir da aula; uma aula com presenças não pode mudar de curso, dia ou horários, preservando os vínculos históricos.

Inscrição (`POST /api/register`), usando pessoa e curso existentes:

```json
{"personCpf":"01234567890","courseOfInterestId":42,"registerDate":"2026-10-01"}
```

O serviço de inscrição normaliza CPF nas quatro operações, aceitando máscara e removendo caracteres não numéricos antes de validar 11 dígitos. GET e DELETE em `/api/register` recebem `{"personCpf":"01234567890","courseOfInterestId":42}`. Não existe PATCH de inscrição.

### Respostas e limites atuais

As respostas usam as entidades JPA diretamente. Um curso inclui `courseClass`; cada turma inclui seu curso, mas esse curso aninhado omite `courseClass` para evitar recursão no JSON. Ainda não há DTOs específicos de saída nem paginação.

`ApiExceptionHandler` centraliza os erros com `@RestControllerAdvice`; `ApiErrorDto` define o corpo com `status` e `message`. Os serviços usam `EntityNotFoundException` para ausência de registro, `IllegalArgumentException` para entradas inválidas e `ConflictException` para conflitos de negócio. As regras continuam nos serviços.

| HTTP | Situação |
| --- | --- |
| 400 | JSON/corpo inválido, CPF/telefone inválido, identificador obrigatório ausente, horários inválidos ou campo obrigatório nulo. |
| 401 / 403 | Login/token ausente, inválido ou expirado / acesso negado. Atualmente não existem restrições diferentes entre os dois perfis. |
| 404 | Registro solicitado não encontrado, inclusive exclusão de pessoa/curso inexistentes, ou rota inexistente. Listagens vazias continuam retornando 200. |
| 409 | Duplicidade, conflito de horários, presença sem inscrição/em aula inativa, alteração de aula com presenças, vínculos que impedem exclusão ou disputa por bloqueio. Busca singular com vários resultados também retorna 409. |
| 405 / 415 | Método HTTP não permitido / tipo de conteúdo não suportado. |
| 500 | Falha inesperada, com mensagem genérica para o cliente e diagnóstico no log do servidor. |

Exemplo de presença sem inscrição:

```json
{"status":409,"message":"A pessoa não possui inscrição no curso desta aula."}
```

Erros de integridade conhecidos do banco são traduzidos sem expor SQL, valores dos registros ou stack trace na resposta. NOT NULL/CHECK e valores fora do formato/tamanho retornam 400; unicidade e chaves estrangeiras retornam 409. Falha de integridade não reconhecida retorna 500. Os cabeçalhos HTTP do Spring são preservados, inclusive `Allow` no 405. O tratamento dos erros MVC segue a extensão de [ResponseEntityExceptionHandler](https://docs.spring.io/spring-framework/docs/7.0.4/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html).

Para conferir sem cadastrar dados, execute `GET /api/person/person/123`: a resposta esperada é `400` com `{"status":400,"message":"CPF deve conter 11 dígitos."}`. A suíte `ApiControllerTests` verifica as respostas com serviços e banco; `ApiExceptionHandlerTests` simula falhas inesperadas e de bloqueio.

Ainda não há validação completa dos campos com Bean Validation. As obrigatoriedades existentes foram preservadas; não foram acrescentadas regras de e-mail, dígitos verificadores de CPF ou novas exigências para cursos. A busca singular de curso por nome exige que o nome identifique apenas um resultado, embora o banco permita nomes repetidos.

Os endpoints de negócio exigem Bearer válido; ADMIN e AGENT têm acesso igual. O endereço é recebido com a pessoa e não possui endpoints independentes. A inscrição direta e a importação compartilham a checagem de horários. Presença exige inscrição no curso da aula; a unicidade pessoa/aula é garantida pela V1.

### Uma aula por curso e dia

No commit `d8fede8`, a criação de `CourseClass` passou a consultar `existsByCourseAndDay(course, day)` e a lançar `IllegalArgumentException` quando encontra uma aula desse curso na mesma data. A regra é por **ID de curso e dia**, independentemente da sessão/horário. Cursos diferentes podem ter aula no mesmo dia, e o mesmo curso pode ter aulas em dias diferentes.

A regra é aplicada no serviço e no banco:

- A edição carrega a aula, combina os valores enviados no PATCH com os salvos e consulta duplicidade excluindo o próprio ID. Reenviar curso/dia da própria aula é permitido; alterar somente curso ou dia também verifica a combinação final antes de modificar campos.
- A V1 cria `UNIQUE(course_id, class_day)` e `NOT NULL` nas duas colunas. O mapeamento JPA declara as mesmas restrições e o Hibernate valida o schema.
- A restrição no banco também impede duplicatas em gravações diretas e simultâneas.
- A V2 exige sessão, início e fim não nulos. A V3 usa `TIME` e exige início anterior ao fim; a data fica somente em `day`. Criação e PATCH validam os valores finais antes de salvar.

`CourseClassServiceTests` e `CourseClassDailyRuleTests` cobrem criação duplicada, cursos/dias diferentes, atualização da própria aula e PATCH parcial conflitante ou permitido. `InitialSchemaMigrationTests` verifica escrita direta, nulos e referências ausentes; `ConcurrentPersistenceTests` verifica duas transações disputando o mesmo curso/dia.

Referências: [`UniqueConstraint` em Jakarta Persistence](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/uniqueconstraint) e [restrições do PostgreSQL](https://www.postgresql.org/docs/current/ddl-constraints.html).

### Status, reagendamento e concorrência de aulas

Aulas começam com `statusClass: "ACTIVE"`. O PATCH permite `ACTIVE`, `CANCELED` ou `POSTPONED`, por exemplo `{"classId":15,"statusClass":"POSTPONED"}`. Horários usam `HH:mm:ss`; valores iguais, invertidos ou ausentes no cadastro são rejeitados. Não há aula atravessando meia-noite no modelo atual.

O DELETE de aula altera o status para `CANCELED`. A aula e suas presenças permanecem nas consultas. Canceladas/adiadas não contam no conflito de horários e não recebem novas presenças. É possível corrigir o status de uma presença já existente. A unicidade curso/dia continua valendo para aulas inativas.

Criar, reagendar ou reativar uma aula verifica conflitos de todos os inscritos. Aulas com presenças não podem alterar curso, dia ou horários. Uma alteração recusada não salva outros campos enviados no mesmo PATCH. Sem presenças, uma aula adiada pode receber nova data/horário e `ACTIVE` no mesmo PATCH.

Inscrição e presença usam lock de leitura no curso antes do lock de escrita da pessoa. Alterar aula usa lock de escrita nos cursos envolvidos antes de bloquear os inscritos por CPF. Importações bloqueiam todos os cursos por ID e todas as pessoas por CPF, em ordem crescente, mantendo a ordem original no processamento/relatório. Assim, os fluxos coordenam alteração de horários e novas inscrições sem inverter a ordem de aquisição dos bloqueios.

Um CPF novo ainda pode disputar a restrição de unicidade; a transação perdedora reverte integralmente e pode tentar novamente. O scheduler faz nova tentativa no próximo ciclo. Não há retry automático dentro da mesma chamada nem garantia para alterações por SQL externo aos serviços.
## Persistência atual e reimportação

Existe uma pessoa por CPF normalizado e cada `Register` contém a pessoa, uma referência obrigatória a `Course` e a data de inscrição. Cursos diferentes podem reutilizar a mesma pessoa. A identidade da inscrição é a combinação de pessoa e ID do curso (`person_id`, `course_id`); a data não faz parte dessa chave. Renomear um curso não cria outra inscrição, e cursos com nomes iguais continuam distintos pelos IDs. A inscrição direta e a importação chamam `RegisterUtils.hasScheduleConflict()` para novas inscrições, comparando aulas do mesmo dia. Horários encostados são permitidos. A tabela abaixo descreve o comportamento da importação.

| Situação | Comportamento implementado |
| --- | --- |
| CPF ainda não cadastrado | Insere pessoa, endereço, deficiências e inscrição juntos. |
| Mesmo CPF, mesmo curso e mesmos dados | Mantém a inscrição e contabiliza como sem alteração. |
| Mesmo CPF e outro curso | Cria outra inscrição reutilizando a pessoa salva. |
| Mesmo CPF com dados pessoais diferentes | Preserva a pessoa e relata divergências; se o curso for novo, também cria a inscrição com os dados já salvos. |
| Mesmo CPF e mesmo curso, com data diferente | Preserva a inscrição e relata a diferença de data. |
| CPF repetido no mesmo lote | Reutiliza a pessoa criada pela primeira linha e compara as inscrições por curso. |
| Linha corrigida na planilha | Se antes era inválida, processa a pessoa e a inscrição conforme as regras acima. |
| CPF corrigido para outro número | É considerado outra identidade e pode gerar novo cadastro; ainda não há vínculo estável com a inscrição da origem para reconciliar essa correção. |
| Linha removida da planilha | Não exclui o cadastro do banco. |
| ID do curso vazio, nome em texto ou número inválido | Descarta a linha e informa o erro de formato no relatório da coleta. |
| ID válido no formato, mas inexistente no catálogo | Interrompe a persistência e reverte o lote inteiro; corrija o ID antes de reimportar. |
| Nova inscrição com horário sobreposto para a mesma pessoa | Lança erro e reverte o lote inteiro, inclusive se o conflito for com outra linha do lote. Inscrições já salvas permanecem intactas. |

Após a primeira gravação, o banco prevalece sobre a planilha. Divergências ficam no resultado da importação e no log; ainda não existe tela de resolução nem histórico persistido desses conflitos. As restrições `uk_person_cpf` e `uk_register_person_course` impedem duplicatas de pessoa e de inscrição no modelo atual. A ordem das linhas não é usada como identidade. Uma inscrição nova pode contar tanto em `inserted` quanto em `conflicts` quando os dados pessoais recebidos diferem dos salvos.

Linhas inválidas são excluídas pelo mapper e continuam no relatório. As linhas válidas são gravadas em uma única transação: se qualquer gravação falhar, o lote inteiro é revertido, incluindo endereços e deficiências. Não há gravação parcial desse lote. Se duas instâncias tentarem inserir o mesmo CPF simultaneamente, a restrição única pode reverter um dos lotes; a próxima execução relê a planilha e compara os registros já gravados.

O serviço de persistência recebe inscrições com pessoa e endereço novos, com CPF normalizado e sem IDs, produzidos pelo mapper. A referência ao curso deve conter o ID de um curso já cadastrado: a importação não cria nem atualiza cursos. A consulta por CPF espera os 11 dígitos, sem máscara. `AddRegisterDto` e `SearchRegisterDto` também usam `courseOfInterestId` (`Long`).

### Preparar o PostgreSQL

Para uma instalação nova, crie um banco vazio e configure seu acesso no `application.properties` local. O usuário do banco precisa poder executar as migrações. Ao iniciar o Spring, o Flyway aplica V1 (estrutura inicial), V2 (sessão/horários obrigatórios), V3 (horários `TIME` e status da aula) e `V4__users_and_auth_sessions.sql` (usuários e sessões de login). A V4 acrescenta duas tabelas e preserva os cadastros existentes. O Hibernate valida a estrutura (`ddl-auto=validate`).

Em um banco que já tem a V1 consolidada, a V2 preserva dados e IDs, mas exige corrigir previamente aulas com sessão/horários nulos. A migração falha se encontrar esses dados; não preenche valores nem exclui aulas automaticamente. As anotações JPA são `@Column(nullable=false)` nos campos simples; a FK continua em `CourseClass.course`, e a coleção inversa em `Course` usa somente `mappedBy`.

O histórico anterior foi consolidado durante o desenvolvimento, quando seus dados eram descartáveis. A nova V1 não converte bancos com as versões antigas: esses bancos precisam ser recriados de forma explícita. Não use `repair` ou `baseline-on-migrate` para tratar as estruturas como equivalentes. A aplicação não apaga dados automaticamente; `clean-disabled=true` permanece habilitado.

A configuração compartilhada fica em `src/main/resources/application.yaml`; as credenciais continuam no `.properties` local. Todos os enums são persistidos pelo nome. Inscrição, pessoa e endereço têm IDs automáticos. O endereço é gravado por cascata com a pessoa, e o serviço associa a pessoa persistida à inscrição na mesma transação.

Novas alterações devem evoluir com V4 e seguintes, sem modificar migrações já aplicadas.

Para importar usando outro componente Spring, injete `RegisterImportService`:

```java
RegisterImportResult result = registerImportService.importRegisters();
// result.inserted(), result.unchanged(), result.conflicts(), result.errors(), result.ignoredRows()
```

As mensagens de erro SQL do Hibernate são desativadas na configuração padrão porque podem conter CPF e outros valores pessoais. O agendamento informa a classe da falha e tenta novamente no ciclo seguinte.

## Cabeçalho da planilha

Importe [modelo-cadastros.csv](modelo-cadastros.csv) no Google Sheets usando `;` como separador, ou crie as colunas abaixo. A ordem é livre. Colunas extras são ignoradas; cabeçalhos reconhecidos duplicados geram erro.

| Coluna | Campo | Formato |
| --- | --- | --- |
| Nome completo | `fullName` | Texto |
| Nome social | `socialName` | Texto opcional; a coluna também pode ser omitida |
| CPF | `cpf` | 11 dígitos ou `000.000.000-00` |
| E-mail | `email` | Texto |
| Contato com WhatsApp | `personalPhone` | Telefone pessoal obrigatório com DDD, com ou sem WhatsApp |
| O contato informado possui WhatsApp? | `personalPhoneHasWhatsapp` | `Sim` ou `Não`, obrigatório; também aceita `true` ou `false` |
| Contato de familiar | `familyPhone` | Telefone com DDD opcional; a coluna também pode ser omitida |
| Rua | `address.street` | Texto |
| Número | `address.number` | Inteiro não negativo |
| Bairro | `address.neighborhood` | Texto |
| Gênero | `gender` | Descrição, nome ou código de `Gender` |
| Escolaridade | `education` | Descrição, nome ou código de `Education` |
| Situação de trabalho | `workState` | Descrição, nome ou código de `WorkState` |
| Deficiência | `disabilities` | Uma ou mais descrições, nomes ou códigos de `Disabilities`, separados por vírgula, ponto e vírgula ou quebra de linha |
| ID do curso | `courseOfInterestId` | Inteiro positivo correspondente ao `id` de um curso cadastrado |
| Data de cadastro | `registerDate` | `dd/MM/aaaa`, `dd/MM/aaaa HH:mm:ss`, `aaaa-MM-dd` ou data/hora ISO local |

Como regra inicial, todos os campos acima são obrigatórios, exceto nome social e contato de familiar. Cabeçalhos e descrições dos enums ignoram maiúsculas, acentos, espaços e pontuação. Também são aceitos os nomes Java dos campos e aliases como `Carimbo de data/hora`, `Endereço de e-mail` e `Logradouro`. Para títulos diferentes do formulário, acrescente aliases em `RegisterSheetMapper.Column`.

Na coluna `ID do curso`, informe, por exemplo, `42` se esse for o ID do curso desejado no banco. Os cabeçalhos `courseId`, `Curso de interesse` e `courseOfInterest` continuam aceitos como aliases, mas o conteúdo agora deve ser o ID, não o nome. Atualize as respostas existentes e a origem do formulário para fornecer esse valor. Não são aceitos zero, negativos, casas decimais, notação científica ou nomes. `collect()` e o Quickstart validam apenas o formato e retornam uma referência `Course` contendo o ID; a existência do curso é verificada por `importRegisters()` ao acessar o banco.

O formulário também pode usar `Endereço (rua)`, `Trabalha atualmente?` e `Data da inscrição`. Quando a data de inscrição e o carimbo de data/hora existem juntos, a data de inscrição prevalece. O carimbo só é usado quando não existe uma coluna específica de data de inscrição/cadastro.

Os telefones são armazenados como texto com DDD, apenas com os 10 ou 11 dígitos nacionais. A coleta aceita máscaras como `(11) 99999-0000` e o prefixo explícito `+55`. A validação confere o formato, sem verificar se o número existe. O telefone pessoal é obrigatório mesmo quando a resposta sobre WhatsApp é `Não`; essa resposta é armazenada separadamente como `false`. O telefone de familiar vazio fica `null` e não recebe a indicação de WhatsApp do telefone pessoal. Na planilha, adicione as novas colunas usando os títulos acima; também é aceito `Telefone pessoal` no lugar de `Contato com WhatsApp`.

Exemplos de enums: `Feminino`, `FEMALE` ou `1`; `Ensino Médio Completo`, `HIGH_SCHOOL_COMPLETE` ou `5`; `Não, somente estudo` ou `7`; `Nenhuma` ou `6`. A lista completa está em `src/main/java/com/enums`.

As deficiências são um `Set<Disabilities>`, sem duplicatas. Exemplos de célula: `Auditiva, Visual`, `Intelectual; Física/Motora` ou `1, 4`. `Nenhuma` e `Sem Declaração` devem aparecer isoladamente. A opção `Múltiplas` foi removida: informe as deficiências específicas. Um item desconhecido invalida a linha inteira, sem descartar silenciosamente parte da resposta.

As deficiências pertencem à pessoa. O mapeamento JPA usa `person_disabilities`, com `person_id` e `disability`, guardando o nome de cada enum. A combinação das duas colunas é única e a V1 já cria essa estrutura. O uso de `@ElementCollection` com `@Enumerated(EnumType.STRING)` segue a [documentação de Jakarta Persistence](https://jakarta.ee/specifications/platform/9.1/apidocs/jakarta/persistence/enumerated.html).

Formate a coluna CPF como texto no Sheets para preservar zeros à esquerda. A coleta valida o formato e remove a máscara; não verifica os dígitos verificadores. O número do endereço segue o `int` do modelo atual, portanto `s/n` e `12A` geram erro. A data/hora é convertida para `LocalDate`, descartando o horário.

## Configuração local e Git

O ID da planilha, o nome da aba e os dados de acesso ao banco ficam em `src/main/resources/application.properties`, ignorado pelo Git. As credenciais OAuth (`src/main/resources/credentials.json`) e a pasta `tokens/` também estão ignoradas.

Para preparar uma nova cópia do projeto, copie o modelo sem dados privados:

```powershell
Copy-Item application.properties.example src/main/resources/application.properties
```

Preencha o arquivo local com `app.sheets.spreadsheet-id`, `app.sheets.range` e os dados do banco. O modelo também aceita `GOOGLE_SHEETS_SPREADSHEET_ID`, `GOOGLE_SHEETS_RANGE`, `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. O ID e o intervalo ficam vazios quando não configurados. Preserve seu arquivo local existente; a cópia do modelo é necessária apenas na configuração inicial.

Em valores do arquivo `.properties`, represente acentos com escapes Unicode, como `\u00e7` para `ç`, ou use a variável de ambiente para o intervalo. Isso preserva os nomes das abas no carregamento da configuração.

## Executar pela IDE

1. Use Java 25 e habilite o processamento de anotações do Lombok.
2. Mantenha as credenciais OAuth de aplicativo desktop em `src/main/resources/credentials.json`, com a API do Google Sheets habilitada no projeto Google.
3. Configure o ID, o intervalo e a linha do cabeçalho no `application.properties` local. Execute o `main` de `com.SheetsQuickstart` sem argumentos para usar esses valores. Se houver argumentos antigos na configuração de execução do IntelliJ, remova-os. Para substituir os valores apenas nessa execução, use:

   ```text
   ID_DA_PLANILHA "'Nome da aba'!A1:Z"
   ```

   Use o ID presente entre `/d/` e `/edit` na URL da planilha. O intervalo deve começar na linha do cabeçalho. Para um cabeçalho na linha 5, use `"'Minha aba'!A5:Z" 5` após o ID.

   O intervalo usa o nome exato da aba, não o nome do arquivo. Se o Google retornar `Unable to parse range`, confira esse nome e o intervalo informado.

4. Na primeira coleta, autorize no navegador com uma conta que tenha acesso à planilha. O retorno OAuth usa a porta local 8888 e os tokens são reutilizados na pasta `tokens`.

O console mostra somente a quantidade de cadastros e os erros, sem imprimir dados pessoais. Para acessar os objetos, use `result.registers()` no serviço. O Quickstart carrega `application.properties` e resolve suas variáveis de ambiente sem iniciar o banco ou o agendamento, usando o [carregador de configuração do Spring Boot](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/context/config/ConfigDataEnvironmentPostProcessor.html). Os argumentos opcionais acima prevalecem sobre a configuração local.

## Usar no Spring

O Spring carrega o `application.properties` local e preenche `SheetsProperties`. Injete `RegisterCollectionService` em outro componente e chame `collect()`:

```java
SheetImportResult result = registerCollectionService.collect();
List<Register> registers = result.registers();
```

Configurações disponíveis no `application.properties` local: `app.sheets.spreadsheet-id`, `app.sheets.range`, `app.sheets.header-row`, `app.sheets.credentials-path`, `app.sheets.tokens-directory` e `app.sheets.oauth-port`. Para credenciais fora do projeto, use `app.sheets.credentials-path=file:C:/caminho/credentials.json`. Para usar uma variável de ambiente personalizada, configure explicitamente o vínculo, por exemplo `app.sheets.spreadsheet-id=${GOOGLE_SHEETS_SPREADSHEET_ID}`. Ajuste `header-row` para o número real da primeira linha do intervalo, usado no relatório de erros.

A autenticação acontece apenas quando `collect()` é chamado. A aplicação Spring continua usando a configuração PostgreSQL existente. Erros de rede/autorização e cabeçalhos inválidos interrompem a coleta; erros de conteúdo descartam apenas a linha afetada e são retornados no relatório. Confira `errors()` antes de consumir os registros.

## Checagem automática

Execute `com.QualificaMaisAnaliticApplication` no IntelliJ ou `.\mvnw.cmd spring-boot:run` e mantenha a aplicação aberta. O `SheetsQuickstart` continua executando apenas uma coleta manual.

Em `src/main/resources/application.properties`, configure:

```properties
app.sheets.check-enabled=true
app.sheets.check-interval=5m
app.sheets.check-initial-delay=10s
```

O padrão faz a primeira checagem após 10 segundos e espera 5 minutos após o fim de cada execução para começar a próxima. São aceitos intervalos como `30s`, `5m` ou `1h`; use um intervalo maior que zero. O atraso inicial pode ser `0s`. O agendamento usa [fixed delay do Spring](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html), evitando sobreposição das execuções dessa tarefa na mesma instância.

O arquivo já oferece as variáveis `GOOGLE_SHEETS_CHECK_ENABLED`, `GOOGLE_SHEETS_CHECK_INTERVAL` e `GOOGLE_SHEETS_CHECK_INITIAL_DELAY` como alternativa à edição. Reinicie a aplicação após mudar os valores. Use `app.sheets.check-enabled=false` para desativar a rotina. ID e intervalo de células também ficam no `application.properties` local.

Cada ciclo relê todo o intervalo, localiza ou cria as pessoas por CPF e processa as inscrições por curso, registrando as quantidades, divergências e erros por linha. Falhas de leitura ou persistência são registradas, e o próximo ciclo tenta novamente. A aplicação usa o PostgreSQL configurado durante a inicialização; a autorização Google ocorre na primeira coleta, reutilizando os tokens existentes. Para a primeira autorização, execute o Quickstart e conclua o fluxo no navegador antes de deixar a rotina rodando sem interação.

## Testes

```powershell
.\mvnw.cmd test
```

O perfil `test` usa dados fictícios, H2 em memória, Flyway e validação de schema; coleta Google desabilitada. Não acessa o banco de trabalho. Resultado de 02/10/2026: **341 testes aprovados em H2 e PostgreSQL 18.6 isolado**, sem falhas, erros ou ignorados. Logs: `target/auth-h2-tests.log` e `target/auth-postgres-tests.log`.

| Área | Cobertura |
| --- | --- |
| Autenticação | 22 casos HTTP com Bearer real, relógio controlado, expiração/renovação/logout, perfis com acesso igual e rotação simultânea; 3 casos de cadastro inicial e 2 da migração V4. |
| API e serviços | 39 casos em `ApiControllerTests` para os 22 endpoints e 6 em `ApiExceptionHandlerTests`; sucesso, erros 400/404/405/409/415/500, dados obrigatórios, histórico e rollback. |
| Importação | Reimportação, divergências, dados preservados, horários por ID de curso, conflitos existentes/entre linhas e rollback do lote, endereço e deficiências. |
| Regras de aula | `ClassScheduleLifecycleTests`: 15 casos de criação após inscrição, reagendamento, validação de horários, cancelamento/adiamento, reativação e proteção de presenças. |
| Concorrência | Unicidade de pessoa/curso, pessoa/aula e curso/dia; 11 casos de inscrição API/Sheets e 12 casos de lotes em ordem inversa, mudanças de aula versus inscrição e cancelamento versus presença. |
| Migrações | V1 inicial, V2 campos obrigatórios e cinco casos da V3: conversão para hora, status inicial, IDs/dados preservados, reexecução e restrições SQL. |
| Validação | CPF conforme limpeza definida, telefones, deficiências, campos omitidos, mapper, coleta/scheduler simulados e contexto Spring. |

As verificações de concorrência usam transações independentes e conferem o estado confirmado. A suíte não equivale a teste de carga ou validação operacional do servidor. CPF é normalizado por quantidade de dígitos, sem cálculo de dígitos verificadores.

Para apenas API, autenticação ou os fluxos de aula:

```powershell
.\mvnw.cmd test "-Dtest=ApiControllerTests"
.\mvnw.cmd test "-Dtest=AuthControllerTests,InitialUserConfigurationTests,AuthMigrationTests"
.\mvnw.cmd test "-Dtest=ClassScheduleLifecycleTests,ConcurrentClassAndBatchTests,ClassTimeStatusMigrationTests"
```

Também é possível usar PostgreSQL exclusivo para testes, criado previamente e inicialmente vazio:

```powershell
.\mvnw.cmd test "-Dtest.db.url=jdbc:postgresql://127.0.0.1:55449/class_flow_tests" "-Dtest.db.driver=org.postgresql.Driver" "-Dtest.db.username=class_test"
```

A porta e o banco são exemplos; o servidor usado na validação foi encerrado. Senha opcional por `TEST_DB_PASSWORD`. Os testes removem registros e criam schemas temporários: nunca apontar para banco de trabalho.
