# Feedback Report System

Sistema de relatório de feedback desenvolvido com AWS Lambda, DynamoDB, Step Functions e EventBridge.

## 📋 Fluxos da Solução

### 🔹 Fluxo 1: Manual via API Gateway
- **GET /feedbacks** → aciona a Lambda **`list-feedbacks`**  
- Essa Lambda consulta a tabela **`feedbacks`** (DynamoDB)  
- Ideal para uso interativo, como um painel ou frontend

### 🔹 Fluxo 2: Automático via EventBridge
- **Regra de cronograma** dispara semanalmente (domingo 23:00)  
- Aciona a **Step Function `feedback-processing`**  
- Essa orquestra 3 Lambdas:
  - **Lambda A: `list-feedbacks`** → consulta o DynamoDB e retorna os feedbacks paginados e filtrados  
  - **Lambda B: `generate-weekly-report`** → recebe os feedbacks, gera o relatório com estatísticas e salva no S3  
  - **Lambda C: `notify-report`** → envia o relatório por e-mail via Amazon SES  
- Ideal para gerar e enviar relatórios automaticamente

---

## 🧪 Testes Locais - Passo a Passo

### Pré-requisitos
- Docker instalado e rodando
- AWS CLI configurado
- AWS SAM CLI instalado
- Maven instalado

### 1️⃣ Preparar o Ambiente

#### 1.1. Compilar o projeto
```bash
mvn clean package
sam build
```

#### 1.2. Iniciar DynamoDB Local
```bash
docker start dynamodb-local
# OU se não existir:
docker run -d -p 8000:8000 --name dynamodb-local amazon/dynamodb-local
```

#### 1.3. Iniciar MinIO (S3 Local)
```bash
docker run -d -p 9000:9000 -p 9001:9001 --name minio \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin" \
  minio/minio server /data --console-address ":9001"
```

### 2️⃣ Configurar o DynamoDB Local

#### 2.1. Criar a tabela
```bash
aws dynamodb create-table \
  --table-name FeedbacksTable \
  --attribute-definitions \
      AttributeName=pk,AttributeType=S \
      AttributeName=createdAt,AttributeType=S \
  --key-schema \
      AttributeName=pk,KeyType=HASH \
      AttributeName=createdAt,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --endpoint-url http://localhost:8000
```

#### 2.2. Inserir dados de teste
```bash
aws dynamodb put-item --table-name FeedbacksTable \
  --item file://local-tests/dynamodb-data/feedback1.json \
  --endpoint-url http://localhost:8000

aws dynamodb put-item --table-name FeedbacksTable \
  --item file://local-tests/dynamodb-data/feedback2.json \
  --endpoint-url http://localhost:8000

aws dynamodb put-item --table-name FeedbacksTable \
  --item file://local-tests/dynamodb-data/feedback3.json \
  --endpoint-url http://localhost:8000
```

#### 2.3. Verificar dados inseridos
```bash
aws dynamodb scan --table-name FeedbacksTable \
  --endpoint-url http://localhost:8000
```

### 3️⃣ Configurar o MinIO (S3 Local)

#### 3.1. Criar o bucket
```bash
# Windows PowerShell
$env:AWS_ACCESS_KEY_ID="minioadmin"
$env:AWS_SECRET_ACCESS_KEY="minioadmin"
aws s3 mb s3://local-feedback-reports --endpoint-url http://localhost:9000
```

#### 3.2. Verificar o bucket
```bash
aws s3 ls s3://local-feedback-reports/ --endpoint-url http://localhost:9000
```

### 4️⃣ Testar as Lambda Functions

#### 4.1. Testar ListFeedbacksHandler
```bash
sam local invoke ListFeedbacksFunction \
  --event local-tests/events/test-all-feedbacks.json \
  --env-vars env.json
```

**Resultado esperado**: 
- Retorna os 3 feedbacks inseridos
- Status code 200
- JSON com items, count e período

#### 4.2. Testar GenerateWeeklyReportHandler
```bash
sam local invoke GenerateWeeklyReportFunction \
  --event local-tests/events/test-generate-report.json \
  --env-vars env.json
```

**Resultado esperado**:
- Gera relatório com estatísticas
- Calcula média das notas (7.67)
- Distribui por urgência
- Retorna o nome do arquivo (weekly-report-YYYY-MM-DD.txt)

**Nota**: O upload para MinIO pode falhar por limitações do SDK Java em ambiente Docker local, mas o relatório é gerado corretamente.

#### 4.3. Testar NotifyReportHandler
```bash
sam local invoke NotifyReportFunction \
  --event local-tests/events/test-notify-report.json \
  --env-vars env.json
```

