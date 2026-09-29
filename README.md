# Qualifica Mais Analitic

Coleta e persistência de inscrições do Google Sheets no PostgreSQL usando `Register`, `Person` e `Address`.

## Estrutura

- `GoogleSheetsReader`: autentica com OAuth e lê a planilha com acesso somente de leitura.
- `RegisterSheetMapper`: identifica as colunas pelo cabeçalho e converte cada linha em `Register`, incluindo `Person` e seu `Address`.
- `RegisterCollectionService.collect()`: coordena a leitura e retorna `SheetImportResult`.
- `RegisterImportService.importRegisters()`: coleta a planilha e encaminha as linhas válidas para persistência.
- `RegisterPersistenceService`: grava o lote em uma transação, reutiliza a pessoa por CPF e compara inscrições pelo curso de interesse.
- `PersonRepository`: consulta pessoas por CPF normalizado.
- `RegisterRepository`: consulta inscrições por ID ou pelo CPF da pessoa e curso de interesse.
- `RegisterCollectionScheduler`: executa a importação periódica e registra o resumo, as divergências e os erros no log.
- `SheetImportResult`: contém `registers` válidos, `errors` com o número da linha e a primeira falha encontrada nela, e `ignoredRows` para linhas vazias.
- `SheetsProperties`: recebe a configuração local da coleta, compartilhada pelo Spring e pelo Quickstart.
- `SheetsQuickstart`: permite executar a coleta pela IDE sem iniciar o Spring/PostgreSQL.

A chamada `collect()` e o Quickstart continuam retornando objetos em memória, sem gravar. A chamada `importRegisters()` e o agendamento gravam no banco. Os IDs de `Register`, `Person` e `Address` são gerados pelo banco ao inserir.

## Persistência atual e reimportação

Existe uma pessoa por CPF normalizado e cada `Register` contém a pessoa, o curso de interesse e a data de inscrição. Cursos diferentes podem reutilizar a mesma pessoa. A comparação atual de curso usa o texto exato de `courseOfInterest`; ainda não existe catálogo de cursos ou validação de horários. A tabela abaixo descreve o comportamento da importação.

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

Após a primeira gravação, o banco prevalece sobre a planilha. Divergências ficam no resultado da importação e no log; ainda não existe tela de resolução nem histórico persistido desses conflitos. As restrições `uk_person_cpf` e `uk_register_person_course` impedem duplicatas de pessoa e de inscrição no modelo atual. A ordem das linhas não é usada como identidade. Uma inscrição nova pode contar tanto em `inserted` quanto em `conflicts` quando os dados pessoais recebidos diferem dos salvos.

Linhas inválidas são excluídas pelo mapper e continuam no relatório. As linhas válidas são gravadas em uma única transação: se qualquer gravação falhar, o lote inteiro é revertido, incluindo endereços e deficiências. Não há gravação parcial desse lote. Se duas instâncias tentarem inserir o mesmo CPF simultaneamente, a restrição única pode reverter um dos lotes; a próxima execução relê a planilha e compara os registros já gravados.

O serviço de persistência recebe inscrições com pessoa e endereço novos, com CPF normalizado e sem IDs, produzidos pelo mapper. A consulta por CPF espera os 11 dígitos, sem máscara.

### Preparar o PostgreSQL

Crie o banco e configure seu acesso no `application.properties` local. O usuário do banco precisa poder executar as migrações. Ao iniciar o Spring, o Flyway aplica V1 e V2 em um schema vazio; em um banco já na V1, aplica somente a V2. A V2 separa `person` de `register`, transfere as deficiências para `person_disabilities` e preserva os IDs das inscrições, endereços e dados pessoais existentes. O Hibernate valida a estrutura (`ddl-auto=validate`); não use `create` ou `create-drop` no banco de trabalho.

A configuração compartilhada fica em `src/main/resources/application.yaml`; as credenciais continuam no `.properties` local. Todos os enums são persistidos pelo nome. Inscrição, pessoa e endereço têm IDs automáticos. O endereço é gravado por cascata com a pessoa, e o serviço associa a pessoa persistida à inscrição na mesma transação.

**Banco com tabelas antigas:** esta primeira migração cria a estrutura inicial; não converte automaticamente tabelas anteriores, IDs manuais ou enums numéricos. O Flyway recusa um schema não vazio sem histórico de migração. Para esse caso, é necessário preparar uma migração específica para a estrutura e os dados existentes. Não habilite `baseline-on-migrate` apenas para contornar esse erro.

Para importar usando outro componente Spring, injete `RegisterImportService`:

```java
RegisterImportResult result = registerImportService.importRegisters();
// result.inserted(), result.unchanged(), result.conflicts(), result.errors(), result.ignoredRows()
```

As mensagens de erro SQL do Hibernate são desativadas na configuração padrão porque podem conter CPF e outros valores pessoais. O agendamento informa a classe da falha e tenta novamente no ciclo seguinte.

## Cabeçalho da planilha

Importe [modelo-cadastros.csv](docs/modelo-cadastros.csv) no Google Sheets usando `;` como separador, ou crie as colunas abaixo. A ordem é livre. Colunas extras são ignoradas; cabeçalhos reconhecidos duplicados geram erro.

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
| Curso de interesse | `courseOfInterest` | Texto |
| Data de cadastro | `registerDate` | `dd/MM/aaaa`, `dd/MM/aaaa HH:mm:ss`, `aaaa-MM-dd` ou data/hora ISO local |

