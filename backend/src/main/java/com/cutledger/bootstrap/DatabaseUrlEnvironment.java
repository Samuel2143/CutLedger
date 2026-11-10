package com.cutledger.bootstrap;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.*;

/** Maps hosting URLs before datasource binding, without echoing credential-bearing URLs. */
public class DatabaseUrlEnvironment implements EnvironmentPostProcessor {
    @Override public void postProcessEnvironment(ConfigurableEnvironment environment,SpringApplication application) {
        String value=environment.getProperty("DATABASE_URL","");
        if(value.isBlank()||value.startsWith("jdbc:postgresql:")) return;
        try {
            URI uri=URI.create(value);
            if(!Set.of("postgres","postgresql").contains(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()==null||uri.getFragment()!=null)
                throw new IllegalArgumentException();
            String[] credentials=uri.getRawUserInfo().split(":",2);
            if(credentials.length!=2||uri.getPath()==null||!uri.getPath().matches("/[A-Za-z0-9_-]+")) throw new IllegalArgumentException();
            String host=uri.getHost();
            if(host.contains(":")) host="["+host+"]";
            String url="jdbc:postgresql://"+host+":"+(uri.getPort()<0?5432:uri.getPort())+uri.getRawPath();
            // Preserve only explicitly supported driver options from a trusted hosting URL.
            if(uri.getRawQuery()!=null) {
                if(!uri.getRawQuery().matches("sslmode=(require|verify-full|verify-ca|disable)")) throw new IllegalArgumentException();
                url+="?"+uri.getRawQuery();
            }
            Map<String,Object> mapped=Map.of("spring.datasource.url",url,
                "spring.datasource.username",URLDecoder.decode(credentials[0].replace("+","%2B"),StandardCharsets.UTF_8),
                "spring.datasource.password",URLDecoder.decode(credentials[1].replace("+","%2B"),StandardCharsets.UTF_8));
            environment.getPropertySources().addFirst(new MapPropertySource("hostingDatabase",mapped));
        } catch(Exception ignored) { throw new IllegalStateException("DATABASE_URL must be a valid supported PostgreSQL connection URL (value redacted)"); }
    }
}
