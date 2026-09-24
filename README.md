# Qualifica Mais Analitic

Coleta de cadastros do Google Sheets usando os campos de `Register` e `Address`.

## Estrutura

- `GoogleSheetsReader`: autentica com OAuth e lê a planilha com acesso somente de leitura.
- `RegisterSheetMapper`: identifica as colunas pelo cabeçalho e converte cada linha em `Register`, incluindo `Address`.
- `RegisterCollectionService.collect()`: coordena a leitura e retorna `SheetImportResult`.
- `SheetImportResult`: contém `registers` válidos, `errors` com o número da linha e a primeira falha encontrada nela, e `ignoredRows` para linhas vazias.
- `SheetsProperties`: configura a origem dos dados quando usado no Spring.
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
| Rua | `address.street` | Texto |
| Número | `address.number` | Inteiro não negativo |
| Bairro | `address.neighborhood` | Texto |
| Gênero | `gender` | Descrição, nome ou código de `Gender` |
| Escolaridade | `education` | Descrição, nome ou código de `Education` |
| Situação de trabalho | `workState` | Descrição, nome ou código de `WorkState` |
| Deficiência | `disabilities` | Descrição, nome ou código de `Disabilities` |
| Curso de interesse | `courseOfInterest` | Texto |
| Data de cadastro | `registerDate` | `dd/MM/aaaa`, `dd/MM/aaaa HH:mm:ss`, `aaaa-MM-dd` ou data/hora ISO local |

Como regra inicial, todos os campos acima são obrigatórios, exceto nome social. Cabeçalhos e descrições dos enums ignoram maiúsculas, acentos, espaços e pontuação. Também são aceitos os nomes Java dos campos e aliases como `Carimbo de data/hora`, `Endereço de e-mail` e `Logradouro`. Para títulos diferentes do formulário, acrescente aliases em `RegisterSheetMapper.Column`.

Exemplos de enums: `Feminino`, `FEMALE` ou `1`; `Ensino Médio Completo`, `HIGH_SCHOOL_COMPLETE` ou `5`; `Não, somente estudo` ou `7`; `Nenhuma` ou `6`. A lista completa está em `src/main/java/com/enums`.

Formate a coluna CPF como texto no Sheets para preservar zeros à esquerda. A coleta valida o formato e remove a máscara; não verifica os dígitos verificadores. O número do endereço segue o `int` do modelo atual, portanto `s/n` e `12A` geram erro. A data/hora é convertida para `LocalDate`, descartando o horário.

## Executar pela IDE

1. Use Java 25 e habilite o processamento de anotações do Lombok.
2. Mantenha as credenciais OAuth de aplicativo desktop em `src/main/resources/credentials.json`, com a API do Google Sheets habilitada no projeto Google.
3. Execute o `main` de `com.SheetsQuickstart` com os argumentos:

   ```text
   ID_DA_PLANILHA "'Respostas ao formulário 1'!A1:Z"
   ```

   Use o ID presente entre `/d/` e `/edit` na URL da planilha. O intervalo deve começar na linha do cabeçalho. Para um cabeçalho na linha 5, use `"'Minha aba'!A5:Z" 5` após o ID.

4. Na primeira coleta, autorize no navegador com uma conta que tenha acesso à planilha. O retorno OAuth usa a porta local 8888 e os tokens são reutilizados na pasta `tokens`.

O console mostra somente a quantidade de cadastros e os erros, sem imprimir dados pessoais. Para acessar os objetos, use `result.registers()` no serviço. O Quickstart recebe os parâmetros pelos argumentos acima e usa os demais padrões de `SheetsProperties`; ele não carrega `application.properties`.

## Usar no Spring

Configure `GOOGLE_SHEETS_SPREADSHEET_ID` e, se necessário, `GOOGLE_SHEETS_RANGE`. Injete `RegisterCollectionService` em outro componente e chame `collect()`:

```java
SheetImportResult result = registerCollectionService.collect();
List<Register> registers = result.registers();
```

Configurações disponíveis em `application.properties`: `app.sheets.spreadsheet-id`, `range`, `header-row`, `credentials-path`, `tokens-directory` e `oauth-port`. Para credenciais fora do projeto, use `GOOGLE_SHEETS_CREDENTIALS_PATH=file:C:/caminho/credentials.json`. Ajuste `header-row` para o número real da primeira linha do intervalo, usado no relatório de erros.

A autenticação acontece apenas quando `collect()` é chamado. A aplicação Spring continua usando a configuração PostgreSQL existente. Erros de rede/autorização e cabeçalhos inválidos interrompem a coleta; erros de conteúdo descartam apenas a linha afetada e são retornados no relatório. Confira `errors()` antes de consumir os registros.

## Testes

```powershell
.\mvnw.cmd test "-Dtest=RegisterSheetMapperTests,RegisterCollectionServiceTests"
```

Esses testes não precisam de Google ou PostgreSQL. O teste de contexto Spring já existente depende da configuração do banco.

A leitura usa `ROWS` e `FORMATTED_VALUE`, incluindo tratamento das células finais vazias que a API omite, conforme a [documentação de leitura do Google Sheets](https://developers.google.com/workspace/sheets/api/guides/values).
