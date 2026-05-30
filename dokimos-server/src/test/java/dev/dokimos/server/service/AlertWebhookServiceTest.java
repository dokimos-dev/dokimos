package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dokimos.server.dto.v1.AlertWebhookView;
import dev.dokimos.server.dto.v1.CreateAlertWebhookRequest;
import dev.dokimos.server.dto.v1.UpdateAlertWebhookRequest;
import dev.dokimos.server.entity.AlertWebhook;
import dev.dokimos.server.entity.Project;
import dev.dokimos.server.repository.AlertWebhookRepository;
import dev.dokimos.server.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertWebhookServiceTest {

    @Mock
    private AlertWebhookRepository webhookRepository;

    @Mock
    private ProjectRepository projectRepository;

    private AlertWebhookService service;

    @BeforeEach
    void setUp() {
        service = new AlertWebhookService(webhookRepository, projectRepository);
    }

    @Test
    void create_shouldSaveEnabledWebhookAndNeverExposeSecret() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(webhookRepository.save(any(AlertWebhook.class))).thenAnswer(inv -> {
            AlertWebhook w = inv.getArgument(0);
            setField(w, "id", UUID.randomUUID());
            return w;
        });

        AlertWebhookView view =
                service.create(projectId, new CreateAlertWebhookRequest("https://hooks.test/x", "shh", null));

        assertThat(view.enabled()).isTrue();
        assertThat(view.hasSecret()).isTrue();
        assertThat(view.url()).isEqualTo("https://hooks.test/x");

        ArgumentCaptor<AlertWebhook> captor = ArgumentCaptor.forClass(AlertWebhook.class);
        verify(webhookRepository).save(captor.capture());
        assertThat(captor.getValue().getSecret()).isEqualTo("shh");
    }

    @Test
    void create_shouldTreatBlankSecretAsNoSecret() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(projectId)));
        when(webhookRepository.save(any(AlertWebhook.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertWebhookView view =
                service.create(projectId, new CreateAlertWebhookRequest("https://hooks.test/x", "  ", false));

        assertThat(view.hasSecret()).isFalse();
        assertThat(view.enabled()).isFalse();
    }

    @Test
    void create_shouldThrowWhenProjectMissing() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        service.create(projectId, new CreateAlertWebhookRequest("https://hooks.test/x", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    void update_shouldKeepExistingSecretWhenBlank() {
        UUID projectId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        Project project = project(projectId);
        AlertWebhook webhook = new AlertWebhook(project, "https://old.test", "original", true);
        setField(webhook, "id", webhookId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(webhookRepository.findById(webhookId)).thenReturn(Optional.of(webhook));
        when(webhookRepository.save(any(AlertWebhook.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(projectId, webhookId, new UpdateAlertWebhookRequest("https://new.test", " ", false));

        assertThat(webhook.getUrl()).isEqualTo("https://new.test");
        assertThat(webhook.isEnabled()).isFalse();
        assertThat(webhook.getSecret()).isEqualTo("original");
    }

    @Test
    void update_shouldReplaceSecretWhenProvided() {
        UUID projectId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        Project project = project(projectId);
        AlertWebhook webhook = new AlertWebhook(project, "https://old.test", "original", true);
        setField(webhook, "id", webhookId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(webhookRepository.findById(webhookId)).thenReturn(Optional.of(webhook));
        when(webhookRepository.save(any(AlertWebhook.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(projectId, webhookId, new UpdateAlertWebhookRequest("https://new.test", "rotated", true));

        assertThat(webhook.getSecret()).isEqualTo("rotated");
    }

    @Test
    void get_shouldRejectWebhookFromAnotherProject() {
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        AlertWebhook webhook = new AlertWebhook(project(otherProjectId), "https://x.test", null, true);
        setField(webhook, "id", webhookId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(projectId)));
        when(webhookRepository.findById(webhookId)).thenReturn(Optional.of(webhook));

        assertThatThrownBy(() -> service.get(projectId, webhookId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to project");
    }

    @Test
    void delete_shouldRemoveWebhook() {
        UUID projectId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        AlertWebhook webhook = new AlertWebhook(project(projectId), "https://x.test", null, true);
        setField(webhook, "id", webhookId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(projectId)));
        when(webhookRepository.findById(webhookId)).thenReturn(Optional.of(webhook));

        service.delete(projectId, webhookId);

        verify(webhookRepository).delete(webhook);
    }

    @Test
    void delete_shouldNotDeleteWhenWebhookMissing() {
        UUID projectId = UUID.randomUUID();
        UUID webhookId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(projectId)));
        when(webhookRepository.findById(webhookId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(projectId, webhookId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Webhook not found");
        verify(webhookRepository, never()).delete(any());
    }

    @Test
    void list_shouldMapToViewsWithoutSecret() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId);
        AlertWebhook w = new AlertWebhook(project, "https://x.test", "sec", true);
        setField(w, "id", UUID.randomUUID());

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(webhookRepository.findByProjectOrderByCreatedAtAsc(project)).thenReturn(List.of(w));

        List<AlertWebhookView> views = service.list(projectId);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).hasSecret()).isTrue();
    }

    private Project project(UUID id) {
        Project project = new Project("project-" + id);
        setField(project, "id", id);
        return project;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
