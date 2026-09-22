package com.fitplan.rag.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContraindicationServiceTest {

    private final ContraindicationService service = new ContraindicationService(new ObjectMapper());

    @Test
    void blocksRegisteredInjuryAndExercisePair() {
        ContraindicationService.Assessment assessment = service.assess("我有腰突，可以做硬拉吗？");

        assertThat(assessment.blocked()).isTrue();
        assertThat(assessment.injuries()).contains("腰椎间盘突出");
        assertThat(assessment.response()).contains("停止生成", "合格康复专业人士");
    }

    @Test
    void keepsConstraintsWhenOnlyInjuryIsMentioned() {
        ContraindicationService.Assessment assessment = service.assess("腰突患者应该如何安排训练？");

        assertThat(assessment.blocked()).isFalse();
        assertThat(assessment.hasInjury()).isTrue();
        assertThat(assessment.forbiddenTerms()).contains("硬拉", "仰卧起坐");
    }

    @Test
    void doesNotMatchNegatedInjury() {
        ContraindicationService.Assessment assessment = service.assess("我没有腰突，可以做硬拉吗？");

        assertThat(assessment.hasInjury()).isFalse();
        assertThat(assessment.blocked()).isFalse();
    }
}
