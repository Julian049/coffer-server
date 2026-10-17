package com.dev.julian09.financeserver;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/** Module rules of detailed-design §6.4. Empty modules pass until their sprint fills them. */
@AnalyzeClasses(packages = ArchitectureTest.BASE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE = "com.dev.julian09.financeserver";
    private static final String SHARED = BASE + ".shared..";
    private static final String CONFIG = BASE + ".config..";
    private static final String IDENTITY = BASE + ".identity..";
    private static final String LEDGER = BASE + ".ledger..";
    private static final String SYNC = BASE + ".sync..";
    private static final String LEGACY = BASE + ".legacy..";

    @ArchTest
    static final ArchRule ledgerDoesNotDependOnSyncOrIdentity = noClasses()
            .that().resideInAPackage(LEDGER)
            .should().dependOnClassesThat().resideInAnyPackage(SYNC, IDENTITY)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule identityOnlyUsesShared = noClasses()
            .that().resideInAPackage(IDENTITY)
            .should().dependOnClassesThat().resideInAnyPackage(LEDGER, SYNC)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sharedDependsOnNoModule = noClasses()
            .that().resideInAPackage(SHARED)
            .should().dependOnClassesThat().resideInAnyPackage(CONFIG, IDENTITY, LEDGER, SYNC, LEGACY);

    // Wiring points at the modules, never the other way around (detailed-design §6.4)
    @ArchTest
    static final ArchRule modulesDoNotDependOnConfig = noClasses()
            .that().resideInAnyPackage(SHARED, IDENTITY, LEDGER, SYNC)
            .should().dependOnClassesThat().resideInAPackage(CONFIG);

    @ArchTest
    static final ArchRule newModulesIgnoreLegacy = noClasses()
            .that().resideInAnyPackage(SHARED, CONFIG, IDENTITY, LEDGER, SYNC)
            .should().dependOnClassesThat().resideInAPackage(LEGACY);

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAssignableTo(Repository.class);

    // ADR-0004: money is BigDecimal, never binary floating point
    @ArchTest
    static final ArchRule noFloatingPointInLedger = noFields()
            .that().areDeclaredInClassesThat().resideInAPackage(LEDGER)
            .should().haveRawType(double.class).orShould().haveRawType(Double.class)
            .orShould().haveRawType(float.class).orShould().haveRawType(Float.class)
            .allowEmptyShould(true);
}
