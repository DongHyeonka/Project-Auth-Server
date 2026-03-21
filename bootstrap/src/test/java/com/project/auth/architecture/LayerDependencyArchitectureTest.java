package com.project.auth.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = {"com.project.auth", "com.project.authmigration"},
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class LayerDependencyArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_spring_web_or_jpa =
            noClasses()
                    .that().resideInAnyPackage("com.project.auth.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "org.hibernate..",
                            "jakarta.persistence..",
                            "jakarta.servlet.."
                    );

    @ArchTest
    static final ArchRule application_must_not_depend_on_presentation_or_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("com.project.auth.application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.project.auth.presentation..",
                            "com.project.auth.infrastructure..",
                            "jakarta.servlet..",
                            "org.springframework.web.."
                    );

    @ArchTest
    static final ArchRule presentation_must_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("com.project.auth.presentation..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.project.auth.infrastructure..");

    @ArchTest
    static final ArchRule bootstrap_is_the_only_layer_that_may_depend_on_config_packages =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.project.auth.domain..",
                            "com.project.auth.application..",
                            "com.project.auth.presentation..",
                            "com.project.auth.infrastructure.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage("com.project.auth.config..");
}
