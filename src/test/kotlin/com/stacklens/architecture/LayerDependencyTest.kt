package com.stacklens.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

private val production: JavaClasses =
    ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.stacklens")

private val applicationPortsAndServices = "com.stacklens.application.."
private val outboundPorts = "com.stacklens.application.port.out.."
private val adapters = "com.stacklens.adapter.."
private val outboundAdapters = "com.stacklens.adapter.out.."
private val inboundAdapters = "com.stacklens.adapter.inbound.."
private val infrastructure = "com.stacklens.infrastructure.."

class LayerDependencyTest {
    @Test
    fun domainDependsOnNothingInsideTheProject() {
        domainRule().check(production)
    }

    @Test
    fun domainDoesNotReachForSpring() {
        noClasses()
            .that()
            .resideInAPackage("com.stacklens.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework..")
            .because("the domain model stays framework free")
            .check(production)
    }

    @Test
    fun applicationDependsOnNoAdapter() {
        applicationRule().check(production)
    }

    @Test
    fun applicationDependsOnNoInfrastructure() {
        noClasses()
            .that()
            .resideInAPackage(applicationPortsAndServices)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(infrastructure)
            .because("only the composition root may know wiring and properties")
            .check(production)
    }

    @Test
    fun inboundAdapterDependsOnNoOutboundAdapter() {
        inboundToOutboundRule().check(production)
    }

    @Test
    fun inboundAdapterDependsOnNoInfrastructure() {
        inboundToInfrastructureRule().check(production)
    }

    @Test
    fun outboundAdapterDependsOnNoInfrastructure() {
        outboundToInfrastructureRule().check(production)
    }

    @Test
    fun inboundAdapterReachesOutboundCapabilitiesOnlyThroughUseCases() {
        inboundToOutboundPortsRule().check(production)
    }

    @Test
    fun portContractsResideInTheApplicationCore() {
        portNamingRule().check(production)
    }

    @Test
    fun useCaseContractsResideInTheInboundPortPackage() {
        useCaseNamingRule().check(production)
    }

    @Test
    fun applicationServicesDependOnUseCasesOnlyByImplementingThem() {
        serviceUseCaseDependencyRule().check(production)
    }

    @Test
    fun applicationCoreDoesNotDependOnWebTransportTypes() {
        coreWebFreedomRule().check(production)
    }

    @Test
    fun useCasesDoNotExtendOutboundPorts() {
        useCasePortDirectionRule().check(production)
    }

    @Test
    fun outboundAdapterDoesNotDependOnInboundPorts() {
        noClasses()
            .that()
            .resideInAPackage(outboundAdapters)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.stacklens.application.port.inbound..")
            .because("driven adapters implement outbound ports and never call use cases")
            .check(production)
    }

    @Test
    fun outboundAdaptersStayWithinTheirOwnModuleOrSharedKernel() {
        crossOutboundAdapterRule().check(production)
    }

    @Test
    fun applicationServicesTranslateThroughTheOutboundI18nPort() {
        noClasses()
            .that()
            .resideInAPackage("com.stacklens.application.service..")
            .and()
            .haveSimpleNameNotContaining("I18nService")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.stacklens.application.port.inbound.platform.I18nUseCase")
            .because("I18nUseCase is a driving port for controllers; services use the outbound I18nPort")
            .check(production)
    }

    @Test
    fun domainDoesNotDependOnTheSharedKernel() {
        noClasses()
            .that()
            .resideInAPackage("com.stacklens.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.stacklens.shared..")
            .because("the shared kernel holds adapter-side HTTP and process helpers")
            .check(production)
    }

    @Test
    fun outboundPortsDoNotExposeJdbcTypes() {
        noClasses()
            .that()
            .resideInAPackage(outboundPorts)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("java.sql..")
            .because("JDBC seams belong to the database adapter, not to the application core")
            .check(production)
    }

