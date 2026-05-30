package dev.dokimos.server.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the single-page React app and forwards client side routes to {@code index.html} so a deep
 * link or a page refresh on a route such as {@code /traces} or {@code /api-keys} is handled by the SPA
 * router instead of returning 404. Real static assets are served directly; only unmatched, non-API
 * paths fall back to {@code index.html}. API, actuator, and OpenAPI paths are left to their
 * controllers, so an unknown one still returns 404 rather than the app shell.
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(@NonNull String resourcePath, @NonNull Resource location)
                            throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("actuator/")
                                || resourcePath.startsWith("v3/")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
