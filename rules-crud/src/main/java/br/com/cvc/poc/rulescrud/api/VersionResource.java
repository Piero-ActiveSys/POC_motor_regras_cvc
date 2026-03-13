package br.com.cvc.poc.rulescrud.api;

import br.com.cvc.poc.contracts.PublishResponse;
import br.com.cvc.poc.rulescrud.application.PublishService;
import br.com.cvc.poc.rulescrud.infra.db.RulesetEntity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

@Path("/rulesets/{rulesetId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VersionResource {

    @Inject PublishService publishService;

    /**
     * Reactivate a previously published version.
     * Validates artifacts exist on storage and emits a RULESET_VERSION_ACTIVATED event.
     */
    @POST
    @Path("/activate-version/{version}")
    @Transactional
    public PublishResponse activateVersion(
            @PathParam("rulesetId") UUID rulesetId,
            @PathParam("version") long version,
            @QueryParam("requestedBy") @DefaultValue("system") String requestedBy
    ) {
        var rs = RulesetEntity.findById(rulesetId);
        if (rs == null) throw new NotFoundException("ruleset not found");
        return publishService.activateVersion(rulesetId, version, requestedBy);
    }

    /**
     * Convenience alias: rollback to the previous version (current - 1).
     */
    @POST
    @Path("/rollback")
    @Transactional
    public PublishResponse rollback(
            @PathParam("rulesetId") UUID rulesetId,
            @QueryParam("requestedBy") @DefaultValue("system") String requestedBy
    ) {
        var rs = RulesetEntity.findById(rulesetId);
        if (rs == null) throw new NotFoundException("ruleset not found");

        // Find the current (latest) version, then activate version - 1
        var latest = br.com.cvc.poc.rulescrud.infra.db.RulesetVersionEntity
                .find("rulesetId = ?1 order by version desc", rulesetId)
                .firstResult();
        if (latest == null) throw new NotFoundException("no versions found for ruleset");

        var latestVer = (br.com.cvc.poc.rulescrud.infra.db.RulesetVersionEntity) latest;
        long targetVersion = latestVer.version - 1;
        if (targetVersion < 1) throw new BadRequestException("no previous version to rollback to");

        return publishService.activateVersion(rulesetId, targetVersion, requestedBy);
    }
}

