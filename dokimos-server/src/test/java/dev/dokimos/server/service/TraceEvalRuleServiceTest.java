package dev.dokimos.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dokimos.server.dto.v1.CreateTraceEvalRuleRequest;
import dev.dokimos.server.dto.v1.TraceEvalRuleView;
import dev.dokimos.server.entity.LlmConnection;
import dev.dokimos.server.entity.TraceEvalRule;
import dev.dokimos.server.entity.TraceMatchType;
import dev.dokimos.server.repository.LlmConnectionRepository;
import dev.dokimos.server.repository.ProjectRepository;
import dev.dokimos.server.repository.TraceEvalRuleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TraceEvalRuleServiceTest {

    @Mock
    private TraceEvalRuleRepository ruleRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private LlmConnectionRepository connectionRepository;

    private TraceEvalRuleService service;
    private UUID projectId;
    private UUID connectionId;
    private LlmConnection connection;

    @BeforeEach
    void setUp() {
        service = new TraceEvalRuleService(ruleRepository, projectRepository, connectionRepository);
        projectId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
        connection = new LlmConnection("conn", "https://api.example.com", "gpt-4");
    }

    private CreateTraceEvalRuleRequest spanNameRequest() {
        return new CreateTraceEvalRuleRequest(
                "answer-quality",
                null,
                TraceMatchType.SPAN_NAME,
                null,
                "llm.generate",
                connectionId,
                "judge",
                "is correct",
                0.0,
                1.0,
                0.5);
    }

    @Test
    void createPersistsRuleWithDefaults() {
        when(projectRepository.findById(eq(projectId), any()))
                .thenReturn(Optional.of(new dev.dokimos.server.entity.Project("p")));
        when(ruleRepository.existsByProjectIdAndName(eq(projectId), eq("answer-quality"), any()))
                .thenReturn(false);
        when(connectionRepository.findById(eq(connectionId), any())).thenReturn(Optional.of(connection));
        when(ruleRepository.save(any(TraceEvalRule.class))).thenAnswer(inv -> inv.getArgument(0));

        TraceEvalRuleView view =
                service.create(projectId, spanNameRequest(), dev.dokimos.server.tenant.TenantScope.unrestricted());

        assertThat(view.name()).isEqualTo("answer-quality");
        assertThat(view.enabled()).isTrue();
        assertThat(view.matchType()).isEqualTo(TraceMatchType.SPAN_NAME);

        ArgumentCaptor<TraceEvalRule> captor = ArgumentCaptor.forClass(TraceEvalRule.class);
        verify(ruleRepository).save(captor.capture());
        assertThat(captor.getValue().getMatchKey()).isNull();
        assertThat(captor.getValue().getThreshold()).isEqualTo(0.5);
    }

    @Test
    void createClearsMatchKeyForSpanNameMatch() {
        CreateTraceEvalRuleRequest request = new CreateTraceEvalRuleRequest(
                "r", null, TraceMatchType.SPAN_NAME, "ignored", "llm.generate", connectionId, "j", "c", 0.0, 1.0, null);
        when(projectRepository.findById(eq(projectId), any()))
                .thenReturn(Optional.of(new dev.dokimos.server.entity.Project("p")));
        when(ruleRepository.existsByProjectIdAndName(eq(projectId), eq("r"), any()))
                .thenReturn(false);
        when(connectionRepository.findById(eq(connectionId), any())).thenReturn(Optional.of(connection));
        when(ruleRepository.save(any(TraceEvalRule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(projectId, request, dev.dokimos.server.tenant.TenantScope.unrestricted());

        ArgumentCaptor<TraceEvalRule> captor = ArgumentCaptor.forClass(TraceEvalRule.class);
        verify(ruleRepository).save(captor.capture());
        assertThat(captor.getValue().getMatchKey()).isNull();
    }

    @Test
    void createRejectsDuplicateName() {
        when(projectRepository.findById(eq(projectId), any()))
                .thenReturn(Optional.of(new dev.dokimos.server.entity.Project("p")));
        when(ruleRepository.existsByProjectIdAndName(eq(projectId), eq("answer-quality"), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                        projectId, spanNameRequest(), dev.dokimos.server.tenant.TenantScope.unrestricted()))
                .isInstanceOf(IllegalStateException.class);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void createRejectsMissingProject() {
        when(projectRepository.findById(eq(projectId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                        projectId, spanNameRequest(), dev.dokimos.server.tenant.TenantScope.unrestricted()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsMissingConnection() {
        when(projectRepository.findById(eq(projectId), any()))
                .thenReturn(Optional.of(new dev.dokimos.server.entity.Project("p")));
        when(ruleRepository.existsByProjectIdAndName(eq(projectId), eq("answer-quality"), any()))
                .thenReturn(false);
        when(connectionRepository.findById(eq(connectionId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                        projectId, spanNameRequest(), dev.dokimos.server.tenant.TenantScope.unrestricted()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