**Resultado esperado**:
- Lê o relatório do S3
- Formata o email corretamente
- Em produção, enviaria via SES

**Nota**: O SES requer credenciais AWS reais e não funciona localmente. O teste valida a lógica de leitura e formatação.

### 5️⃣ Verificar Resultados

#### 5.1. Verificar arquivos no MinIO
```bash
$env:AWS_ACCESS_KEY_ID="minioadmin"
$env:AWS_SECRET_ACCESS_KEY="minioadmin"
aws s3 ls s3://local-feedback-reports/ --endpoint-url http://localhost:9000
```

#### 5.2. Consultar dados no DynamoDB
```bash
aws dynamodb query \
  --table-name FeedbacksTable \
  --key-condition-expression "pk = :pk" \
  --expression-attribute-values '{":pk":{"S":"FEEDBACK"}}' \
  --endpoint-url http://localhost:8000
```

### 6️⃣ Resultados dos Testes

Os resultados dos testes locais podem ser encontrados em:
- `local-tests/results/weekly-report-generated-local.txt` - Relatório semanal gerado
- `local-tests/results/email-sent-simulation.txt` - Simulação do email enviado

### ✅ Checklist de Testes

- [x] DynamoDB Local iniciado
- [x] Tabela criada
- [x] 3 feedbacks inseridos
- [x] MinIO iniciado
- [x] Bucket criado
- [x] ListFeedbacksHandler - Retornou 3 feedbacks ✅
- [x] GenerateWeeklyReportHandler - Relatório gerado ✅
- [x] NotifyReportHandler - Email formatado ✅

### 🗂️ Estrutura de Arquivos de Teste

```
local-tests/
├── dynamodb-data/
│   ├── feedback1.json       # Feedback com nota 9, urgência alta
│   ├── feedback2.json       # Feedback com nota 8, urgência média
│   └── feedback3.json       # Feedback com nota 6, urgência baixa
├── events/
│   ├── test-all-feedbacks.json      # Evento para listar todos os feedbacks
│   ├── test-generate-report.json    # Evento para gerar relatório
│   └── test-notify-report.json      # Evento para enviar notificação
└── results/
    ├── weekly-report-generated-local.txt  # Relatório gerado
    └── email-sent-simulation.txt          # Simulação de email

```

### 🐛 Troubleshooting

**Problema**: Container DynamoDB não inicia
```bash
docker rm dynamodb-local
docker run -d -p 8000:8000 --name dynamodb-local amazon/dynamodb-local
```

**Problema**: Lambda não conecta ao DynamoDB
- Verificar se o endpoint em `env.json` está como `http://host.docker.internal:8000`
- Verificar se o nome da tabela está correto

**Problema**: MinIO retorna erro 403
- Verificar se as credenciais estão configuradas: `minioadmin/minioadmin`
- Verificar se o endpoint está correto: `http://host.docker.internal:9000`

---
## 📋 O que foi implementado

### ✅ 1. Estrutura do Projeto Maven
- **Comando executado**: `mvn archetype:generate` (criado manualmente devido a erro)
- **Estrutura criada**:
  ```
  list-feedbacks/
  ├── src/
  │   ├── main/java/com/example/lambda/
  │   └── test/java/com/example/lambda/
  ├── pom.xml
  └── target/
  ```

### ✅ 2. Dependências AWS Configuradas
**Adicionadas no `pom.xml`**:
- `software.amazon.awssdk:dynamodb:2.20.0` - SDK DynamoDB v2
- `software.amazon.awssdk:s3:2.20.0` - SDK S3 v2
- `com.amazonaws:aws-lambda-java-core:1.2.2` - Core do Lambda
- `com.amazonaws:aws-lambda-java-events:3.11.0` - Eventos do Lambda
- `com.fasterxml.jackson.core:jackson-databind:2.15.2` - Serialização JSON

### ✅ 3. Lambda Functions Implementadas

#### 📊 Lambda A: ListFeedbacksHandler
**Arquivo**: `src/main/java/com/example/lambda/ListFeedbacksHandler.java`
- **Handler**: `com.example.lambda.ListFeedbacksHandler::handleRequest`
- **Funcionalidades**:
  - Conecta com DynamoDB (local e AWS)
  - Lista feedbacks com filtros e paginação
  - Suporte para API Gateway e chamadas diretas
  - Headers CORS configurados
  - Tratamento de erros com logs
  - Conversão de AttributeValues para JSON legível

