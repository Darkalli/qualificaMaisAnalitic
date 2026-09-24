# Qualifica Mais Analitic

Coleta de cadastros do Google Sheets usando os campos de `Register` e `Address`.

## Estrutura

- `GoogleSheetsReader`: autentica com OAuth e lê a planilha com acesso somente de leitura.
- `RegisterSheetMapper`: identifica as colunas pelo cabeçalho e converte cada linha em `Register`, incluindo `Address`.
- `RegisterCollectionService.collect()`: coordena a leitura e retorna `SheetImportResult`.
- `RegisterCollectionScheduler`: executa a checagem periódica e registra o resumo e os erros no log.
- `SheetImportResult`: contém `registers` válidos, `errors` com o número da linha e a primeira falha encontrada nela, e `ignoredRows` para linhas vazias.
- `SheetsProperties`: recebe a configuração local da coleta, compartilhada pelo Spring e pelo Quickstart.
- `SheetsQuickstart`: permite executar a coleta pela IDE sem iniciar o Spring/PostgreSQL.

A coleta retorna objetos em memória. Ainda não salva no banco nem remove duplicados. Os IDs de `Register` e `Address` permanecem nulos para uma futura camada de persistência.

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

O mapeamento JPA usa a tabela `register_disabilities`, com `register_id` e `disability`, guardando o nome de cada enum. A combinação das duas colunas é única. O uso de `@ElementCollection` com `@Enumerated(EnumType.STRING)` segue a [documentação de Jakarta Persistence](https://jakarta.ee/specifications/platform/9.1/apidocs/jakarta/persistence/enumerated.html). Se houver dados persistidos no antigo campo único, a migração para essa tabela deve ser feita antes de usar a nova estrutura no banco; a coleta continua retornando os cadastros em memória.

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

Cada ciclo relê todo o intervalo da planilha e registra quantidades e erros por linha. Falhas de leitura são registradas, e o próximo ciclo tenta novamente. A checagem ainda não grava os cadastros no banco nem detecta apenas linhas novas. A aplicação usa o PostgreSQL configurado durante a inicialização; a autorização Google ocorre na primeira coleta, reutilizando os tokens existentes. Para a primeira autorização, execute o Quickstart e conclua o fluxo no navegador antes de deixar a rotina rodando sem interação.

## Testes

```powershell
.\mvnw.cmd test "-Dtest=RegisterSheetMapperTests,RegisterCollectionServiceTests"
```

Esses testes não precisam de Google ou PostgreSQL. O teste de contexto Spring já existente depende da configuração do banco.

`RegisterDisabilitiesPersistenceTests` valida a gravação, leitura e atualização de várias deficiências em um banco H2 em memória, isolado do PostgreSQL configurado. Execute-o com `.\mvnw.cmd test "-Dtest=RegisterDisabilitiesPersistenceTests"`.

A leitura usa `ROWS` e `FORMATTED_VALUE`, incluindo tratamento das células finais vazias que a API omite, conforme a [documentação de leitura do Google Sheets](https://developers.google.com/workspace/sheets/api/guides/values).
