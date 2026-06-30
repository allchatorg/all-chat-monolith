package com.mk3.chatapp.configs;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AWS3Config {

    @Value("${wasabi.s3.access-key}")
    private String accessKey;

    @Value("${wasabi.s3.secret-key}")
    private String secretKey;

    @Value("${wasabi.s3.endpoint}")
    private String endpoint;

    @Value("${wasabi.s3.region:us-east-1}")
    private String region;

    @Bean
    public AmazonS3 wasabiS3Client() {
        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        return AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(
                        new AmazonS3ClientBuilder.EndpointConfiguration(endpoint, region))
                .withPathStyleAccessEnabled(true)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();
    }
}