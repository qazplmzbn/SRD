package com.vis4srd.verification;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DoctorVerificationConfig {
    @Bean
    public DataSourceInitializer doctorVerificationSchema(@Qualifier("mysqlDataSource") DataSource source) {
        // Legacy installations use ENUM('admin','user'). Extend its allowed values without
        // changing any account role or removing existing enum entries/default/nullability.
        var jdbc = new JdbcTemplate(source);
        var columns = jdbc.queryForList("SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_users' AND COLUMN_NAME='role'");
        if (columns.size() != 1) throw new IllegalStateException("app_users.role is required before doctor verification migration");
        var column = columns.get(0);
        String type = column.get("COLUMN_TYPE").toString();
        String declaration = extendRoleType(type);
        if (!declaration.equals(type)) {
            String defaultRole = column.get("COLUMN_DEFAULT") == null ? null : column.get("COLUMN_DEFAULT").toString();
            if (defaultRole != null && !defaultRole.matches("[a-zA-Z0-9_]+")) throw new IllegalStateException("Unsupported role default; migrate manually");
            String nullable = "YES".equals(column.get("IS_NULLABLE")) ? " NULL" : " NOT NULL";
            jdbc.execute("ALTER TABLE app_users MODIFY COLUMN role " + declaration + nullable + (defaultRole == null ? "" : " DEFAULT '" + defaultRole + "'"));
        }
        var initializer = new DataSourceInitializer();
        initializer.setDataSource(source);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("db/doctor-verification.sql")));
        return initializer;
    }

    static String extendRoleType(String type) {
        if (type.startsWith("enum(")) {
            if (!type.matches("enum\\('[a-zA-Z0-9_]+'(?:,'[a-zA-Z0-9_]+')*\\)")) throw new IllegalStateException("Unsupported role enum; migrate manually");
            String expanded = type.substring(0, type.length() - 1);
            for (String role : new String[]{"researcher", "doctor"}) if (!type.contains("'" + role + "'")) expanded += ",'" + role + "'";
            return expanded + ")";
        }
        if (type.matches("varchar\\(\\d+\\)")) {
            int size = Integer.parseInt(type.substring(8, type.length()-1));
            return size < 32 ? "varchar(32)" : type;
        }
        throw new IllegalStateException("Unsupported app_users.role type; migrate manually");
    }
}
