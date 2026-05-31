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
 * extra dependency. It enforces three rules that keep the compile-time scoped-repository design intact:
 *
 * <ol>
 *   <li>A tenant entity repository never extends {@link CrudRepository}, {@link JpaRepository}, or {@link
 *       PagingAndSortingRepository}. Those carry the dangerous inherited finders ({@code findById(id)},
 *       {@code findAll()}, {@code getReferenceById}) that would let an unscoped load slip through. Tenant
 *       repositories extend only the empty {@link Repository} plus the scoped fragments.
 *   <li>A service never depends on {@link EntityManager} directly. The only legitimate place to build a
 *       query is a scoped repository (whose finders all require a {@link TenantScope}); a service holding
 *       an {@code EntityManager} could bypass the scope predicate entirely.
 *   <li>The child-row repositories that still extend {@link JpaRepository} (so they expose the unscoped
 *       {@code findById}/{@code getReferenceById}/{@code findAllById}) may be injected only by an explicit
 *       allow-list of classes that already reach them behind a documented tenant gate. Any other class
 *       that injects one fails the build, forcing a reviewer to add it to the allow-list and justify the
 *       gate rather than silently loading a child row by id from user input.
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

    /**
     * The child-row repositories that still extend {@code JpaRepository} and so expose the unscoped
     * inherited finders. Access to any of these is restricted to {@link #CHILD_REPOSITORY_ACCESSORS}.
     */
    private static final List<String> CHILD_REPOSITORIES = List.of(
            "ItemResultRepository",
            "EvalResultRepository",
            "DatasetVersionRepository",
            "DatasetItemRepository",
            "TraceSpanRepository",
            "AnnotationRepository");

    /**
     * The only classes allowed to inject a {@link #CHILD_REPOSITORIES child repository}. Each reaches its
     * child rows behind a tenant gate, named alongside it. A new class that injects a child repository is
     * not on this list, so it fails the build until a reviewer adds it and documents how it gates the
     * load.
     */
    private static final List<String> CHILD_REPOSITORY_ACCESSORS = List.of(
            // Loads items only by an ExperimentRun the caller already resolved through the scoped run
            // finder (findByRunWithEvals), never by raw id.
            "ComparisonSupport",
            // Loads the run through the scoped run finder (runRepository.findById(runId, scope)) before
            // reading its items and annotations.
            "AlignmentService",
            // Reads items through the tenant-predicated query (findItemsNeedingReview takes the scope),
            // then batch-loads evals and annotations only for ids that query already returned.
            "ReviewQueueService",
            // Worker-side persistence behind a JudgeJob whose run was scoped when the job was enqueued;
            // stamps each eval result with its parent item's tenant (getReferenceById is used only to
            // attach a result to an item the job already owns).
            "JudgeJobTransactions",
            // Creates versions and items under a Dataset loaded through the scoped findByNameForUpdate,
            // stamping the dataset's tenant; loads dataset items only behind a visibleUnder(scope) filter.
            "DatasetService",
            // Loads the run through the scoped run finder (requireItemResultInRun) before touching the
            // item result or its annotation.
            "AnnotationService",
            // Saves items under a run the service already scoped, and loads dataset items only behind a
            // visibleUnder(scope) filter (loadDatasetItems).
            "RunService",
            // Loads spans only for a Trace resolved through the scoped trace finder
            // (traceRepository.findById(id, scope)).
            "TraceQueryService");

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

    @Test
    void onlyAllowListedClassesInjectChildRepositories() throws Exception {
        List<String> offenders = new ArrayList<>();
        List<Class<?>> candidates = new ArrayList<>();
        candidates.addAll(classesIn("service"));
        candidates.addAll(classesInRecursive("controller"));
        candidates.addAll(classesIn("judge"));
        for (Class<?> type : candidates) {
            if (CHILD_REPOSITORY_ACCESSORS.contains(type.getSimpleName())) {
                continue;
            }
            for (Field field : type.getDeclaredFields()) {
                if (CHILD_REPOSITORIES.contains(field.getType().getSimpleName())) {
                    offenders.add(type.getSimpleName() + "." + field.getName() + " -> "
                            + field.getType().getSimpleName());
                }
            }
        }
        assertThat(offenders)
                .as("a child repository (which still exposes unscoped findById/getReferenceById/findAllById) "
                        + "may be injected only by an allow-listed accessor that gates the load behind a "
                        + "tenant scope; add the new class to CHILD_REPOSITORY_ACCESSORS and document its gate")
                .isEmpty();
    }

    private static List<Class<?>> classesIn(String subPackage) throws IOException, ClassNotFoundException {
        return loadClasses(subPackage, false);
    }

    /** Recurses into nested sub-packages, so a rule covering {@code controller} also covers {@code controller.v1}. */
    private static List<Class<?>> classesInRecursive(String subPackage) throws IOException, ClassNotFoundException {
        return loadClasses(subPackage, true);
    }

    /**
     * Loads production classes in the sub-package, scanning the main build output only so a service test
     * that injects a repository for a fixture is not counted as a production accessor.
     */
    private static List<Class<?>> loadClasses(String subPackage, boolean recursive)
            throws IOException, ClassNotFoundException {
        Path mainRoot = mainClassesRoot();
        String pkg = BASE_PACKAGE + "." + subPackage;
        Path dir = mainRoot.resolve(pkg.replace('.', '/'));
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> files = recursive ? Files.walk(dir) : Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String relative = mainRoot.relativize(file).toString().replace('/', '.');
                String className = relative.substring(0, relative.length() - ".class".length());
                if (className.contains("$")) {
                    continue;
                }
                classes.add(Class.forName(className));
            }
        }
        return classes;
    }

    /** Resolves the production {@code target/classes} root from a known main class, not test output. */
    private static Path mainClassesRoot() {
        String mainClassPath = (BASE_PACKAGE + ".DokimosServerApplication").replace('.', '/') + ".class";
        URL marker = Thread.currentThread().getContextClassLoader().getResource(mainClassPath);
        if (marker == null) {
            throw new IllegalStateException("Could not locate production classes root via " + mainClassPath);
        }
        Path markerFile = Path.of(marker.getPath());
        Path root = markerFile;
        for (int i = 0; i < mainClassPath.split("/").length; i++) {
            root = root.getParent();
        }
        return root;
    }
}
