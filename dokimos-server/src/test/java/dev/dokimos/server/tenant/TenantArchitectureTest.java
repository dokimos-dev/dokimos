package dev.dokimos.server.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.Repository;

/**
 * Structural backstop for the tenant isolation boundary, in the spirit of an ArchUnit test but with no
 * extra dependency. It enforces two rules that keep the compile-time scoped-repository design intact:
 *
 * <ol>
 *   <li>A tenant entity repository never extends {@link CrudRepository}, {@link JpaRepository}, or {@link
 *       PagingAndSortingRepository}. Those carry the dangerous inherited finders ({@code findById(id)},
 *       {@code findAll()}, {@code getReferenceById}) that would let an unscoped load slip through. Tenant
 *       repositories extend only the empty {@link Repository} plus the scoped fragments.
 *   <li>A service never depends on {@link EntityManager} directly. The only legitimate place to build a
 *       query is a scoped repository (whose finders all require a {@link TenantScope}); a service holding
 *       an {@code EntityManager} could bypass the scope predicate entirely.
 * </ol>
 *
 * <p>Infrastructure repositories that carry no tenant column (queues, batches, the API key store) are
 * allowed to remain {@code JpaRepository}; they are listed explicitly so the rule stays a deliberate
 * allow-list rather than a blanket exemption.
 */
class TenantArchitectureTest {

    private static final String BASE_PACKAGE = "dev.dokimos.server";

    /**
     * Repositories allowed to remain {@code JpaRepository}. Two groups:
     *
     * <ul>
     *   <li>Infrastructure with no tenant column (queues, batches, the key store): {@code ApiKeyRepository},
     *       {@code EvalJobRepository}, {@code TraceEvalJobRepository}, {@code IngestedBatchRepository}.
     *   <li>Tenant child rows that are never loaded by id straight from user input. They carry a {@code
     *       tenant_id} (stamped from their parent) but are reached only through a tenant-scoped parent (a
     *       scoped run, dataset, or trace), so the parent load is the trust boundary. {@code
     *       AnnotationRepository} is reached only through {@code AnnotationService}, which loads the run
     *       through the scoped run finder before touching the item or its annotation.
     * </ul>
     */
    private static final List<String> NON_TENANT_REPOSITORIES = List.of(
            "ApiKeyRepository",
            "EvalJobRepository",
            "TraceEvalJobRepository",
            "IngestedBatchRepository",
            "ItemResultRepository",
            "EvalResultRepository",
            "DatasetItemRepository",
            "DatasetVersionRepository",
            "TraceSpanRepository",
            "AnnotationRepository");

    @Test
    void tenantRepositoriesDoNotExtendCrudOrJpaRepository() throws Exception {
        List<Class<?>> offenders = new ArrayList<>();
        for (Class<?> type : classesIn("repository")) {
            if (!type.isInterface() || !Repository.class.isAssignableFrom(type)) {
                continue;
            }
            if (NON_TENANT_REPOSITORIES.contains(type.getSimpleName())) {
                continue;
            }
            boolean dangerous = CrudRepository.class.isAssignableFrom(type)
                    || JpaRepository.class.isAssignableFrom(type)
                    || PagingAndSortingRepository.class.isAssignableFrom(type);
            if (dangerous) {
                offenders.add(type);
            }
        }
        assertThat(offenders)
                .as("tenant repositories must extend only Repository<T,ID> plus scoped fragments, never "
                        + "Crud/Jpa/PagingAndSorting which carry unscoped finders")
                .isEmpty();
    }

    @Test
    void servicesDoNotDependOnEntityManagerDirectly() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : classesIn("service")) {
            for (Field field : type.getDeclaredFields()) {
                if (EntityManager.class.isAssignableFrom(field.getType())) {
                    offenders.add(type.getSimpleName() + "." + field.getName());
                }
            }
        }
        assertThat(offenders)
                .as("services must reach the database through scoped repositories, never an EntityManager "
                        + "field that could bypass the tenant predicate")
                .isEmpty();
    }

    private static List<Class<?>> classesIn(String subPackage) throws IOException, ClassNotFoundException {
        String pkg = BASE_PACKAGE + "." + subPackage;
        String path = pkg.replace('.', '/');
        URL root = Thread.currentThread().getContextClassLoader().getResource(path);
        if (root == null) {
            return List.of();
        }
        Path dir = Path.of(root.getPath());
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String className = file.getFileName().toString().replace(".class", "");
                if (className.contains("$")) {
                    continue;
                }
                classes.add(Class.forName(pkg + "." + className));
            }
        }
        return classes;
    }
}
