package com.fitplan.rag.agent;

import com.fitplan.rag.safety.FitnessRiskAssessmentService;
import com.fitplan.rag.safety.FitnessRiskClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SafetyGuardAgentTest {

    private FitnessRiskClassifier riskClassifier;
    private SafetyGuardAgent agent;

    @BeforeEach
    void setUp() {
        riskClassifier = mock(FitnessRiskClassifier.class);
        when(riskClassifier.classify(any())).thenReturn(FitnessRiskClassifier.RiskDecision.safe());
        agent = new SafetyGuardAgent(new FitnessRiskAssessmentService(), riskClassifier);
    }

    @Test
    void blocksUrgentRuleWithoutCallingClassifier() {
        SafetyGuardAgent.SafetyDecision decision = agent.assess("我跑步时胸口很痛");

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.level()).isEqualTo(FitnessRiskClassifier.RiskLevel.URGENT);
        assertThat(decision.userMessage()).contains("停止训练", "04-recovery-safety.md");
        assertThat(decision.toolCalls()).containsExactly("checkRedFlagRules");
        verify(riskClassifier, never()).classify(any());
    }

    @Test
    void blocksMedicalBoundaryRuleWithoutCallingClassifier() {
        SafetyGuardAgent.SafetyDecision decision = agent.assess("请诊断我的膝盖疼痛是什么病");

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.level()).isEqualTo(FitnessRiskClassifier.RiskLevel.MEDICAL_BOUNDARY);
        assertThat(decision.userMessage()).contains("不能进行疾病诊断");
        assertThat(decision.toolCalls()).containsExactly("checkRedFlagRules");
        verify(riskClassifier, never()).classify(any());
    }

    @Test
    void passesOrdinaryRequestThroughBothToolsInOrder() {
        SafetyGuardAgent.SafetyDecision decision = agent.assess("RPE 8 表示什么意思？");

        assertThat(decision.blocked()).isFalse();
        assertThat(decision.level()).isEqualTo(FitnessRiskClassifier.RiskLevel.SAFE);
        assertThat(decision.toolCalls()).containsExactly("checkRedFlagRules", "classifyRisk");
        verify(riskClassifier).classify("RPE 8 表示什么意思？");
    }

    @Test
    void blocksWhenClassifierReportsUrgent() {
        when(riskClassifier.classify(any())).thenReturn(new FitnessRiskClassifier.RiskDecision(
                FitnessRiskClassifier.RiskLevel.URGENT,
                "疑似红旗",
                true,
                false));

        SafetyGuardAgent.SafetyDecision decision = agent.assess("跑步时心口像压着一块石头，还冒冷汗");

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.level()).isEqualTo(FitnessRiskClassifier.RiskLevel.URGENT);
        assertThat(decision.userMessage()).contains("停止训练", "医疗评估");
        assertThat(decision.toolCalls()).containsExactly("checkRedFlagRules", "classifyRisk");
    }

    @Test
    void fallsBackToClarifyWhenClassifierUnavailable() {
        when(riskClassifier.classify(any())).thenReturn(FitnessRiskClassifier.RiskDecision.classifierUnavailable());

        SafetyGuardAgent.SafetyDecision decision = agent.assess("练完以后感觉很不舒服");

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.level()).isEqualTo(FitnessRiskClassifier.RiskLevel.CLARIFY);
        assertThat(decision.userMessage()).contains("信息还不够明确", "暂停增加训练强度");
        assertThat(decision.toolCalls()).containsExactly("checkRedFlagRules", "classifyRisk");
    }

    @Test
    void exposesAgentMetadataAndTools() {
        assertThat(agent.id()).isEqualTo("safety-guard");
        assertThat(agent.name()).isEqualTo("安全审查 Agent");
        assertThat(agent.description()).contains("双层兜底");
        assertThat(agent.toolNames()).containsExactly("checkRedFlagRules", "classifyRisk");
    }
}
