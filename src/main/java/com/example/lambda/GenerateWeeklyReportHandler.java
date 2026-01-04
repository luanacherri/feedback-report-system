package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

public class GenerateWeeklyReportHandler implements RequestHandler<Map<String, Object>, String> {

    private final S3Client s3;
    private final String bucketName;

    public GenerateWeeklyReportHandler() {
        String s3Endpoint = System.getenv("S3_ENDPOINT");
        S3ClientBuilder builder = S3Client.builder();
        
        if (s3Endpoint != null && !s3Endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(s3Endpoint))
                   .forcePathStyle(true); // Necessário para MinIO
            
            // Para ambiente local (MinIO), usar credenciais específicas
            String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
            String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
            if (accessKey != null && secretKey != null) {
                AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
                builder.credentialsProvider(StaticCredentialsProvider.create(credentials))
                       .region(Region.US_EAST_1); // Região fixa para MinIO
            }
        }
        
        this.s3 = builder.build();
        this.bucketName = System.getenv("REPORTS_BUCKET");
    }

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("Iniciando geração do relatório semanal...\n");

        try {
            // Exemplo: feedbacks recebidos da Lambda A
            List<Map<String, Object>> feedbacks = (List<Map<String, Object>>) input.get("feedbacks");

            // Verificar se o bucket existe, criar se necessário
            try {
                s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                logger.log("Bucket encontrado: " + bucketName + "\n");
            } catch (Exception e) {
                logger.log("Bucket não existe, tentando criar: " + bucketName + "\n");
                try {
                    s3.createBucket(CreateBucketRequest.builder()
                            .bucket(bucketName)
                            .build());
                    logger.log("Bucket criado com sucesso: " + bucketName + "\n");
                } catch (Exception createError) {
                    logger.log("AVISO: Não foi possível criar bucket (pode não ter permissão): " + createError.getMessage() + "\n");
                    logger.log("Continuando assumindo que bucket será criado automaticamente pelo S3...\n");
                }
            }

            // Calcular estatísticas dos feedbacks
            String reportContent = generateReportContent(feedbacks, logger);
            logger.log("Conteúdo do relatório:\n" + reportContent + "\n");

            // Nome do arquivo no S3
            String objectKey = "weekly-report-" + LocalDate.now() + ".txt";
            logger.log("Salvando arquivo: " + objectKey + " no bucket: " + bucketName + "\n");

            // Upload para S3
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType("text/plain; charset=utf-8")
                    .contentEncoding("utf-8")
                    .build(),
                RequestBody.fromString(reportContent, StandardCharsets.UTF_8)
            );
            logger.log("Upload concluído com sucesso!\n");

            logger.log("Relatório salvo no bucket S3: " + bucketName + "/" + objectKey + "\n");

            return "Relatório gerado com sucesso: " + objectKey;

        } catch (Exception e) {
            logger.log("Erro ao gerar relatório: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String generateReportContent(List<Map<String, Object>> feedbacks, LambdaLogger logger) {
        StringBuilder report = new StringBuilder();
        
        report.append("=== RELATÓRIO SEMANAL DE FEEDBACKS ===\n");
        report.append("Data de geração: ").append(LocalDate.now()).append("\n\n");

        if (feedbacks == null || feedbacks.isEmpty()) {
            report.append("Nenhum feedback encontrado no período.\n");
            return report.toString();
        }

        int total = feedbacks.size();
        report.append("Total de feedbacks: ").append(total).append("\n\n");

        // Calcular média das notas
        OptionalDouble mediaNotas = feedbacks.stream()
            .filter(feedback -> feedback.get("nota") != null)
            .mapToDouble(feedback -> {
                try {
                    return Double.parseDouble(feedback.get("nota").toString());
                } catch (NumberFormatException e) {
                    logger.log("Nota inválida ignorada: " + feedback.get("nota"));
                    return 0.0;
                }
            })
            .filter(nota -> nota > 0)
            .average();

        if (mediaNotas.isPresent()) {
            report.append(String.format("Média geral das notas: %.2f\n", mediaNotas.getAsDouble()));
        }

        // Contadores por urgência
        long alta = feedbacks.stream()
            .filter(f -> "alta".equals(f.get("urgency")))
            .count();
        long media = feedbacks.stream()
            .filter(f -> "media".equals(f.get("urgency")))
            .count();
        long baixa = feedbacks.stream()
            .filter(f -> "baixa".equals(f.get("urgency")))
            .count();

        report.append("\n=== DISTRIBUIÇÃO POR URGÊNCIA ===\n");
        report.append("Alta: ").append(alta).append(" feedbacks\n");
        report.append("Média: ").append(media).append(" feedbacks\n");
        report.append("Baixa: ").append(baixa).append(" feedbacks\n");

        // Quantidade de avaliações por dia
        Map<String, Long> avaliacoesPorDia = feedbacks.stream()
            .filter(feedback -> feedback.get("createdAt") != null)
            .collect(Collectors.groupingBy(
                feedback -> {
                    try {
                        String createdAt = feedback.get("createdAt").toString();
                        // Extrair apenas a data (YYYY-MM-DD) do timestamp ISO
                        return createdAt.substring(0, 10);
                    } catch (Exception e) {
                        logger.log("Data inválida ignorada: " + feedback.get("createdAt"));
                        return "Data inválida";
                    }
                },
                Collectors.counting()
            ));

        report.append("\n=== QUANTIDADE DE AVALIAÇÕES POR DIA ===\n");
        avaliacoesPorDia.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByKey())
            .forEach(entry -> {
                report.append(entry.getKey()).append(": ").append(entry.getValue()).append(" avaliações\n");
            });

        // Detalhes dos feedbacks
        report.append("\n=== DETALHES DOS FEEDBACKS ===\n");
        for (int i = 0; i < feedbacks.size(); i++) {
            Map<String, Object> feedback = feedbacks.get(i);
            report.append(String.format("%d. Nota: %s | Urgência: %s | Data: %s\n", 
                i + 1,
                feedback.get("nota"),
                feedback.get("urgency"),
                feedback.get("createdAt")
            ));
            
            if (feedback.get("descricao") != null) {
                report.append("   Descrição: ").append(feedback.get("descricao")).append("\n");
            }
            report.append("\n");
        }

        return report.toString();
    }
}