    @Test
    fun desktopDependsOnNoAdapter() {
        noClasses()
            .that()
            .resideInAPackage("com.stacklens.desktop..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(adapters)
            .because("the launcher drives the application through its own collaborators")
            .check(production)
    }

    @Test
    fun loaderDependsOnNoOtherProjectLayer() {
        noClasses()
            .that()
            .resideInAPackage("com.stacklens.loader..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                applicationPortsAndServices,
                adapters,
                infrastructure,
                "com.stacklens.domain..",
                "com.stacklens.desktop..",
                "com.stacklens",
            ).because("the encrypted launcher boots the JVM before any application class exists")
            .check(production)
    }

    @Test
    fun adaptersNeverDependOnApplicationServices() {
        noClasses()
            .that()
            .resideInAPackage(adapters)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.stacklens.application.service..")
            .because("adapters reach the core through ports; concrete services stay behind them")
            .check(production)
    }

    @Test
    fun applicationCoreStaysFreeOfSpring() {
        noClasses()
            .that()
            .resideInAPackage(applicationPortsAndServices)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework..")
            .because("only adapters and the composition root may know Spring")
            .check(production)
    }

    @Test
    fun outboundAdaptersDoNotImplementServiceContracts() {
        classes()
            .that()
            .resideInAPackage(outboundAdapters)
            .should(
                object : ArchCondition<JavaClass>("implement contracts owned by the application ports") {
                    override fun check(
                        item: JavaClass,
                        events: ConditionEvents,
                    ) {
                        item.rawInterfaces.forEach { iface ->
                            if (iface.packageName.startsWith("com.stacklens.application.service")) {
                                events.add(
                                    SimpleConditionEvent.violated(
                                        item,
                                        "$item implements ${iface.name} from the service package; declare that contract as an outbound port instead",
                                    ),
                                )
                            }
                        }
                    }
                },
            ).check(production)
    }

    companion object {
        fun domainRule(): ArchRule =
            noClasses()
                .that()
                .resideInAPackage("com.stacklens.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    applicationPortsAndServices,
                    adapters,
                    infrastructure,
                    "com.stacklens.desktop..",
                    "com.stacklens.loader..",
                ).because("the domain layer is the innermost layer")

        fun applicationRule(): ArchRule =
            noClasses()
                .that()
                .resideInAPackage(applicationPortsAndServices)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(adapters)
                .because("application policies talk to adapters through ports only")

        fun inboundToOutboundRule(): ArchRule =
            noClasses()
                .that()
                .resideInAPackage(inboundAdapters)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(outboundAdapters)
                .because("inbound adapters reach outbound capabilities through application ports")

        fun inboundToInfrastructureRule(): ArchRule =
            noClasses()
                .that()
                .resideInAPackage(inboundAdapters)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(infrastructure)
                .because("wiring belongs to the composition root, not to inbound adapters")

        fun outboundToInfrastructureRule(): ArchRule =
            noClasses()
                .that()
                .resideInAPackage(outboundAdapters)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(infrastructure)
                .because("wiring belongs to the composition root, not to outbound adapters")

        fun inboundToOutboundPortsRule(): ArchRule =
            noClasses()
                .that()
                .resideInAPackage(inboundAdapters)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(outboundPorts)
                .because("driving adapters drive the application through inbound use cases only")

        fun portNamingRule(): ArchRule =
            classes()
                .that()
                .areInterfaces()
                .and()
                .haveSimpleNameEndingWith("Port")
                .should()
                .resideInAPackage("com.stacklens.application.port..")
                .because("port contracts are owned by the application core and live in the port packages")

        fun useCaseNamingRule(): ArchRule =
            classes()
                .that()
                .areInterfaces()
                .and()
                .haveSimpleNameEndingWith("UseCase")
                .should()
                .resideInAPackage("com.stacklens.application.port.inbound..")
                .because("use case contracts are driving ports owned by the application core")

        fun serviceUseCaseDependencyRule(): ArchRule =
            classes()
                .that()
                .resideInAPackage("com.stacklens.application.service..")
                .should(
                    object : ArchCondition<JavaClass>("depend on use case interfaces only by implementing them") {
                        override fun check(
                            item: JavaClass,
                            events: ConditionEvents,
                        ) {
                            item.directDependenciesFromSelf.forEach { dependency ->
                                val target = dependency.targetClass
                                if (target.isInterface &&
                                    target.simpleName.endsWith("UseCase") &&
                                    target.packageName.startsWith("com.stacklens.application.port.inbound") &&
                                    !item.isAssignableTo(target.name)
                                ) {
                                    events.add(
                                        SimpleConditionEvent.violated(
                                            dependency,
                                            "$item depends on driving use case ${target.name}; use an outbound collaboration port instead",
                                        ),
                                    )
                                }
                            }
                        }
                    },
                )

        fun useCasePortDirectionRule(): ArchRule =
            classes()
                .that()
                .resideInAPackage("com.stacklens.application.port.inbound..")
                .and()
                .areInterfaces()
                .should(
                    object : ArchCondition<JavaClass>("not extend driven ports") {
                        override fun check(
                            item: JavaClass,
                            events: ConditionEvents,
                        ) {
                            item.rawInterfaces.forEach { parent ->
                                if (parent.packageName.startsWith("com.stacklens.application.port.out")) {
                                    events.add(
                                        SimpleConditionEvent.violated(
                                            item,
                                            "$item extends driven port ${parent.name}; declare the required methods on the driving port instead",
                                        ),
                                    )
                                }
                            }
                        }
                    },
                ).because("driving ports own their contract and must not inherit driven ports")

        fun coreWebFreedomRule(): ArchRule =
            noClasses()
                .that()
                .resideInAnyPackage(applicationPortsAndServices, "com.stacklens.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "org.springframework.http..", "jakarta.servlet..")
                .because("transport types must not appear in use case or port contracts")

        private const val OUTBOUND_ADAPTER_ROOT = "com.stacklens.adapter.out"

        private val sharedKernelPackages = listOf("$OUTBOUND_ADAPTER_ROOT.shared", "$OUTBOUND_ADAPTER_ROOT.persistence")

        fun crossOutboundAdapterRule(): ArchRule =
            classes()
                .that()
                .resideInAPackage(outboundAdapters)
                .should(
                    object : ArchCondition<JavaClass>("depend only on their own module or the shared outbound kernel") {
                        override fun check(
                            item: JavaClass,
                            events: ConditionEvents,
                        ) {
                            val originPackage = item.packageName
                            item.directDependenciesFromSelf.forEach { dependency ->
                                val targetPackage = dependency.targetClass.packageName
                                if (!targetPackage.startsWith("$OUTBOUND_ADAPTER_ROOT.")) return@forEach
                                val sameModule =
                                    targetPackage == originPackage ||
                                        targetPackage.startsWith("$originPackage.") ||
                                        originPackage.startsWith("$targetPackage.")
                                val sharedKernel = sharedKernelPackages.any { targetPackage.startsWith(it) }
                                if (!sameModule && !sharedKernel) {
                                    events.add(
                                        SimpleConditionEvent.violated(
                                            dependency,
                                            "$item depends on the foreign adapter module ${dependency.targetClass.name} ($targetPackage)",
                                        ),
                                    )
                                }
                            }
                        }
                    },
                )
    }
}