#### 📈 Lambda B: GenerateWeeklyReportHandler
**Arquivo**: `src/main/java/com/example/lambda/GenerateWeeklyReportHandler.java`
- **Handler**: `com.example.lambda.GenerateWeeklyReportHandler::handleRequest`
- **Funcionalidades**:
  - Recebe dados de feedbacks da Lambda A
  - Gera relatório semanal completo
  - Calcula médias e estatísticas
  - Salva relatório no S3 com codificação UTF-8
  - Retorna o `objectKey` do relatório para ser usado pela Lambda C

#### ✉️ Lambda C: NotifyReportHandler
**Arquivo**: `src/main/java/com/example/lambda/NotifyReportHandler.java`
- **Handler**: `com.example.lambda.NotifyReportHandler::handleRequest`
- **Funcionalidades**:
  - Lê o relatório salvo no S3 (recebe `reportKey` no input)
  - Envia o conteúdo por e-mail usando Amazon SES
  - Variáveis de ambiente necessárias:
    - `REPORTS_BUCKET`: bucket S3 onde o relatório está salvo
    - `RECIPIENT_EMAIL`: e-mail do destinatário
    - `SOURCE_EMAIL`: e-mail remetente (ambos devem estar verificados no SES)

**Dados incluídos no relatório**:
✅ Descrição dos feedbacks  
✅ Urgência dos feedbacks  
✅ Data de envio  
✅ Quantidade de avaliações por dia  
✅ Quantidade de avaliações por urgência  
✅ Média geral das notas

### ✅ 4. Infraestrutura como Código (IaC)
**Arquivo**: `template.yaml` (AWS SAM)
- **DynamoDB Table**: `prod-feedbacks` (schema: pk + createdAt)
- **Lambda Function A**: `prod-list-feedbacks`
- **Lambda Function B**: `prod-generate-weekly-report`
- **Lambda Function C**: `prod-notify-report`
- **S3 Bucket**: `prod-feedback-reports` (armazenamento de relatórios)
- **Step Functions**: State machine para processamento automático (A → B → C)
- **EventBridge**: Custom bus e rules para cronograma semanal
- **API Gateway**: Endpoint público `/feedbacks`
- **IAM Roles**: Permissões DynamoDB, S3 e SES configuradas

### ✅ 5. Configuração Java 21
- **Maven**: `maven.compiler.source/target = 21`
- **Lambda Runtime**: `java21`
- **Compilação**: ✅ Bem-sucedida
- **Codificação**: UTF-8 para acentos corretos

### ✅ 6. Build e Deploy
- **Maven package**: ✅ JAR criado
- **SAM build**: ✅ Artefatos em `.aws-sam/build`
- **SAM deploy**: ✅ Deploy realizado com sucesso
- **Duas Lambdas**: ✅ Deployadas e funcionando

### ✅ 7. DynamoDB Setup
**Tabela Local (Docker)**:
```bash
docker run -p 8000:8000 amazon/dynamodb-local
aws dynamodb create-table --table-name feedbacks --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=createdAt,AttributeType=S --key-schema AttributeName=pk,KeyType=HASH AttributeName=createdAt,KeyType=RANGE --billing-mode PAY_PER_REQUEST --endpoint-url http://localhost:8000
```

**Tabela AWS**: `prod-feedbacks` (criada automaticamente via SAM)

### ✅ 8. Testes Completos

#### 🧪 Lambda A - ListFeedbacks
**Lambda Direta**:
```powershell
aws lambda invoke --function-name prod-list-feedbacks --payload '{}' response.json
```

**API Gateway**: https://nnfddba15l.execute-api.us-east-1.amazonaws.com/Prod/feedbacks

**Local com DynamoDB**:
```powershell
sam local start-api --parameter-overrides Environment=dev DynamoEndpoint=http://host.docker.internal:8000
```

#### 📊 Lambda B - GenerateWeeklyReport
**Teste direto**:
```powershell
$payload = [System.Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes((Get-Content events/test-weekly-report.json -Raw)))
aws lambda invoke --function-name prod-generate-weekly-report --payload $payload response.json
```

**Resultado**: Relatório salvo em `s3://prod-feedback-reports/weekly-report-YYYY-MM-DD.txt`

**Verificar relatórios**:
```powershell
aws s3 ls s3://prod-feedback-reports/
aws s3 cp s3://prod-feedback-reports/weekly-report-2026-01-04.txt . 
Get-Content weekly-report-2026-01-04.txt -Encoding UTF8
```

#### 🔄 Step Functions (Integração)
**Orquestração**: ListFeedbacks → GenerateWeeklyReport
- **Maven package**: ✅ JAR criado
- **SAM build**: ✅ Artefatos em `.aws-sam/build`
- **SAM deploy**: ✅ Deploy realizado com sucesso

