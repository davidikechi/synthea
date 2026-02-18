package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.Test;
import org.mitre.synthea.helpers.GenomicAlteration;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;

public class CSVExporterGenomicsDateTest {

  @Test
  public void preservesExplicitDateGenomics() {
    Person person = new Person(123L);
    GenomicAlteration alteration = new GenomicAlteration();
    alteration.dateGenomics = "2024-09-01";

    String resolved = CSVExporter.resolveDateGenomics(person, alteration,
        millis("2025-01-01T00:00:00Z"));

    assertEquals("2024-09-01", resolved);
  }

  @Test
  public void generatedDateFallsWithinClinicalBounds() {
    Person person = new Person(456L);
    long birthdate = millis("2008-01-01T00:00:00Z");
    long conditionStart = millis("2016-03-15T00:00:00Z");
    long deathdate = millis("2018-08-20T00:00:00Z");

    person.attributes.put(Person.BIRTHDATE, birthdate);
    person.attributes.put(Person.DEATHDATE, deathdate);

    Encounter encounter = person.record.new Encounter(conditionStart, "ambulatory");
    Entry condition = person.record.new Entry(conditionStart, "acute_myeloid_leukemia");
    encounter.conditions.add(condition);
    person.record.encounters.add(encounter);

    GenomicAlteration alteration = new GenomicAlteration();

    String resolved = CSVExporter.resolveDateGenomics(person, alteration,
        millis("2020-01-01T00:00:00Z"));

    LocalDate resolvedDate = LocalDate.parse(resolved);
    LocalDate minDate = LocalDate.ofInstant(Instant.ofEpochMilli(conditionStart), ZoneOffset.UTC);
    LocalDate maxDate = LocalDate.ofInstant(Instant.ofEpochMilli(deathdate), ZoneOffset.UTC);

    assertTrue(!resolvedDate.isBefore(minDate));
    assertTrue(!resolvedDate.isAfter(maxDate));
  }


  @Test
  public void explicitDateBeforeConditionStartIsClamped() {
    Person person = new Person(789L);
    long birthdate = millis("2008-01-01T00:00:00Z");
    long conditionStart = millis("2016-03-15T00:00:00Z");
    long deathdate = millis("2018-08-20T00:00:00Z");

    person.attributes.put(Person.BIRTHDATE, birthdate);
    person.attributes.put(Person.DEATHDATE, deathdate);

    Encounter encounter = person.record.new Encounter(conditionStart, "ambulatory");
    Entry condition = person.record.new Entry(conditionStart, "acute_myeloid_leukemia");
    encounter.conditions.add(condition);
    person.record.encounters.add(encounter);

    GenomicAlteration alteration = new GenomicAlteration();
    alteration.dateGenomics = "2010-01-01";

    String resolved = CSVExporter.resolveDateGenomics(person, alteration,
        millis("2020-01-01T00:00:00Z"));

    assertEquals("2016-03-15", resolved);
  }

  private static long millis(String isoInstant) {
    return Instant.parse(isoInstant).toEpochMilli();
  }
}
