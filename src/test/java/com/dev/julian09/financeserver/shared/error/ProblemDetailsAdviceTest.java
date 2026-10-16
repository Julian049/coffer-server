package com.dev.julian09.financeserver.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemDetailsAdviceTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ProblemDetailsAdvice())
            .build();

    @Test
    void apiExceptionKeepsItsCodeAndDetail() throws Exception {
        expectProblem(mvc.perform(get("/conflict")), 409, "FIN-CONFLICT")
                .andExpect(jsonPath("$.type").value("https://finance.app/problems/conflict"))
                .andExpect(jsonPath("$.title").value("El recurso ya existe"))
                .andExpect(jsonPath("$.detail").value("El usuario ya existe"));
    }

    @Test
    void invalidBodyListsTheFieldErrors() throws Exception {
        expectProblem(mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")),
                400, "FIN-VALIDATION")
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void malformedJsonIsAValidationProblem() throws Exception {
        expectProblem(mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{")),
                400, "FIN-VALIDATION");
    }

    @Test
    void unknownRouteGetsACode() throws Exception {
        expectProblem(mvc.perform(get("/nope")), 404, "FIN-VALIDATION");
    }

    @Test
    void integrityViolationIsAConflict() throws Exception {
        expectProblem(mvc.perform(get("/duplicate")), 409, "FIN-CONFLICT")
                .andExpect(content().string(not(containsString("local_id"))));
    }

    @Test
    void unexpectedErrorHidesInternals() throws Exception {
        expectProblem(mvc.perform(get("/boom")), 500, "FIN-INTERNAL")
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(content().string(not(containsString("secret"))));
    }

    private static ResultActions expectProblem(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    @RestController
    static class ThrowingController {

        record Item(@NotBlank String name) {}

        @GetMapping("/conflict")
        void conflict() {
            throw new ApiException(ErrorCode.CONFLICT, "El usuario ya existe");
        }

        @PostMapping("/items")
        void create(@Valid @RequestBody Item item) {
        }

        @GetMapping("/duplicate")
        void duplicate() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint on local_id");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret connection string");
        }
    }
}