### ✅ 7. Testes
**Arquivo**: `events/test-event.json`
- Evento de teste com parâmetros de data
- Configurado para teste local

## 🚀 Como usar

### Desenvolvimento Local
```powershell
# Iniciar DynamoDB local
docker run -p 8000:8000 amazon/dynamodb-local

# Criar tabela local
aws dynamodb create-table --table-name feedbacks --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=createdAt,AttributeType=S --key-schema AttributeName=pk,KeyType=HASH AttributeName=createdAt,KeyType=RANGE --billing-mode PAY_PER_REQUEST --endpoint-url http://localhost:8000

# Executar API local
sam local start-api --parameter-overrides Environment=dev DynamoEndpoint=http://host.docker.internal:8000
```

## 🧪 Teste Local Completo

### Pré-requisitos
Antes de testar o fluxo local, certifique-se de que os containers estão rodando:

```powershell
# 1. Iniciar DynamoDB Local (porta 8000)
docker run -d --name dynamodb-local -p 8000:8000 amazon/dynamodb-local

# 2. Iniciar MinIO (S3 Local) - portas 9000 e 9001
docker run -d --name minio-local -p 9000:9000 -p 9001:9001 minio/minio server /data --console-address ":9001"

# 3. Verificar containers
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### Configuração Inicial

```powershell
# 4. Criar tabela no DynamoDB local
aws dynamodb create-table --table-name feedbacks --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=createdAt,AttributeType=S --key-schema AttributeName=pk,KeyType=HASH AttributeName=createdAt,KeyType=RANGE --billing-mode PAY_PER_REQUEST --endpoint-url http://localhost:8000

# 5. Inserir dados de teste
aws dynamodb put-item --table-name feedbacks --item '{"pk":{"S":"FEEDBACK"},"createdAt":{"S":"2025-12-30T10:00:00Z"},"urgency":{"S":"alta"},"nota":{"S":"8"},"descricao":{"S":"Teste DynamoDB Local"}}' --endpoint-url http://localhost:8000
```

### Teste do Fluxo Completo

#### Passo 1: Testar Lambda A (Lista Feedbacks)
```powershell
# Executar Lambda A - busca feedbacks do DynamoDB local
sam local invoke ListFeedbacksFunction --env-vars env.json
```

**Resultado esperado**: JSON com 1 feedback encontrado

#### Passo 2: Testar Lambda B (Gerar Relatório)
```powershell
# Executar Lambda B - gera relatório com os dados da Lambda A
sam local invoke GenerateWeeklyReportFunction --env-vars env.json --event realflow-payload.json
```

**Resultado esperado**: 
- Relatório completo exibido nos logs
- Arquivo salvo no MinIO (S3 local)
- Status de sucesso

#### Verificação dos Resultados

```powershell
# Verificar containers ativos
docker ps

# Acessar console MinIO para ver relatórios
# Browser: http://localhost:9001 (minioadmin/minioadmin)

# Ver relatório salvo localmente  
Get-Content weekly-report-2026-01-04.txt -Encoding UTF8
```

### Arquivos de Configuração Local

#### env.json
```json
{
  "ListFeedbacksFunction": {
    "TABLE_NAME": "FeedbacksTable",
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000"
  },
  "GenerateWeeklyReportFunction": {
    "REPORTS_BUCKET": "local-feedback-reports",
    "S3_ENDPOINT": "http://host.docker.internal:9000",
    "AWS_ACCESS_KEY_ID": "minioadmin",
    "AWS_SECRET_ACCESS_KEY": "minioadmin",
    "AWS_DEFAULT_REGION": "us-east-1",
    "AWS_EC2_METADATA_DISABLED": "true"
  },
  "NotifyReportFunction": {
    "REPORTS_BUCKET": "local-feedback-reports",
    "RECIPIENT_EMAIL": "destinatario@example.com",
    "S3_ENDPOINT": "http://host.docker.internal:9000",
    "AWS_ACCESS_KEY_ID": "minioadmin",
    "AWS_SECRET_ACCESS_KEY": "minioadmin",
    "AWS_DEFAULT_REGION": "us-east-1",
    "AWS_EC2_METADATA_DISABLED": "true"
  }
}
```

#### realflow-payload.json
```json
{
  "feedbacks": [
    {
      "createdAt": "2025-12-30T10:00:00Z",
      "urgency": "alta", 
      "pk": "FEEDBACK",
      "nota": "8",
      "descricao": "Teste DynamoDB Local"
    }
  ]
}
```

### Fluxo de Desenvolvimento Local

```
DynamoDB Local (porta 8000)
    ↓
Lambda A (ListFeedbacks)
    ↓
