package com.glaciernotes.cloud.api;

import com.glaciernotes.cloud.generated.api.AdministrationApi;
import com.glaciernotes.cloud.generated.model.AdminStatus;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RolesAllowed("ADMIN")
public class AdministrationResource implements AdministrationApi {
    @Override
    public AdminStatus getAdminStatus() {
        return new AdminStatus()
            .service(AdminStatus.ServiceEnum.GLACIER_NOTES_CLOUD)
            .status(AdminStatus.StatusEnum.OK)
            .apiVersion(AdminStatus.ApiVersionEnum.V1)
            .database(AdminStatus.DatabaseEnum.UP);
    }
}
