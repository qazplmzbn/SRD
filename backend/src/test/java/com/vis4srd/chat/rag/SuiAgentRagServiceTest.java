package com.vis4srd.chat.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuiAgentRagServiceTest {

    @Test
    void retrieveForChatShouldReturnEvidenceFromKnowledgeBase() {
        var props = new com.vis4srd.knowledge.config.KnowledgeProperties();
        props.setRootDir("../knowledge");
        var service = new SuiAgentRagService(props, new com.fasterxml.jackson.databind.ObjectMapper());

        var bundle = service.retrieveForChat("如何识别自杀风险并进行危机干预？", "intervention");

        assertFalse(bundle.evidence().isEmpty(), "应检索到证据片段");
        assertFalse(bundle.ragSources().isEmpty(), "应有来源文件");
        assertTrue(bundle.contextBlock().length() > 20, "上下文块应有内容");
    }
}
