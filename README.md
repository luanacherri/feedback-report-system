# Feedback Report System

Sistema de relatório de feedback desenvolvido com AWS Lambda, DynamoDB, Step Functions e EventBridge.

## � Fluxos da Solução

### 🔹 Fluxo 1: Manual via API Gateway
- **GET /feedbacks** → aciona a Lambda **`list-feedbacks`**  
- Essa Lambda consulta a tabela **`feedbacks`** (DynamoDB)  
- Ideal para uso interativo, como um painel ou frontend

### 🔹 Fluxo 2: Automático via EventBridge
- **Regra de cronograma** dispara semanalmente  
- Aciona a **Step Function `feedback-processing`**  
- Essa orquestra:
  - **Lambda A: `list-feedbacks`** → consulta o DynamoDB e retorna os feedbacks paginados e filtrados  
  - **Lambda B: `generate-weekly-report`** → recebe os feedbacks, gera o relatório com médias semanais e salva no S3  
  - **Lambda C: `notify-report`** → envia o relatório por e-mail (via SES ou SNS)  
- Ideal para gerar e enviar relatórios automaticamente

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
  - Atende todos os requisitos obrigatórios

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
- **S3 Bucket**: `prod-feedback-reports` (armazenamento de relatórios)
- **Step Functions**: State machine para processamento automático
- **EventBridge**: Custom bus e rules para cronograma
- **API Gateway**: Endpoint público `/feedbacks`
- **IAM Roles**: Permissões DynamoDB e S3 configuradas

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
    "TABLE_NAME": "feedbacks",
    "DYNAMODB_ENDPOINT": "http://host.docker.internal:8000"
  },
  "GenerateWeeklyReportFunction": {
    "REPORTS_BUCKET": "local-feedback-reports",
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

### Fluxo de Desenvolvimento

1. **DynamoDB Local** (porta 8000) ← dados persistidos
2. **Lambda A** ← busca feedbacks do DynamoDB 
3. **Lambda B** ← recebe dados da Lambda A
4. **MinIO** (portas 9000/9001) ← salva relatórios
5. **Arquivo Local** ← backup do relatório

### Performance Local

| **Componente** | **Tempo Médio** | **Status** |
|---|---|---|
| Lambda A (DynamoDB) | ~8 segundos | ✅ |
| Lambda B (Relatório) | ~4 segundos | ✅ |
| Fluxo Completo | ~12 segundos | ✅ |
| MinIO Upload | <1 segundo | ✅ |

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
Certifique-se de que a infraestrutura está deployada:

```powershell
# 1. Verificar se as funções estão deployadas
aws lambda list-functions --query "Functions[?contains(FunctionName, 'prod-')].FunctionName" --output table

# 2. Verificar Step Functions
aws stepfunctions list-state-machines --query "stateMachines[?contains(name, 'prod-')].name" --output table

# 3. Verificar bucket S3
aws s3 ls | findstr prod-feedback-reports
```

### Inserção de Dados de Teste

```powershell
# 4. Inserir dados de teste no DynamoDB da AWS
aws dynamodb put-item --table-name prod-feedbacks --item file://feedback1.json
aws dynamodb put-item --table-name prod-feedbacks --item file://feedback2.json
aws dynamodb put-item --table-name prod-feedbacks --item file://feedback3.json

# 5. Verificar dados inseridos
aws dynamodb scan --table-name prod-feedbacks --max-items 5
```

### Teste do Fluxo Completo via Step Functions

#### Passo 1: Executar Step Functions (Fluxo A → B)
```powershell
# Criar arquivo de entrada com período de datas
# step-input.json:
{
  "startDate": "2025-01-01T00:00:00Z",
  "endDate": "2027-12-31T23:59:59Z"
}

# Executar fluxo completo
aws stepfunctions start-execution --state-machine-arn "arn:aws:states:us-east-1:761554982054:stateMachine:prod-feedback-processing" --input file://step-input.json --name "test-aws-flow-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
```

#### Passo 2: Acompanhar Execução
```powershell
# Verificar status (substituir ARN pela execução criada)
aws stepfunctions describe-execution --execution-arn "arn:aws:states:us-east-1:761554982054:execution:prod-feedback-processing:test-aws-flow-XXXXXXXX"

# Ver histórico detalhado
aws stepfunctions get-execution-history --execution-arn "arn:aws:states:us-east-1:761554982054:execution:prod-feedback-processing:test-aws-flow-XXXXXXXX" --max-items 10
```

#### Passo 3: Verificar Relatório Gerado
```powershell
# Listar relatórios no bucket S3
aws s3 ls s3://prod-feedback-reports/ --human-readable

# Baixar relatório mais recente
aws s3 cp s3://prod-feedback-reports/weekly-report-2026-01-04.txt aws-final-report.txt

# Visualizar conteúdo (com acentos corretos)
Get-Content aws-final-report.txt -Encoding UTF8
```

### Teste Individual das Lambdas

#### Testar Lambda A (Lista Feedbacks)
```powershell
# Teste direto da Lambda A
aws lambda invoke --function-name prod-list-feedbacks --cli-binary-format raw-in-base64-out --payload file://lambda-test.json response-a.json

# Ver resultado
Get-Content response-a.json -Encoding UTF8
```

#### Testar Lambda B (Gerar Relatório)
```powershell
# Criar payload com dados da Lambda A
# aws-flow-payload.json com feedbacks reais

# Teste direto da Lambda B
aws lambda invoke --function-name prod-generate-weekly-report --cli-binary-format raw-in-base64-out --payload file://aws-flow-payload.json response-b.json

# Ver resultado
Get-Content response-b.json -Encoding UTF8
```

### Teste via API Gateway

```powershell
# Testar endpoint público
Invoke-WebRequest -Uri "https://nnfddba15l.execute-api.us-east-1.amazonaws.com/Prod/feedbacks" -Method GET

# Com parâmetros de filtro
Invoke-WebRequest -Uri "https://nnfddba15l.execute-api.us-east-1.amazonaws.com/Prod/feedbacks?urgency=alta&startDate=2025-01-01T00:00:00Z" -Method GET
```

### Arquivos de Configuração AWS

#### step-input.json
```json
{
  "startDate": "2025-01-01T00:00:00Z",
  "endDate": "2027-12-31T23:59:59Z"
}
```

#### lambda-test.json
```json
{
  "startDate": "2025-01-01T00:00:00Z",
  "endDate": "2027-12-31T23:59:59Z"
}
```

### Fluxo de Produção AWS

1. **EventBridge** → agenda execução semanal
2. **Step Functions** → orquestra Lambda A → Lambda B
3. **Lambda A** → busca feedbacks do DynamoDB  
4. **Lambda B** → gera relatório e salva no S3
5. **S3** → armazena relatórios com versionamento

### Performance AWS

| **Componente** | **Tempo Médio** | **Custo** |
|---|---|---|
| Lambda A (DynamoDB) | ~0.14 segundos | $0.000001 |
| Lambda B (Relatório) | ~0.20 segundos | $0.000001 |
| Step Functions | ~8.8 segundos total | $0.000025 |
| API Gateway | ~1 segundo | $0.0000035 |

### Troubleshooting AWS

```powershell
# Logs das Lambdas
aws logs describe-log-groups --log-group-name-prefix /aws/lambda/prod-

# Logs específicos
aws logs tail /aws/lambda/prod-list-feedbacks --follow

# Step Functions com erro
aws stepfunctions describe-execution --execution-arn "ARN_DA_EXECUCAO"

# Verificar permissões IAM
aws iam get-role-policy --role-name feedback-report-ListFeedbacksFunctionRole-XXXXX --policy-name root
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
│   │   └── GenerateWeeklyReportHandler.java
│   └── test/java/com/example/lambda/
│       └── AppTest.java
├── events/
│   ├── test-event.json
│   ├── test-event-clean.json
│   └── test-weekly-report.json
├── .aws-sam/
│   └── build/
├── target/
├── pom.xml
├── template.yaml
├── feedback1.json
├── feedback2.json
├── feedback3.json
├── test-payload.json
├── weekly-report-2026-01-04.txt
└── weekly-report-fixed.txt
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

### Recursos AWS Criados
- **API Gateway**: `https://nnfddba15l.execute-api.us-east-1.amazonaws.com/Prod/feedbacks`
- **Lambda A**: `prod-list-feedbacks`
- **Lambda B**: `prod-generate-weekly-report`
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
- **AWS Lambda** (2 funções)
- **DynamoDB**
- **S3** (armazenamento de relatórios)
- **Step Functions**
- **EventBridge**
- **API Gateway**
- **AWS SAM**
- **Maven**
- **Jackson JSON**
- **Docker (DynamoDB local)**

## 📝 Status Final
- ✅ Projeto criado e configurado
- ✅ Dependências instaladas (DynamoDB + S3)
- ✅ Lambda A: ListFeedbacks implementada e testada
- ✅ Lambda B: GenerateWeeklyReport implementada e testada
- ✅ Infraestrutura completa deployada
- ✅ Build bem-sucedido (ambas as funções)
- ✅ Deploy realizado com sucesso
- ✅ Testes locais funcionando
- ✅ Testes em produção funcionando
- ✅ API Gateway operacional
- ✅ DynamoDB populado com dados de teste
- ✅ S3 bucket funcionando com relatórios
- ✅ Codificação UTF-8 corrigida
- ✅ Todos os requisitos do relatório atendidos
- ✅ Step Functions orquestrando o fluxo completo
- ✅ Repositório Git criado e sincronizado
- ✅ **Ambiente local completo (DynamoDB + MinIO)**
- ✅ **Fluxo local testado: A → B → Relatório (~12s)**
- ✅ **Containers Docker funcionando**
- ✅ **Teste AWS completo via Step Functions**
- ✅ **Fluxo AWS testado: A → B → S3 (~8.8s)**
- ✅ **Performance otimizada (local e AWS)**
- ✅ **Tratamento robusto de buckets (local/AWS)**

## 🚨 Troubleshooting

### Erro Maven archetype
**Problema**: `mvn archetype:generate` falhou
**Solução**: Estrutura criada manualmente

### Erro DynamoDB Schema
**Problema**: Schema de chaves incompatível
**Solução**: Tabela recriada com pk + createdAt

### API Gateway 500 Error
**Problema**: Formato de resposta incompatível
**Solução**: Handler adaptado para detectar origem da chamada (API Gateway vs Lambda direta)

### PowerShell vs Bash
**Problema**: Comandos diferentes no Windows
**Solução**: Comandos documentados em PowerShell