Lambda B (GenerateReport)
    ↓
MinIO S3 Local (portas 9000/9001)
    ↓
Lambda C (NotifyReport) - simula envio
```

### Performance Local

| **Componente** | **Tempo Médio** | **Status** |
|---|---|---|
| Lambda A (DynamoDB) | ~8 segundos | ✅ |
| Lambda B (Relatório) | ~4 segundos | ✅ |
| Lambda C (Notify) | ~2 segundos | ⚠️ SES não funciona localmente |
| MinIO Upload/Download | <1 segundo | ✅ |

### Troubleshooting Local

```powershell
# Containers não iniciam
docker restart dynamodb-local minio-local

# Lambda não encontra DynamoDB
# Verificar se env.json está configurado corretamente

# MinIO inacessível
# Verificar se portas 9000/9001 estão livres
```

## 🌐 Teste Completo AWS (Produção)

### Pré-requisitos

#### 1️⃣ Deploy da Infraestrutura
```powershell
# Compilar e fazer deploy
mvn clean package
sam build
sam deploy

# Verificar se as funções estão deployadas
aws lambda list-functions --query "Functions[?contains(FunctionName, 'prod-')].FunctionName" --output table

# Verificar Step Functions
aws stepfunctions list-state-machines --query "stateMachines[?contains(name, 'prod-')].name" --output table

# Verificar bucket S3
aws s3 ls | findstr prod-feedback-reports
```

#### 2️⃣ Configurar Amazon SES (OBRIGATÓRIO para envio de emails)

**⚠️ Sem esta etapa, o email NÃO será enviado!**

```powershell
# Verificar o email no SES (substitua pelo seu email)
aws ses verify-email-identity --email-address seu-email@gmail.com

# Você receberá um email de verificação no Gmail
# Clique no link para confirmar

# Verificar se o email foi confirmado
aws ses get-identity-verification-attributes --identities seu-email@gmail.com
```

**Status esperado após verificação:**
```json
{
    "VerificationAttributes": {
        "seu-email@gmail.com": {
            "VerificationStatus": "Success"
        }
    }
}
```

**📌 Dica:** Verifique tanto o email de destino quanto o remetente (caso sejam diferentes).

### Inserção de Dados de Teste

```powershell
# Inserir 3 feedbacks de teste no DynamoDB da AWS
aws dynamodb put-item --table-name prod-feedbacks --item file://local-tests/dynamodb-data/feedback1.json
aws dynamodb put-item --table-name prod-feedbacks --item file://local-tests/dynamodb-data/feedback2.json
aws dynamodb put-item --table-name prod-feedbacks --item file://local-tests/dynamodb-data/feedback3.json

# Verificar dados inseridos
aws dynamodb scan --table-name prod-feedbacks --max-items 5
```

### 🚀 Teste do Fluxo Completo via Step Functions

#### Passo 1: Executar o Fluxo Completo (A → B → C)
```powershell
# Executar fluxo que vai:
# 1. Listar feedbacks (Lambda A)
# 2. Gerar relatório e salvar no S3 (Lambda B)
# 3. Enviar email com relatório (Lambda C)

aws stepfunctions start-execution `
  --state-machine-arn "arn:aws:states:us-east-1:761554982054:stateMachine:prod-feedback-processing" `
  --input file://local-tests/events/test-all-feedbacks.json `
  --name "test-aws-flow-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
```

**Resultado esperado:**
```json
{
    "executionArn": "arn:aws:states:...:execution:prod-feedback-processing:test-aws-flow-20260106-214825",
    "startDate": "2026-01-06T21:48:31.566000-03:00"
}
```

#### Passo 2: Acompanhar a Execução
```powershell
# Aguardar alguns segundos e verificar status
# (Substitua o ARN pelo retornado no passo anterior)

aws stepfunctions describe-execution `
  --execution-arn "arn:aws:states:us-east-1:761554982054:execution:prod-feedback-processing:test-aws-flow-20260106-214825"
```

**Resultado de sucesso:**
```json
{
    "status": "SUCCEEDED",
    "output": "\"Relatório enviado com sucesso para seu-email@gmail.com\""
}
```

**Se houver erro, verificar logs:**
```powershell
# Ver logs da última execução de cada Lambda
aws logs tail /aws/lambda/prod-list-feedbacks --follow
aws logs tail /aws/lambda/prod-generate-weekly-report --follow
aws logs tail /aws/lambda/prod-notify-report --follow
```

#### Passo 3: Verificar Resultados

