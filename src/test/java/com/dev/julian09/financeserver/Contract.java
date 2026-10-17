package com.dev.julian09.financeserver;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import org.springframework.test.web.servlet.ResultMatcher;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;

/** The copy of docs/04-api/openapi.yml in src/test/resources/contract; refresh it when the contract changes. */
public final class Contract {

    private static final OpenApiInteractionValidator VALIDATOR =
            OpenApiInteractionValidator.createForSpecificationUrl("contract/openapi.yml").build();

    private Contract() {
    }

    /** Request and response both match the contract. */
    public static ResultMatcher matches() {
        return openApi().isValid(VALIDATOR);
    }
}
