package com.vis4srd.verification;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DoctorRoleMigrationTest {
    @Test void extendsEnumWithoutDroppingExistingRolesAndIsIdempotent() {
        String old = "enum('admin','user','legacy')";
        String upgraded = DoctorVerificationConfig.extendRoleType(old);
        assertThat(upgraded).isEqualTo("enum('admin','user','legacy','researcher','doctor')");
        assertThat(DoctorVerificationConfig.extendRoleType(upgraded)).isEqualTo(upgraded);
    }
    @Test void preservesWideColumnsAndRejectsUnexpectedTypes() {
        assertThat(DoctorVerificationConfig.extendRoleType("varchar(8)")).isEqualTo("varchar(32)");
        assertThat(DoctorVerificationConfig.extendRoleType("varchar(100)")).isEqualTo("varchar(100)");
        assertThatThrownBy(() -> DoctorVerificationConfig.extendRoleType("int")).isInstanceOf(IllegalStateException.class);
    }
}
