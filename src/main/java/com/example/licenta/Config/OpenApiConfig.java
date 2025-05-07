////package com.example.licenta.Config;
////
////import io.swagger.v3.oas.models.OpenAPI;
////import io.swagger.v3.oas.models.info.Contact;
////import io.swagger.v3.oas.models.info.Info;
////import io.swagger.v3.oas.models.info.License;
////import io.swagger.v3.oas.models.servers.Server;
////import org.springframework.beans.factory.annotation.Value;
////import org.springframework.context.annotation.Bean;
////import org.springframework.context.annotation.Configuration;
////
////import java.util.List;
////
////@Configuration
////public class OpenApiConfig {
////
////    @Value("${server.port:8082}")
////    private String serverPort;
////
////    @Bean
////    public OpenAPI myOpenAPI() {
////        Server devServer = new Server();
////        devServer.setUrl("http://localhost:" + serverPort);
////        devServer.setDescription("Development server");
////
////        return new OpenAPI()
////                .servers(List.of(devServer));
////    }
////}
//
//package com.example.licenta.Config;
//
//import com.azure.ai.openai.OpenAIClientBuilder;
//import com.azure.ai.openai.OpenAIServiceVersion;
//import com.azure.core.credential.AzureKeyCredential;
//import com.azure.core.http.policy.HttpLogOptions;
//import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
//import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class OpenApiConfig {
//
//    @Bean
//    public OpenAIClientBuilder openAIClientBuilder() {
//        String apiKey = System.getProperty("AZURE_OPENAI_API_KEY");
//        String endpoint = System.getProperty("AZURE_OPENAI_ENDPOINT");
//
//        if (apiKey == null || apiKey.isEmpty()) {
//            throw new IllegalStateException("AZURE_OPENAI_API_KEY is not set in .env file or is not being loaded properly");
//        }
//
//        if (endpoint == null || endpoint.isEmpty()) {
//            throw new IllegalStateException("AZURE_OPENAI_ENDPOINT is not set in .env file or is not being loaded properly");
//        }
//
//        return new OpenAIClientBuilder()
//                .credential(new AzureKeyCredential(apiKey))
//                .endpoint(endpoint)
//                .httpLogOptions(new HttpLogOptions()
//                        .setLogLevel(com.azure.core.http.policy.HttpLogDetailLevel.BODY_AND_HEADERS));
//    }
//
//    @Bean
//    public AzureOpenAiChatModel azureOpenAiChatModel(OpenAIClientBuilder openAIClientBuilder) {
//        return AzureOpenAiChatModel.builder()
//                .openAIClientBuilder(openAIClientBuilder)
//                .defaultOptions(AzureOpenAiChatOptions.builder()
//                        .deploymentName("gpt-4o-mini")
//                        .temperature(0.7d)
//                        .maxTokens(1000)
//                        .build())
//                .build();
//    }
//}

package com.example.licenta.Config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class OpenApiConfig {

    // or as an environment variable that Spring Boot automatically picks up.
    private final String openAiApiKey = System.getenv("OPENAI_API_KEY"); // Recommended for environment variables
    // Or for system property: System.getProperty("OPENAI_API_KEY");

    @Bean
    public OpenAiApi openAiApi() {
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY is not set. Please set it as an environment variable or system property.");
        }
        return new OpenAiApi(openAiApiKey);
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        // You can customize the model and other options here
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .temperature(0.7d)
                .maxTokens(1000)
                .build();

        return new OpenAiChatModel(openAiApi, options);
    }
}