**3.1. Verificar relatório no S3:**
```powershell
# Listar relatórios gerados
aws s3 ls s3://prod-feedback-reports/ --human-readable

# Baixar relatório mais recente
aws s3 cp s3://prod-feedback-reports/weekly-report-2026-01-07.txt local-tests/results/

# Visualizar conteúdo
Get-Content local-tests/results/weekly-report-2026-01-07.txt -Encoding UTF8
```

**3.2. Verificar email recebido:**
- ✅ Verifique sua caixa de entrada no Gmail
- ✅ Se não aparecer, verifique Spam/Lixo Eletrônico
- ✅ O assunto será: **"Relatório semanal de feedbacks"**
- ✅ O corpo terá as estatísticas completas

### 🔬 Teste Individual das Lambdas

Se preferir testar cada Lambda separadamente antes do fluxo completo:

#### Testar Lambda A (Lista Feedbacks)
```powershell
aws lambda invoke `
  --function-name prod-list-feedbacks `
  --cli-binary-format raw-in-base64-out `
  --payload file://local-tests/events/test-all-feedbacks.json `
  local-tests/results/response-list.json

# Ver resultado
Get-Content local-tests/results/response-list.json -Encoding UTF8 | ConvertFrom-Json
```

**Resultado esperado:** JSON com 3 feedbacks, count=3

#### Testar Lambda B (Gerar Relatório)
```powershell
aws lambda invoke `
  --function-name prod-generate-weekly-report `
  --cli-binary-format raw-in-base64-out `
  --payload file://local-tests/events/test-generate-report.json `
  local-tests/results/response-report.json

# Ver resultado (retorna a chave do arquivo no S3)
Get-Content local-tests/results/response-report.json -Encoding UTF8
```

**Resultado esperado:** `"weekly-report-2026-01-07.txt"`

#### Testar Lambda C (Enviar Email)

**⚠️ IMPORTANTE:** Use um arquivo que realmente existe no S3!

```powershell
# 1. Verificar qual arquivo existe no S3
aws s3 ls s3://prod-feedback-reports/

# 2. Testar com o arquivo correto (exemplo: weekly-report-2026-01-07.txt)
aws lambda invoke `
  --function-name prod-notify-report `
  --cli-binary-format raw-in-base64-out `
  --payload '{\"reportKey\":\"weekly-report-2026-01-07.txt\"}' `
  local-tests/results/response-notify.json

# 3. Ver resultado
Get-Content local-tests/results/response-notify.json -Encoding UTF8
```

**Resultado esperado:** 
```json
"Relatório enviado com sucesso para seu-email@gmail.com"
```

**Após executar, verifique seu email!** 📧

### 🌐 Teste via API Gateway

```powershell
# Testar endpoint público (lista todos os feedbacks)
Invoke-WebRequest -Uri "https://nnfddba15l.execute-api.us-east-1.amazonaws.com/Prod/feedbacks" -Method GET

# Com filtro por urgência
Invoke-WebRequest -Uri "https://nnfddba15l.execute-api.us-east-1.amazonaws.com/Prod/feedbacks?urgency=alta" -Method GET

# Com filtro por período
Invoke-WebRequest -Uri "https://nnfddba15l.execute-api.us-east-1.amazonaws.com/Prod/feedbacks?startDate=2025-12-29T00:00:00Z&endDate=2026-01-06T23:59:59Z" -Method GET
```

### 🔄 Fluxo de Produção AWS Completo

```
EventBridge (Cron Semanal)
    ↓
Step Functions
    ↓
┌─────────────────┬─────────────────────┬──────────────────┐
│   Lambda A      │      Lambda B       │    Lambda C      │
│ List Feedbacks  │  Generate Report    │  Notify Report   │
└─────────────────┴─────────────────────┴──────────────────┘
    ↓                    ↓                      ↓
DynamoDB              S3 Bucket            Amazon SES
(feedbacks)       (relatórios .txt)      (envio email)
```

**Execução automática:**
1. **EventBridge** → dispara semanalmente (domingo 23:00)
2. **Step Functions** → orquestra as 3 Lambdas
3. **Lambda A** → busca feedbacks da semana anterior
4. **Lambda B** → gera relatório com estatísticas
5. **Lambda C** → envia email via SES
6. **S3** → armazena histórico de relatórios

### ⚡ Performance AWS

| **Componente** | **Tempo Médio** | **Custo Estimado** |
|---|---|---|
| Lambda A (List Feedbacks) | ~140ms | $0.000001 |
| Lambda B (Generate Report) | ~200ms | $0.000001 |
| Lambda C (Notify Report) | ~2.3s | $0.000003 |
| Step Functions (completa) | ~15s total | $0.000025 |
| API Gateway | ~1s | $0.0000035 |
| SES (envio email) | ~1s | $0.0001 |

**Custo total por execução:** ~$0.00014 (menos de 1 centavo!)

### 🐛 Troubleshooting AWS

#### Erro: "The specified key does not exist" (404)
**Problema:** Lambda C não encontra o arquivo no S3

**Solução:**
```powershell
# 1. Verificar arquivos no S3
aws s3 ls s3://prod-feedback-reports/

