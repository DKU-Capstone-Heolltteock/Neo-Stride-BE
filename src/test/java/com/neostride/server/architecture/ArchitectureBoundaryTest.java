package com.neostride.server.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
	private final JavaClasses productionClasses = new ClassFileImporter()
			.withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages("com.neostride.server");

	@Test
	void communityRunningAndCrewDoNotWriteNotificationsThroughRepository() {
		noClasses()
				.that().resideInAnyPackage(
						"com.neostride.server.community..",
						"com.neostride.server.running..",
						"com.neostride.server.crew.."
				)
				.should().dependOnClassesThat().resideInAnyPackage("com.neostride.server.notification.repository..")
				.check(productionClasses);
	}

	@Test
	void runningDomainUsesCoachingPortInsteadOfCoachingImplementation() {
		noClasses()
				.that().resideInAnyPackage("com.neostride.server.running..")
				.should().dependOnClassesThat().resideInAnyPackage("com.neostride.server.coaching.service..")
				.check(productionClasses);
	}

	@Test
	void runningRepositoryDoesNotReachAcrossDomainPackages() {
		noClasses()
				.that().resideInAnyPackage("com.neostride.server.running.repository..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"com.neostride.server.community..",
						"com.neostride.server.notification..",
						"com.neostride.server.coaching.."
				)
				.check(productionClasses);
	}

	@Test
	void crewDomainUsesPortsInsteadOfForeignRepositories() {
		noClasses()
				.that().resideInAnyPackage("com.neostride.server.crew..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"com.neostride.server.running.repository..",
						"com.neostride.server.community.repository..",
						"com.neostride.server.notification.repository.."
				)
				.check(productionClasses);
	}

}
