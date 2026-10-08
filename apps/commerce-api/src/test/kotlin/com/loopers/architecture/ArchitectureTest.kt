package com.loopers.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.base.DescribedPredicate.alwaysTrue
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import kotlin.test.Test

class ArchitectureTest {
    @Test
    fun respectsLayerDependencies() {
        val classes = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.loopers")

        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..interfaces..", "..application..", "..infrastructure..")
            .check(classes)

        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..interfaces..", "..infrastructure..")
            .check(classes)

        noClasses().that().resideInAPackage("..interfaces..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .check(classes)
    }

    /**
     * 도메인끼리는 서로 부르지 않는다. 조합이 필요하면 application 이 엮는다.
     *
     * 계층 규칙은 방향만 보므로 `domain.order` 가 `domain.product` 를 부르는 것을 잡지 못한다.
     * 같은 계층이기 때문이다. 실제로 그렇게 쌓였다가 한 번 걷어냈고, 다시 생기면 여기서 막힌다.
     *
     * `domain.shared` 는 뺀다. `Money` 처럼 모든 도메인이 쓰는 값이다.
     */
    @Test
    fun domainsDoNotDependOnEachOther() {
        val classes = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.loopers.domain")

        slices()
            .matching("com.loopers.domain.(*)..")
            .should().notDependOnEachOther()
            .ignoreDependency(alwaysTrue(), resideInAPackage("com.loopers.domain.shared.."))
            .check(classes)
    }
}
