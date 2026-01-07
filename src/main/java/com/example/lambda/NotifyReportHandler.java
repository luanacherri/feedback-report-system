package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.net.URI;
import java.util.Map;

public class NotifyReportHandler implements RequestHandler<Map<String, Object>, String> {

    private final S3Client s3;
    private final SesClient ses;
    private final String bucketName;
    private final String recipientEmail;
    private final String sourceEmail;

    public NotifyReportHandler() {
        String s3Endpoint = System.getenv("S3_ENDPOINT");
        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        
        S3ClientBuilder builder = S3Client.builder();
        
        if (s3Endpoint != null && !s3Endpoint.isEmpty()) {
            // MinIO ou S3 local
            builder.endpointOverride(URI.create(s3Endpoint))
                   .forcePathStyle(true)
                   .region(Region.US_EAST_1);
            
            if (accessKey != null && secretKey != null) {
                AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
                builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
            }
        } else {
            // AWS S3 em produção - usar credenciais padrão
            String regionEnv = System.getenv("AWS_REGION");
            Region region = regionEnv != null ? Region.of(regionEnv) : Region.US_EAST_1;
            builder.region(region);
        }
        
        this.s3 = builder.build();
        this.ses = SesClient.create();
        this.bucketName = System.getenv("REPORTS_BUCKET");
        this.recipientEmail = System.getenv("RECIPIENT_EMAIL");
        this.sourceEmail = System.getenv("SOURCE_EMAIL") != null ? System.getenv("SOURCE_EMAIL") : "no-reply@seu-dominio-validado.com";
    }

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("Iniciando envio do relatório por e-mail...\n");
        logger.log("RECIPIENT_EMAIL: " + recipientEmail + "\n");
        logger.log("SOURCE_EMAIL: " + sourceEmail + "\n");
        logger.log("BUCKET: " + bucketName + "\n");

        try {
            String objectKey = (String) input.get("reportKey");
            if (objectKey == null) {
                logger.log("Nenhum reportKey fornecido no input.\n");
                throw new IllegalArgumentException("reportKey is required");
            }

            ResponseBytes<GetObjectResponse> objectBytes = s3.getObject(
                GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build(),
                ResponseTransformer.toBytes()
            );

            String reportContent = objectBytes.asUtf8String();

            // Montar e enviar e-mail via SES
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                .destination(Destination.builder()
                    .toAddresses(recipientEmail)
                    .build())
                .message(Message.builder()
                    .subject(Content.builder()
                        .data("Relatório semanal de feedbacks")
                        .build())
                    .body(Body.builder()
                        .text(Content.builder()
                            .data(reportContent.toString())
                            .build())
                        .build())
                    .build())
                .source(sourceEmail)
                .build();

            ses.sendEmail(emailRequest);

            logger.log("Relatório enviado para: " + recipientEmail + "\n");
            return "Relatório enviado com sucesso para " + recipientEmail;

        } catch (Exception e) {
            logger.log("Erro ao enviar relatório: " + e.getMessage() + "\n");
            throw new RuntimeException(e);
        }
    }
}
