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
    @Test void malformedHostingUrlCannotAppearInError() {
        var env=new MockEnvironment().withProperty("DATABASE_URL","https://private:credential@wrong.example/db");
        assertThatThrownBy(()->new DatabaseUrlEnvironment().postProcessEnvironment(env,new SpringApplication()))
            .hasMessageNotContaining("private").hasMessageNotContaining("credential").hasMessageNotContaining("wrong.example");
    }
}
