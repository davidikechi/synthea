package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mitre.synthea.world.agents.Person;

public class GenomicsHelperTest {

  @Test
  public void collectGenomicsFromListOfMaps() {
    Person person = new Person(0L);

    List<Map<String, Object>> alterations = new ArrayList<>();

    Map<String, Object> first = new HashMap<>();
    first.put("date_genomics", "2024-01-11");
    first.put("method_genomics", "NGS");
    first.put("vaf", 0.53d);
    first.put("external_db_id", "COSM123");
    alterations.add(first);

    Map<String, Object> second = new HashMap<>();
    second.put("gene", "FLT3");
    second.put("abnormal_cells_karyo", "17.5");
    alterations.add(second);

    person.attributes.put("genomics_alterations", alterations);

    GenomicsHelper.collectGenomics(person);

    assertEquals(2, person.genomics.size());
    assertEquals("2024-01-11", person.genomics.get(0).dateGenomics);
    assertEquals("NGS", person.genomics.get(0).methodGenomics);
    assertEquals(Double.valueOf(0.53d), person.genomics.get(0).vaf);
    assertEquals("COSM123", person.genomics.get(0).externalDbId);
    assertEquals("FLT3", person.genomics.get(1).gene);
    assertEquals(Double.valueOf(17.5d), person.genomics.get(1).abnormalCellsKaryo);
    assertNull(person.genomics.get(1).vaf);
  }

  @Test
  public void collectGenomicsFromSingleAttributeSet() {
    Person person = new Person(0L);
    person.attributes.put("gene", "NPM1");
    person.attributes.put("alteration_type", "SNV");

    GenomicsHelper.collectGenomics(person);

    assertEquals(1, person.genomics.size());
    assertEquals("NPM1", person.genomics.get(0).gene);
    assertEquals("SNV", person.genomics.get(0).alterationType);
  }

  @Test
  public void collectGenomicsRetainsPrePopulatedListWhenNoAttributesPresent() {
    Person person = new Person(0L);
    GenomicAlteration existing = new GenomicAlteration();
    existing.gene = "TP53";
    person.genomics.add(existing);

    GenomicsHelper.collectGenomics(person);

    assertEquals(1, person.genomics.size());
    assertEquals("TP53", person.genomics.get(0).gene);
  }

  @Test
  public void collectGenomicsLeavesListEmptyWhenNoDataIsPresent() {
    Person person = new Person(0L);

    GenomicsHelper.collectGenomics(person);

    assertTrue(person.genomics.isEmpty());
  }
}
