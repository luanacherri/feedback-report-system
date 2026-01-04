package com.example.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.regions.Region;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.*;

public class ListFeedbacksHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient ddb;
    private final String tableName = System.getenv("TABLE_NAME");
    private final int pageSize = Integer.parseInt(System.getenv().getOrDefault("DEFAULT_PAGE_SIZE", "100"));
    private final ObjectMapper mapper = new ObjectMapper();

    public ListFeedbacksHandler() {
        String endpoint = System.getenv("DYNAMODB_ENDPOINT"); // usado só em testes locais
        String regionEnv = System.getenv("AWS_REGION");       // já existe na AWS automaticamente
        Region region = Region.of(regionEnv != null ? regionEnv : "us-east-1");

        DynamoDbClientBuilder builder = DynamoDbClient.builder().region(region);

        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        this.ddb = builder.build();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            // Verificar se é uma chamada do API Gateway
            boolean isApiGateway = event.containsKey("httpMethod") || event.containsKey("requestContext");
            
            Map<String, Object> queryParams = new HashMap<>();
            if (isApiGateway) {
                // Extrair query parameters do API Gateway event
                Map<String, Object> queryStringParams = (Map<String, Object>) event.get("queryStringParameters");
                if (queryStringParams != null) {
                    queryParams = queryStringParams;
                }
            } else {
                // Chamada direta da Lambda
                queryParams = event;
            }
            
            Map<String, Object> result = processRequest(queryParams);
            
            if (isApiGateway) {
                // Retornar resposta no formato API Gateway
                return createApiGatewayResponse(200, result);
            } else {
                // Retornar resposta direta
                return result;
            }
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            if (event.containsKey("httpMethod") || event.containsKey("requestContext")) {
                return createApiGatewayResponse(500, Map.of("error", e.getMessage()));
            } else {
                throw new RuntimeException(e);
            }
        }
    }
    
    private Map<String, Object> processRequest(Map<String, Object> queryParams) {
        // Validar e definir valores padrão para parâmetros obrigatórios
        String startDate = (String) queryParams.get("startDate");
        String endDate = (String) queryParams.get("endDate");
        String urgency = (String) queryParams.get("urgency");
        Map<String, Object> nextToken = (Map<String, Object>) queryParams.get("nextToken");

        // Valores padrão se não fornecidos
        if (startDate == null || startDate.isEmpty()) {
            startDate = "2020-01-01T00:00:00Z";
        }
        if (endDate == null || endDate.isEmpty()) {
            endDate = "2030-12-31T23:59:59Z";
        }

        Map<String, AttributeValue> exprValues = new HashMap<>();
        exprValues.put(":pk", AttributeValue.builder().s("FEEDBACK").build());
        exprValues.put(":start", AttributeValue.builder().s(startDate).build());
        exprValues.put(":end", AttributeValue.builder().s(endDate).build());

        QueryRequest.Builder queryBuilder = QueryRequest.builder()
                .tableName(tableName)
                .limit(pageSize)
                .keyConditionExpression("pk = :pk AND createdAt BETWEEN :start AND :end");

        if (urgency != null && !urgency.isEmpty()) {
            exprValues.put(":urgency", AttributeValue.builder().s(urgency).build());
            queryBuilder.filterExpression("urgency = :urgency");
        }

        queryBuilder.expressionAttributeValues(exprValues);

        if (nextToken != null && !nextToken.isEmpty()) {
            queryBuilder.exclusiveStartKey(convertMap(nextToken));
        }

        QueryResponse response = ddb.query(queryBuilder.build());

        Map<String, Object> result = new HashMap<>();
        result.put("count", response.count());
        
        // Convert DynamoDB items to readable format
        List<Map<String, Object>> responseItems = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            Map<String, Object> convertedItem = new HashMap<>();
            for (Map.Entry<String, AttributeValue> entry : item.entrySet()) {
                convertedItem.put(entry.getKey(), convertAttributeValue(entry.getValue()));
            }
            responseItems.add(convertedItem);
        }
        
        result.put("items", responseItems);
        result.put("nextToken", response.lastEvaluatedKey().isEmpty() ? null : response.lastEvaluatedKey());
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        result.put("urgency", urgency);

        return result;
    }

    private Map<String, AttributeValue> convertMap(Map<String, Object> input) {
        Map<String, AttributeValue> output = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            output.put(entry.getKey(), AttributeValue.builder().s(entry.getValue().toString()).build());
        }
        return output;
    }

    private Object convertAttributeValue(AttributeValue value) {
        if (value.s() != null) {
            return value.s();
        } else if (value.n() != null) {
            return value.n();
        } else if (value.bool() != null) {
            return value.bool();
        } else if (value.hasL() && !value.l().isEmpty()) {
            return value.l().stream().map(this::convertAttributeValue).toArray();
        } else if (value.hasM() && !value.m().isEmpty()) {
            Map<String, Object> map = new HashMap<>();
            value.m().forEach((k, v) -> map.put(k, convertAttributeValue(v)));
            return map;
        } else if (value.nul() != null && value.nul()) {
            return null;
        }
        return value.toString();
    }
    
    private Map<String, Object> createApiGatewayResponse(int statusCode, Object body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        response.put("headers", headers);
        
        try {
            response.put("body", mapper.writeValueAsString(body));
        } catch (Exception e) {
            response.put("body", "{\"error\": \"Failed to serialize response\"}");
        }
        
        return response;
    }
}