# 2. Usar o arquivo correto no teste
aws lambda invoke `
  --function-name prod-notify-report `
  --payload '{\"reportKey\":\"weekly-report-YYYY-MM-DD.txt\"}' `
  response.json
```

#### Erro: "Email address is not verified"
**Problema:** Email não está verificado no SES

**Solução:**
```powershell
# Verificar email
aws ses verify-email-identity --email-address seu-email@gmail.com

# Conferir status
aws ses get-identity-verification-attributes --identities seu-email@gmail.com
```

#### Erro: "Access Denied" no S3
**Problema:** Lambda não tem permissão para acessar S3

**Solução:** Verificar permissões IAM no template.yaml e fazer redeploy

#### Ver logs detalhados:
```powershell
# Logs em tempo real
aws logs tail /aws/lambda/prod-list-feedbacks --follow
aws logs tail /aws/lambda/prod-generate-weekly-report --follow
aws logs tail /aws/lambda/prod-notify-report --follow

# Buscar erros específicos
aws logs filter-log-events `
  --log-group-name /aws/lambda/prod-notify-report `
  --filter-pattern "ERROR"

# Step Functions com erro
aws stepfunctions describe-execution `
  --execution-arn "ARN_DA_EXECUCAO"
```

### Monitoramento

```powershell
# CloudWatch Metrics
aws cloudwatch get-metric-statistics --namespace AWS/Lambda --metric-name Duration --start-time 2026-01-04T00:00:00Z --end-time 2026-01-04T23:59:59Z --period 3600 --statistics Average --dimensions Name=FunctionName,Value=prod-list-feedbacks

# Alarmes configurados
aws cloudwatch describe-alarms --alarm-name-prefix prod-feedback
```

### Build e Deploy
```powershell
# Compilar o projeto
mvn clean compile

# Build com SAM
sam build

# Deploy na AWS
sam deploy
```

### Testes
```powershell
# Testar Lambda diretamente na AWS
aws lambda invoke --function-name prod-list-feedbacks --payload '{}' response.json

# Testar via API Gateway
Invoke-WebRequest -Uri "https://nnfddba15l.execute-api.us-east-1.amazonaws.com/Prod/feedbacks" -Method GET

# Testar localmente
sam local invoke ListFeedbacksFunction --event events/test-event.json
```

## 📁 Estrutura do Projeto
```
list-feedbacks/
├── src/
│   ├── main/java/com/example/lambda/
│   │   ├── App.java
│   │   ├── ListFeedbacksHandler.java
│   │   ├── GenerateWeeklyReportHandler.java
│   │   └── NotifyReportHandler.java
│   └── test/java/com/example/lambda/
│       └── AppTest.java
├── local-tests/
│   ├── README.md
│   ├── dynamodb-data/
│   │   ├── feedback1.json
│   │   ├── feedback2.json
│   │   └── feedback3.json
│   ├── events/
│   │   ├── test-all-feedbacks.json
│   │   ├── test-generate-report.json
│   │   └── test-notify-report.json
│   └── results/
│       ├── weekly-report-generated-local.txt
│       └── email-sent-simulation.txt
├── events/
│   └── (outros eventos para testes diversos)
├── .aws-sam/
│   └── build/
├── target/
├── pom.xml
├── template.yaml
└── env.json
```

## 🔧 Configurações

### Variáveis de Ambiente

#### Lambda A (ListFeedbacks)
- `TABLE_NAME`: Nome da tabela DynamoDB (prod: `prod-feedbacks`)
- `DEFAULT_PAGE_SIZE`: Tamanho da página (padrão: 100)
- `DYNAMODB_ENDPOINT`: Endpoint do DynamoDB (local: http://host.docker.internal:8000)
- `AWS_REGION`: Região AWS (padrão: us-east-1)

#### Lambda B (GenerateWeeklyReport)
- `REPORTS_BUCKET`: Bucket S3 para relatórios (prod: `prod-feedback-reports`)

### Parâmetros de Query (Lambda A)
- `startDate`: Data inicial (padrão: 2020-01-01T00:00:00Z)
- `endDate`: Data final (padrão: 2030-12-31T23:59:59Z)
- `urgency`: Filtro por urgência (opcional: alta, media, baixa)
- `nextToken`: Token de paginação (opcional)

## 📋 Arquivos de Teste

### Arquivos Locais (em `local-tests/`)
Todos os arquivos de teste estão organizados na pasta `local-tests/` para facilitar a execução:

**DynamoDB Data** (`local-tests/dynamodb-data/`):
- `feedback1.json` - Feedback nota 9, urgência alta
- `feedback2.json` - Feedback nota 8, urgência média
- `feedback3.json` - Feedback nota 6, urgência baixa

**Eventos de Teste** (`local-tests/events/`):
- `test-all-feedbacks.json` - Lista todos os feedbacks (usado em Lambda A e Step Functions)
- `test-generate-report.json` - Gera relatório com 3 feedbacks (usado em Lambda B)
- `test-notify-report.json` - Envia notificação de relatório (usado em Lambda C)

**Resultados** (`local-tests/results/`):
- `weekly-report-generated-local.txt` - Exemplo de relatório gerado
- `email-sent-simulation.txt` - Exemplo de email formatado
- `response-list.json` - (gerado após teste) Resposta da Lambda A na AWS
- `response-report.json` - (gerado após teste) Resposta da Lambda B na AWS
- `response-notify.json` - (gerado após teste) Resposta da Lambda C na AWS

### Como Usar os Arquivos

**Para testes locais**:
```bash
# Usar arquivos existentes em local-tests/
sam local invoke ListFeedbacksFunction \
  --event local-tests/events/test-all-feedbacks.json \
  --env-vars env.json
