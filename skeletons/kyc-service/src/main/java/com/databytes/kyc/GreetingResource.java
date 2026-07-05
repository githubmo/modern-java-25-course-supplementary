package com.databytes.kyc;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The "is it alive?" endpoint for the hello-world skeleton.
 *
 * <p>This is your starting point for Day 08. Once the skeleton runs, you replace this with the
 * real {@code ApplicantResource} from the brief: a {@code POST /applicants} that validates a
 * {@code RegisterApplicantRequest} and a {@code GET /applicants/&#123;id&#125;}. See
 * {@code apps/kyc-service} for the finished version.
 */
@Path("/hello")
public class GreetingResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "KYC Service is up — hello, world. Next: build /applicants (see the Day 08 brief).";
    }
}
