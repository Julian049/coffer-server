package com.dev.julian09.financeserver;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import org.springframework.test.web.servlet.ResultMatcher;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;

/** The copy of docs/04-api/openapi.yml in src/test/resources/contract; refresh it when the contract changes. */
public final class Contract {

    private static final String SPEC = "contract/openapi.yml";

    private static final OpenApiInteractionValidator STRICT = OpenApiInteractionValidator.createForSpecificationUrl(SPEC).build();

    /*
     * The sync changes are oneOf + discriminator over allOf compositions. The validator injects additionalProperties=false
     * into each allOf part (so every part rejects the others' properties) and cannot follow the discriminator mapping.
     * Types, required members, enums, consts, patterns and formats are still validated; only those two are relaxed,
     * and only for /sync.
     */
    private static final OpenApiInteractionValidator COMPOSED = OpenApiInteractionValidator.createForSpecificationUrl(SPEC)
            .withLevelResolver(LevelResolver.create()
                    .withLevel("validation.schema.additionalProperties", ValidationReport.Level.IGNORE)
                    // The discriminator mapping points at allOf schemas the validator cannot resolve; oneOf still
                    // requires exactly one entity schema to match (each pins its entity with const)
                    .withLevel("validation.response.body.schema.discriminator", ValidationReport.Level.IGNORE)
                    .withLevel("validation.request.body.schema.discriminator", ValidationReport.Level.IGNORE)
                    .build())
            .build();

    private Contract() {
    }

    /** Request and response both match the contract, with no undeclared properties. */
    public static ResultMatcher matches() {
        return openApi().isValid(STRICT);
    }

    /** For the /sync endpoints, whose schemas are compositions (see COMPOSED). */
    public static ResultMatcher matchesComposed() {
        return openApi().isValid(COMPOSED);
    }
}
