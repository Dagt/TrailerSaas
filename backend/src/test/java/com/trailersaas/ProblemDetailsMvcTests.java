package com.trailersaas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(ProblemDetailsMvcTests.MvcConfiguration.class)
@WebAppConfiguration
class ProblemDetailsMvcTests {
    @Autowired
    private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestCorrelationFilter()).build();
    }

    @Test
    void domainAdvicePreservesSemanticsWithAutomaticCorrelation() throws Exception {
        mvc.perform(get("/test/domain-error").header(RequestCorrelationFilter.HEADER, "domain-mvc"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.trailersaas.example/problems/test-domain-error"))
                .andExpect(jsonPath("$.title").value("Test domain error"))
                .andExpect(jsonPath("$.detail").value("Safe domain detail"))
                .andExpect(jsonPath("$.code").value("TEST_DOMAIN_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("exampleField"))
                .andExpect(jsonPath("$.errors[0].message").value("safe validation message"))
                .andExpect(jsonPath("$.traceId").value("domain-mvc"));
    }

    @ParameterizedTest
    @CsvSource({"/test/failure,500", "/test/error-response,422",
            "/test/parameter?value=synthetic-secret-token,400"})
    void untrustedInfrastructureStillUsesGenericProblems(String path, int status) throws Exception {
        var response = mvc.perform(get(path)
                        .header(RequestCorrelationFilter.HEADER, "safe-mvc")
                        .header("Authorization", "Bearer synthetic-secret-token"))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.traceId").value("safe-mvc"))
                .andExpect(jsonPath("$.detail").value(status >= 500
                        ? "The server could not complete the request." : "The request could not be completed."))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("synthetic-secret-token", "SQL", "password");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({ProblemDetailsConfiguration.class, InfrastructureExceptionHandler.class,
            HttpObservabilityTests.TestEndpoints.class})
    static class MvcConfiguration {}
}
