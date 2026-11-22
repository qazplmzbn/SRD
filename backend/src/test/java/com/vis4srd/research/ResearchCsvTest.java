package com.vis4srd.research;

import com.vis4srd.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ResearchCsvTest {
    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file","sample.csv","text/csv",content.getBytes(StandardCharsets.UTF_8));
    }
    @Test void mapsByNameAndDefaultsMissingOptions() {
        var rows=ResearchCsv.read(csv("\uFEFF分类,正文\r\n1,\"引号,换行\n第二行\""),"正文","分类","","");
        assertEquals(new ResearchVersionController.Sample("row-1","引号,换行\n第二行",1,"TEST"),rows.get(0));
    }
    @Test void validatesMappingAndBinaryLabels() {
        var data=csv("正文,分类\n示例,2");
        assertThrows(BusinessException.class,()->ResearchCsv.read(data,"正文","分类","",""));
        assertThrows(BusinessException.class,()->ResearchCsv.read(data,"正文","标签","",""));
        assertThrows(BusinessException.class,()->ResearchCsv.read(data,"正文","","",""));
    }
    @Test void readsChineseExcelEncodings() {
        for (String encoding : new String[]{"GBK", "UTF-16"}) {
            var file = new MockMultipartFile("file","excel.csv","text/csv","贴文,分类\r\n中文内容,0".getBytes(java.nio.charset.Charset.forName(encoding)));
            assertEquals("中文内容",ResearchCsv.read(file,"贴文","分类","","").get(0).text());
        }
    }
    @Test void reportsMalformedQuotesSeparately() {
        var error=assertThrows(BusinessException.class,()->ResearchCsv.read(csv("正文,分类\n\"未闭合,1"),"正文","分类","",""));
        assertTrue(error.getMessage().contains("双引号"));
    }
}