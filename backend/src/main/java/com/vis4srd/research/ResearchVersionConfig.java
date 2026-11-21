package com.vis4srd.research;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.*;
@Configuration
public class ResearchVersionConfig {
 @Bean public DataSourceInitializer researchVersionSchema(@Qualifier("mysqlDataSource") DataSource source) {
  var init=new DataSourceInitializer(); init.setDataSource(source);
  init.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("db/research-versions.sql"),new ClassPathResource("db/research-experiments.sql"))); return init;
 }
}
