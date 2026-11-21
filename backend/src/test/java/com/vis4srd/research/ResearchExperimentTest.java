package com.vis4srd.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.security.AuthUser;
import com.vis4srd.model.service.ModelService;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResearchExperimentTest {
    JdbcTemplate jdbc; ResearchVersionService versions; ResearchExperimentService service; ModelService models;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(ds);
        new ResourceDatabasePopulator(new ClassPathResource("db/research-versions.sql"),new ClassPathResource("db/research-experiments.sql")).execute(ds);
        jdbc.execute("CREATE TABLE app_users(id BIGINT PRIMARY KEY,username VARCHAR(30),role VARCHAR(30),is_active BOOLEAN)");
        jdbc.update("INSERT INTO app_users VALUES(1,'owner','researcher',true),(2,'reader','doctor',true),(3,'admin','admin',true)");
        versions=new ResearchVersionService(jdbc,new ObjectMapper());models=mock(ModelService.class);service=new ResearchExperimentService(jdbc,new ObjectMapper(),versions,models);
        when(models.getModelForInference(7)).thenReturn(new HashMap<>(Map.of("model_name","synthetic","model_code","synthetic-v1","model_type","api","api_key","SECRET-DO-NOT-EXPOSE","api_base_url","https://internal.example","temperature",0.8)));
        login(1,"researcher");
    }
    @AfterEach void cleanup() throws Exception {SecurityContextHolder.clearContext();try(var c=jdbc.getDataSource().getConnection();var s=c.createStatement()){s.execute("SHUTDOWN");}}
    void login(long id,String role) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(AuthUser.builder().userId(id).role(role).build(),null,List.of()));}
    String version() {return versions.create(new ResearchVersionController.Create("synthetic","v1","synthetic only","test",true,List.of(
        new ResearchVersionController.Sample("train","training must not be sent",1,"TRAIN"),
        new ResearchVersionController.Sample("a","tp",1,"TEST"),new ResearchVersionController.Sample("b","tn",0,"TEST"),
        new ResearchVersionController.Sample("c","fp",0,"TEST"),new ResearchVersionController.Sample("d","fn",1,"TEST"),
        new ResearchVersionController.Sample("e","invalid",1,"TEST"),new ResearchVersionController.Sample("f","upstream",0,"TEST"))));}
    String create(String version) {return service.create(new ResearchExperimentController.Create(version,7,true));}
    void outputByText() {
        when(models.callApiModel(anyMap(),anyString(),anyString())).thenAnswer(invocation -> {
            String text=invocation.getArgument(1);return switch(text) {
                case "tp","fp" -> Map.of("success",true,"response","{\"label\":1}");
                case "tn","fn" -> Map.of("success",true,"response","{\"label\":0}");
                case "upstream" -> Map.of("success",false,"error","SECRET-DO-NOT-EXPOSE");
                default -> Map.of("success",true,"response","{\"label\":0} trailing");
            };
        });
    }
    @SuppressWarnings("unchecked")
    @Test void computesMetricsFromValidTestPredictionsAndKeepsFailuresSeparate() {
        String id=create(version());outputByText();service.runNext();var detail=service.detail(id);
        assertThat(detail.get("status")).isEqualTo("PARTIAL_FAILED");assertThat(detail.get("completed")).isEqualTo(6);
        var metrics=(Map<String,Object>)detail.get("metrics");assertThat(metrics).containsEntry("tp",1).containsEntry("tn",1).containsEntry("fp",1).containsEntry("fn",1).containsEntry("failed",2).containsEntry("pending",0).containsEntry("precision",0.5).containsEntry("recall",0.5).containsEntry("f1",0.5).containsEntry("coverage",4.0/6);
        assertThat(detail.toString()).doesNotContain("SECRET-DO-NOT-EXPOSE","internal.example");
        verify(models,never()).callApiModel(anyMap(),eq("training must not be sent"),anyString());verify(models,times(6)).callApiModel(argThat(c -> c.get("temperature").equals(0.0)),anyString(),anyString());
        service.runNext();verify(models,times(6)).callApiModel(anyMap(),anyString(),anyString());
    }
    @Test void grantRevocationStopsQueuedTaskAndHidesExistingResults() {
        String v=version();versions.grant(v,new ResearchVersionController.Grant(2,false));login(2,"doctor");String id=create(v);
        login(3,"admin");assertThatThrownBy(()->service.detail(id)).isInstanceOf(BusinessException.class);
        login(1,"researcher");versions.grant(v,new ResearchVersionController.Grant(2,true));service.runNext();
        login(2,"doctor");assertThatThrownBy(()->service.detail(id)).isInstanceOf(BusinessException.class);assertThat(service.list(1).get("total")).isEqualTo(0L);
        verify(models,never()).callApiModel(anyMap(),anyString(),anyString());
        assertThat(jdbc.queryForObject("SELECT status FROM research_experiments WHERE id=?",String.class,id)).isEqualTo("FAILED");
    }
    @Test void changedModelCannotSilentlyRunUnderOriginalSnapshot() {
        String id=create(version());when(models.getModelForInference(7)).thenReturn(Map.of("model_name","changed","model_type","api"));service.runNext();
        assertThat(service.detail(id).get("status")).isEqualTo("FAILED");verify(models,never()).callApiModel(anyMap(),anyString(),anyString());
    }
    @Test void cancellationDuringRequestDiscardsResponseAndStopsFurtherCalls() {
        String id=create(version());when(models.callApiModel(anyMap(),anyString(),anyString())).thenAnswer(invocation -> {service.cancel(id);return Map.of("success",true,"response","{\"label\":1}");});
        service.runNext();assertThat(service.detail(id).get("status")).isEqualTo("CANCELLED");assertThat(service.detail(id).get("completed")).isEqualTo(0);
        verify(models,times(1)).callApiModel(anyMap(),anyString(),anyString());
    }
    @Test void restartMarksRunningFailedWithoutRetryingAndKeepsQueuedJobs() {
        String v=version(),running=create(v),queued=create(v);jdbc.update("UPDATE research_experiments SET status='RUNNING' WHERE id=?",running);
        service.interruptUnfinished();assertThat(service.detail(running).get("status")).isEqualTo("FAILED");assertThat(service.detail(queued).get("status")).isEqualTo("QUEUED");
        service.cancel(queued);service.runNext();verify(models,never()).callApiModel(anyMap(),anyString(),anyString());
    }
    @Test void rejectsMissingConsentTrainingOnlyAndExcessQueuedTasks() {
        String v=version();assertThatThrownBy(()->service.create(new ResearchExperimentController.Create(v,7,false))).isInstanceOf(BusinessException.class);
        String train=versions.create(new ResearchVersionController.Create("training","v1","synthetic","test",true,List.of(new ResearchVersionController.Sample("only","training",1,"TRAIN"))));
        assertThatThrownBy(()->create(train)).isInstanceOf(BusinessException.class);
        create(v);create(v);create(v);assertThatThrownBy(()->create(v)).isInstanceOf(BusinessException.class);
        login(2,"user");assertThatThrownBy(()->service.list(1)).isInstanceOf(BusinessException.class);
    }
    @Test void undefinedMetricsAreNullAndAllNegativeAccuracyIsNotPositiveRecall() {
        assertThat(ResearchExperimentService.metrics(List.of(),3)).containsEntry("accuracy",null).containsEntry("recall",null).containsEntry("coverage",0.0).containsEntry("pending",3);
        assertThat(ResearchExperimentService.metrics(List.of(Map.of("expected",0,"predicted",0)),1)).containsEntry("accuracy",1.0).containsEntry("precision",null).containsEntry("recall",null).containsEntry("f1",null);
    }
}