Como regra inicial, todos os campos acima são obrigatórios, exceto nome social e contato de familiar. Cabeçalhos e descrições dos enums ignoram maiúsculas, acentos, espaços e pontuação. Também são aceitos os nomes Java dos campos e aliases como `Carimbo de data/hora`, `Endereço de e-mail` e `Logradouro`. Para títulos diferentes do formulário, acrescente aliases em `RegisterSheetMapper.Column`.

O formulário também pode usar `Endereço (rua)`, `Trabalha atualmente?` e `Data da inscrição`. Quando a data de inscrição e o carimbo de data/hora existem juntos, a data de inscrição prevalece. O carimbo só é usado quando não existe uma coluna específica de data de inscrição/cadastro.

Os telefones são armazenados como texto com DDD, apenas com os 10 ou 11 dígitos nacionais. A coleta aceita máscaras como `(11) 99999-0000` e o prefixo explícito `+55`. A validação confere o formato, sem verificar se o número existe. O telefone pessoal é obrigatório mesmo quando a resposta sobre WhatsApp é `Não`; essa resposta é armazenada separadamente como `false`. O telefone de familiar vazio fica `null` e não recebe a indicação de WhatsApp do telefone pessoal. Na planilha, adicione as novas colunas usando os títulos acima; também é aceito `Telefone pessoal` no lugar de `Contato com WhatsApp`.

Exemplos de enums: `Feminino`, `FEMALE` ou `1`; `Ensino Médio Completo`, `HIGH_SCHOOL_COMPLETE` ou `5`; `Não, somente estudo` ou `7`; `Nenhuma` ou `6`. A lista completa está em `src/main/java/com/enums`.

As deficiências são um `Set<Disabilities>`, sem duplicatas. Exemplos de célula: `Auditiva, Visual`, `Intelectual; Física/Motora` ou `1, 4`. `Nenhuma` e `Sem Declaração` devem aparecer isoladamente. A opção `Múltiplas` foi removida: informe as deficiências específicas. Um item desconhecido invalida a linha inteira, sem descartar silenciosamente parte da resposta.

As deficiências pertencem à pessoa. O mapeamento JPA usa `person_disabilities`, com `person_id` e `disability`, guardando o nome de cada enum. A combinação das duas colunas é única. A V2 transfere a coleção da antiga tabela `register_disabilities` para a pessoa correspondente. O uso de `@ElementCollection` com `@Enumerated(EnumType.STRING)` segue a [documentação de Jakarta Persistence](https://jakarta.ee/specifications/platform/9.1/apidocs/jakarta/persistence/enumerated.html).

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

Os testes usam dados fictícios e não acessam o Google nem o PostgreSQL configurado no arquivo local. O perfil `test` usa H2 em memória e executa a mesma migração Flyway da aplicação, com validação do schema pelo Hibernate.

`PersonDisabilitiesPersistenceTests` valida a gravação, leitura e atualização das deficiências da pessoa. Execute-o com `.\mvnw.cmd test "-Dtest=PersonDisabilitiesPersistenceTests"`.

`RegisterPersistenceTests` cobre gravação completa, IDs automáticos, CPF com zero inicial, inscrições diferentes para a mesma pessoa, reimportação, divergências, rollback, unicidade de CPF/inscrição e ausência de CPF nos logs de falha SQL. `PersonMigrationTests` cria dados no schema V1 e verifica as migrações seguintes, incluindo vínculos, deficiências e geração de novos IDs. `RegisterImportServiceTests` cobre a ligação entre coleta e persistência.

Em `src/test/java/com/example/qualificamaisanalitic/services`, os testes de pessoa, curso, turma, presença e inscrição verificam cadastro, atualização parcial, consultas, exclusão e registros inexistentes. Os testes unitários usam repositórios simulados e os mappers reais gerados pelo MapStruct. `ServicesPersistenceTests` exercita os serviços com H2/Flyway, incluindo unicidade de inscrição, presenças em cursos diferentes no mesmo dia e atualização de deficiências sem uma transação aberta pelo chamador. Também verifica que uma atualização inválida não altera os dados salvos. `CoursePresencePersistenceTests` verifica os relacionamentos e a leitura das novas tabelas.

`CpfUtilsTests` e `CellphoneUtilsTests` cobrem formatação e normalização, CPF com zero inicial, telefone fixo/celular, prefixo `+55`, DDD 55, contato opcional ausente e quantidades inválidas de dígitos. Esses testes não comprovam validação dos dígitos verificadores de CPF nem existência dos números de telefone.

Também é possível executar a suíte em um **banco PostgreSQL exclusivo para testes**, já criado e inicialmente vazio. Os testes apagam os registros de suas tabelas entre casos; nunca indique um banco de trabalho:

```powershell
.\mvnw.cmd test "-Dtest.db.url=jdbc:postgresql://127.0.0.1:55439/persistence_tests" "-Dtest.db.driver=org.postgresql.Driver" "-Dtest.db.username=persistence_test"
```

Se necessário, forneça a senha de teste pela variável de ambiente `TEST_DB_PASSWORD`. As demais substituições são `test.db.url`, `test.db.driver` e `test.db.username`.

A leitura usa `ROWS` e `FORMATTED_VALUE`, incluindo tratamento das células finais vazias que a API omite, conforme a [documentação de leitura do Google Sheets](https://developers.google.com/workspace/sheets/api/guides/values).
