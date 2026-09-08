package com.fitplan.rag.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FitnessRiskAssessmentServiceTest {

    private final FitnessRiskAssessmentService service = new FitnessRiskAssessmentService();

    @Test
    void blocksChestPainBeforeCallingTheModel() {
        FitnessRiskAssessmentService.RiskBlock block = service
                .assess("我跑步时胸痛，请安排高强度训练")
                .orElseThrow();

        assertThat(block.reason()).isEqualTo("chest-pain");
        assertThat(block.response()).contains("停止训练", "医生", "04-recovery-safety.md");
    }

    @Test
    void blocksMedicalDiagnosisRequests() {
        assertThat(service.assess("请诊断我的膝盖到底是什么病"))
                .hasValueSatisfying(block -> assertThat(block.reason()).isEqualTo("medical-diagnosis"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "训练后昏厥过一次，今天还能继续吗？",
            "走路时喘不上气，但我想做冲刺训练",
            "运动时心悸伴不适，帮我提高强度",
            "昨天扭伤脚踝，现在无法正常负重"
    })
    void blocksMedicalRedFlagVariants(String message) {
        assertThat(service.assess(message)).isPresent();
    }

    @Test
    void doesNotTreatExplicitlyNegatedSymptomsAsCurrentRedFlags() {
        assertThat(service.assess("我没有胸痛，也未出现严重气短，RPE 8 是什么意思？"))
                .isEmpty();
    }

    @Test
    void allowsOrdinaryFitnessQuestionsToReachTheAgent() {
        assertThat(service.assess("RPE 8 表示什么意思？")).isEmpty();
    }
}
