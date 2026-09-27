package com.quizsphere.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Hosting providers (Render, Neon, Heroku) expose DATABASE_URL as
 * {@code postgres://user:password@host:port/db?sslmode=require}. JDBC needs
 * {@code jdbc:postgresql://host:port/db?sslmode=require} plus separate credentials,
 * so this converts the provider form before the datasource is configured.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty("DATABASE_URL");
        if (raw == null || raw.isBlank() || raw.startsWith("jdbc:")) {
            return;
        }
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) {
            // e.g. a machine-wide DATABASE_URL left by another project (sqlite:///...). Logging is not
            // initialised yet, so warn on stderr and fall back to the local development database.
            System.err.println("WARNING: ignoring DATABASE_URL with unsupported scheme (QuizSphere needs PostgreSQL); "
                    + "using jdbc:postgresql://localhost:5432/quizsphere");
            environment.getPropertySources().addFirst(new MapPropertySource("quizsphereDatabaseUrl",
                    Map.of("spring.datasource.url", "jdbc:postgresql://localhost:5432/quizsphere")));
            return;
        }
        URI uri = URI.create(raw.replaceFirst("^postgres(ql)?://", "http://"));
        String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
        String query = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath() + query);
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            String[] parts = userInfo.split(":", 2);
            props.put("spring.datasource.username", URLDecoder.decode(parts[0], StandardCharsets.UTF_8));
            if (parts.length > 1) {
                props.put("spring.datasource.password", URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        environment.getPropertySources().addFirst(new MapPropertySource("quizsphereDatabaseUrl", props));
    }
}
