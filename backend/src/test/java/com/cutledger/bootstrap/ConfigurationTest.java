package com.cutledger.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ConfigurationTest {
    @Test void mapsHostingUrlWithoutEmbeddingCredentialsInJdbcUrl() {
        var env=new MockEnvironment().withProperty("DATABASE_URL","postgresql://demo:p%40ss%2Bword@host.example:5432/cutledger?sslmode=require");
        new DatabaseUrlEnvironment().postProcessEnvironment(env,new SpringApplication());
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://host.example:5432/cutledger?sslmode=require");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("demo");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p@ss+word");
    }
    AppConfig configured(String origin,String webhook,String gate){return new AppConfig(java.net.URI.create(origin),"synthetic-pepper-with-more-than-32-characters",gate,"sandbox","synthetic-client","synthetic-secret","synthetic-merchant",webhook,"synthetic-model-key","gemini-3.5-flash-lite",200);}
    @Test void hostedProfileRequiresHttpsAndCompleteIntegrationAndHashedAccessGate(){
        var demo=new MockEnvironment();demo.setActiveProfiles("demo");
        var hashed=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("synthetic-test-access");
        assertThatCode(()->new RuntimeConfig(configured("https://cutledger.example","synthetic-webhook",hashed),demo)).doesNotThrowAnyException();
        assertThatThrownBy(()->new RuntimeConfig(configured("http://localhost:8080","synthetic-webhook",hashed),demo)).hasMessageContaining("HTTPS");
        assertThatThrownBy(()->new RuntimeConfig(configured("https://cutledger.example","",hashed),demo)).hasMessageContaining("configured");
        assertThatThrownBy(()->new RuntimeConfig(configured("https://cutledger.example","synthetic-webhook","plaintext-test-code"),demo)).hasMessageContaining("hashed access gate");
    }
    @Test void localHttpIsRestrictedToLoopbackOrigins(){
        var local=new MockEnvironment();local.setActiveProfiles("local");
        assertThatCode(()->new RuntimeConfig(configured("http://localhost:8080","",""),local)).doesNotThrowAnyException();
        assertThatThrownBy(()->new RuntimeConfig(configured("http://public.example","",""),local)).hasMessageContaining("HTTPS");
    }
    @Test void malformedHostingUrlCannotAppearInError() {
        var env=new MockEnvironment().withProperty("DATABASE_URL","https://private:credential@wrong.example/db");
        assertThatThrownBy(()->new DatabaseUrlEnvironment().postProcessEnvironment(env,new SpringApplication()))
            .hasMessageNotContaining("private").hasMessageNotContaining("credential").hasMessageNotContaining("wrong.example");
    }
}