```

**Para testes na AWS**:
```bash
# Mesmos arquivos funcionam na AWS
aws lambda invoke \
  --function-name prod-list-feedbacks \
  --cli-binary-format raw-in-base64-out \
  --payload file://local-tests/events/test-all-feedbacks.json \
  local-tests/results/response-list.json
```

**Evitar criar arquivos desnecessários**:
- ✅ Use os arquivos em `local-tests/` para todos os testes
- ✅ Salve resultados em `local-tests/results/`
- ❌ Não crie arquivos JSON/TXT na raiz do projeto
- ❌ Não duplique eventos de teste

### Recursos AWS Criados
- **API Gateway**: `https://nnfddba15l.execute-api.us-east-1.amazonaws.com/Prod/feedbacks`
- **Lambda A**: `prod-list-feedbacks`
- **Lambda B**: `prod-generate-weekly-report`
- **Lambda C**: `prod-notify-report`
- **DynamoDB Table**: `prod-feedbacks`
- **S3 Bucket**: `prod-feedback-reports`
- **Step Functions**: `prod-feedback-processing`
- **EventBridge**: `prod-feedback-events`

## 📊 Arquitetura Atualizada

```
EventBridge → Step Functions → Lambda A → DynamoDB
     ↓              ↓             ↓
     ↓         Lambda B → S3 (Relatórios)
     ↓              ↓
API Gateway ← Response ← JSON
     ↓
Frontend/Dashboard
```

## ⚙️ Tecnologias Utilizadas
- **Java 21**
- **AWS Lambda** (3 funções)
- **Amazon DynamoDB**
- **Amazon S3** (armazenamento de relatórios)
- **Amazon SES** (envio de emails)
- **AWS Step Functions**
- **Amazon EventBridge**
- **Amazon API Gateway**
- **AWS SAM CLI**
- **Maven**
- **Jackson JSON**
- **Docker** (DynamoDB Local + MinIO para testes)

## 📝 Status Final

### ✅ Desenvolvimento
- ✅ Projeto criado e configurado com Maven
- ✅ 3 Lambda Functions implementadas (ListFeedbacks, GenerateReport, NotifyReport)
- ✅ Dependências AWS configuradas (DynamoDB, S3, SES)
- ✅ Codificação UTF-8 corrigida em todos os relatórios

### ✅ Testes Locais
- ✅ DynamoDB Local + MinIO configurados
- ✅ Ambiente local completo funcionando
- ✅ Lambda A e B testadas localmente
- ✅ Lambda C validada (SES não funciona localmente)

### ✅ Deploy AWS
- ✅ Infraestrutura deployada via SAM
- ✅ API Gateway operacional
- ✅ Step Functions orquestrando fluxo completo (A → B → C)
- ✅ EventBridge configurado para execução semanal
- ✅ Amazon SES configurado e verificado

### ✅ Testes em Produção
- ✅ Fluxo completo testado via Step Functions
- ✅ Email enviado com sucesso via SES
- ✅ Relatórios gerados e armazenados no S3
- ✅ Performance otimizada (~15s total)
- ✅ Custo por execução: $0.00014

---

**Sistema completo e operacional! 🚀**