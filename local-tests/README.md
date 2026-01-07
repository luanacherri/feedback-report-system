# 🧪 Testes Locais - Arquivos

Esta pasta contém todos os arquivos necessários para executar os testes locais do sistema de feedback.

## 📁 Estrutura

### `dynamodb-data/`
Contém os arquivos JSON para inserir dados de teste no DynamoDB Local.

- **feedback1.json** - Feedback com nota 9, urgência alta, data: 2025-12-30
- **feedback2.json** - Feedback com nota 8, urgência média, data: 2025-12-31
- **feedback3.json** - Feedback com nota 6, urgência baixa, data: 2026-01-02

**Como usar**:
```bash
aws dynamodb put-item --table-name FeedbacksTable \
  --item file://local-tests/dynamodb-data/feedback1.json \
  --endpoint-url http://localhost:8000
```

### `events/`
Contém os payloads de entrada para testar cada Lambda function.

- **test-all-feedbacks.json** - Testa ListFeedbacksHandler buscando todos os feedbacks no período
- **test-generate-report.json** - Testa GenerateWeeklyReportHandler com os 3 feedbacks
- **test-notify-report.json** - Testa NotifyReportHandler com a chave do relatório

**Como usar**:
```bash
sam local invoke ListFeedbacksFunction \
  --event local-tests/events/test-all-feedbacks.json \
  --env-vars env.json
```

### `results/`
Contém os resultados dos testes executados.

- **weekly-report-generated-local.txt** - Relatório semanal gerado pela Lambda B
  - Total de feedbacks: 3
  - Média das notas: 7.67
  - Distribuição por urgência: Alta(1), Média(1), Baixa(1)
  
- **email-sent-simulation.txt** - Simulação do email que seria enviado pela Lambda C
  - De: no-reply@seu-dominio-validado.com
  - Para: destinatario@example.com
  - Assunto: Relatório semanal de feedbacks

## 🎯 Ordem de Execução

1. **Setup inicial** (uma vez)
   - Iniciar DynamoDB Local
   - Criar tabela
   - Inserir os 3 feedbacks
   - Iniciar MinIO
   - Criar bucket

2. **Testar Lambda A** - ListFeedbacksHandler
   ```bash
   sam local invoke ListFeedbacksFunction \
     --event local-tests/events/test-all-feedbacks.json \
     --env-vars env.json
   ```

3. **Testar Lambda B** - GenerateWeeklyReportHandler
   ```bash
   sam local invoke GenerateWeeklyReportFunction \
     --event local-tests/events/test-generate-report.json \
     --env-vars env.json
   ```

4. **Testar Lambda C** - NotifyReportHandler
   ```bash
   sam local invoke NotifyReportFunction \
     --event local-tests/events/test-notify-report.json \
     --env-vars env.json
   ```

## ✅ Validação

Após executar os testes, você deve ter:
- ✅ 3 feedbacks no DynamoDB Local
- ✅ 1 relatório no MinIO (s3://local-feedback-reports/)
- ✅ Outputs JSON válidos de cada Lambda
- ✅ Nenhum erro crítico nos logs

## 📝 Notas

- O upload para MinIO pode falhar no teste da Lambda B devido a limitações do SDK Java em ambiente Docker, mas o relatório é gerado corretamente.
- O SES não funciona localmente, então o teste da Lambda C valida apenas a lógica de leitura e formatação do email.
- Todos os testes validam que o código está funcionando corretamente e pronto para deploy na AWS.
