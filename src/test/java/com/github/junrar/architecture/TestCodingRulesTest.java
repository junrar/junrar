package com.github.junrar.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.github.junrar", importOptions = {ImportOption.OnlyIncludeTests.class})
class TestCodingRulesTest {

    @ArchTest
    public static final ArchRule no_junit_assertions = noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.junit.jupiter.api.Assertions");
}
