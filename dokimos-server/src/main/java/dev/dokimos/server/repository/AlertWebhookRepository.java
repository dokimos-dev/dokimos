package dev.dokimos.server.repository;

import dev.dokimos.server.entity.AlertWebhook;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Tenant-scoped repository for {@link AlertWebhook}. Extends only the empty {@link Repository} plus the
 * scoped fragments, so every read takes a {@code TenantScope}. The dispatcher path lists a project's
 * enabled webhooks unrestricted (a regression alert fires regardless of the caller's tenant).
 */
public interface AlertWebhookRepository extends Repository<AlertWebhook, UUID>, AlertWebhookRepositoryFragment {}
