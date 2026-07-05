package com.databytes.screening;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The "is it alive?" endpoint for the hello-world skeleton.
 *
 * <p>This is your starting point for Day 10. The real service has no inbound REST of its own
 * beyond a read-only {@code GET /screenings} view — its job is to <em>consume</em>
 * {@code applicant-registered} events from Kafka and run a background check. You build that
 * {@code @Incoming} consumer in the brief; see {@code apps/screening-service} for the finished
 * version.
 */
@Path("/hello")
public class GreetingResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Screening Service is up — hello, world. Next: consume applicant-registered (see the Day 10 brief).";
    }
}
