package com.stacklens.infrastructure.config

import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.application.stereotype.ManuallyWired
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.annotation.AnnotationBeanNameGenerator
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.type.AnnotationMetadata
import org.springframework.core.type.filter.AnnotationTypeFilter

class ApplicationServiceRegistrar : ImportBeanDefinitionRegistrar {
    override fun registerBeanDefinitions(
        importingClassMetadata: AnnotationMetadata,
        registry: BeanDefinitionRegistry,
    ) {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(ApplicationService::class.java))
        val beanNameGenerator = AnnotationBeanNameGenerator()
        scanner.findCandidateComponents(APPLICATION_PACKAGE).forEach { candidate ->
            val manuallyWired = (candidate as AnnotatedBeanDefinition).metadata.hasAnnotation(ManuallyWired::class.java.name)
            if (!manuallyWired) {
                registry.registerBeanDefinition(beanNameGenerator.generateBeanName(candidate, registry), candidate)
            }
        }
    }

    companion object {
        const val APPLICATION_PACKAGE: String = "com.stacklens.application"
    }
}
