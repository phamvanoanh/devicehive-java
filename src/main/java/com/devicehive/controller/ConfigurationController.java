package com.devicehive.controller;

import com.devicehive.auth.HiveRoles;
import com.devicehive.configuration.ConfigurationService;
import com.devicehive.configuration.Constants;
import com.devicehive.json.strategies.JsonPolicyDef;
import com.devicehive.model.ApiInfo;
import com.devicehive.model.Configuration;
import com.devicehive.model.ErrorResponse;
import com.devicehive.model.Version;
import com.devicehive.service.TimestampService;
import com.devicehive.utils.LogExecutionTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.EJB;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Provide API information
 */
@LogExecutionTime
@Path("/config")
public class ConfigurationController {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationController.class);


    @EJB
    private ConfigurationService configurationService;


    @POST
    @RolesAllowed(HiveRoles.ADMIN)
    @Path("/set")
    public Response setProperty(@QueryParam("name") @NotNull String name, @QueryParam("value") String value) {
        logger.debug("Congiguration will be set. Property's name : {} value : {} ", name, value);
        configurationService.save(name, value);
        logger.debug("Congiguration will has been set. Property's name : {} value : {} ", name, value);
        return ResponseFactory.response(Response.Status.NO_CONTENT);
    }

    @GET
    @RolesAllowed(HiveRoles.ADMIN)
    @Path("/reload")
    public Response reloadConfig() {
        configurationService.notifyUpdateAll();
        return ResponseFactory.response(Response.Status.NO_CONTENT);
    }
}
