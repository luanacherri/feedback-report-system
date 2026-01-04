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
- `com.amazonaws:aws-lambda-java-core:1.2.2` - Core do Lambda
- `com.amazonaws:aws-lambda-java-events:3.11.0` - Eventos do Lambda
- `com.fasterxml.jackson.core:jackson-databind:2.15.2` - Serialização JSON

### ✅ 3. Handler Lambda Implementado
**Arquivo**: `src/main/java/com/example/lambda/ListFeedbacksHandler.java`
- **Handler**: `com.example.lambda.ListFeedbacksHandler::handleRequest`
- **Funcionalidades**:
  - Conecta com DynamoDB (local e AWS)
  - Lista feedbacks com filtros e paginação
  - Suporte para API Gateway e chamadas diretas
  - Headers CORS configurados
  - Tratamento de erros com logs
  - Conversão de AttributeValues para JSON legível

### ✅ 4. Infraestrutura como Código (IaC)
**Arquivo**: `template.yaml` (AWS SAM)
- **DynamoDB Table**: `prod-feedbacks` (schema: pk + createdAt)
- **Lambda Function**: `prod-list-feedbacks`
- **Step Functions**: State machine para processamento
- **EventBridge**: Custom bus e rules
- **API Gateway**: Endpoint `/feedbacks`
- **IAM Roles**: Permissões DynamoDB configuradas

### ✅ 5. Configuração Java 21
- **Maven**: `maven.compiler.source/target = 21`
- **Lambda Runtime**: `java21`
- **Compilação**: ✅ Bem-sucedida

### ✅ 6. Build e Deploy
- **Maven package**: ✅ JAR criado
- **SAM build**: ✅ Artefatos em `.aws-sam/build`
- **SAM deploy**: ✅ Deploy realizado com sucesso

### ✅ 7. DynamoDB Setup
**Tabela Local (Docker)**:
```bash
docker run -p 8000:8000 amazon/dynamodb-local
aws dynamodb create-table --table-name feedbacks --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=createdAt,AttributeType=S --key-schema AttributeName=pk,KeyType=HASH AttributeName=createdAt,KeyType=RANGE --billing-mode PAY_PER_REQUEST --endpoint-url http://localhost:8000
```

**Tabela AWS**: `prod-feedbacks` (criada automaticamente via SAM)

### ✅ 8. Testes Completos
**Lambda Direta**:
```powershell
aws lambda invoke --function-name prod-list-feedbacks --payload '{}' response.json
```

**API Gateway**: https://nnfddba15l.execute-api.us-east-1.amazonaws.com/Prod/feedbacks

**Local com DynamoDB**:
```powershell
sam local start-api --parameter-overrides Environment=dev DynamoEndpoint=http://host.docker.internal:8000
```
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
│   │   └── ListFeedbacksHandler.java
│   └── test/java/com/example/lambda/
│       └── AppTest.java
├── events/
│   └── test-event.json
├── .aws-sam/
│   └── build/
├── target/
├── pom.xml
├── template.yaml
├── feedback1.json
├── feedback2.json
└── feedback3.json
```

## 🔧 Configurações

### Variáveis de Ambiente
- `TABLE_NAME`: Nome da tabela DynamoDB (prod: `prod-feedbacks`)
- `DEFAULT_PAGE_SIZE`: Tamanho da página (padrão: 100)
- `DYNAMODB_ENDPOINT`: Endpoint do DynamoDB (local: http://host.docker.internal:8000)
- `AWS_REGION`: Região AWS (padrão: us-east-1)

### Parâmetros de Query
- `startDate`: Data inicial (padrão: 2020-01-01T00:00:00Z)
- `endDate`: Data final (padrão: 2030-12-31T23:59:59Z)
- `urgency`: Filtro por urgência (opcional: alta, media, baixa)
- `nextToken`: Token de paginação (opcional)

### Endpoints
- **API Gateway**: `https://nnfddba15l.execute-api.us-east-1.amazonaws.com/Prod/feedbacks`
- **Lambda Function**: `prod-list-feedbacks`
- **DynamoDB Table**: `prod-feedbacks`

## 📊 Arquitetura

```
EventBridge → Step Functions → Lambda → DynamoDB
     ↓              ↓            ↓        ↑
API Gateway ← Response ← JSON ← Query ←──┘
     ↓
Frontend/Dashboard
```

## ⚙️ Tecnologias Utilizadas
- **Java 21**
- **AWS Lambda**
- **DynamoDB**
- **Step Functions**
- **EventBridge**
- **API Gateway**
- **AWS SAM**
- **Maven**
- **Jackson JSON**
- **Docker (DynamoDB local)**

## 📝 Status
- ✅ Projeto criado e configurado
- ✅ Dependências instaladas
- ✅ Handler implementado e testado
- ✅ Infraestrutura definida e deployada
- ✅ Build bem-sucedido
- ✅ Deploy realizado
- ✅ Testes locais funcionando
- ✅ Testes em produção funcionando
- ✅ API Gateway operacional
- ✅ DynamoDB populado com dados de teste

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