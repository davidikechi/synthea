package org.mitre.synthea.helpers;

import java.util.List;
import java.util.Map;

import org.mitre.synthea.world.agents.Person;

/**
 * Utility methods for collecting genomic alteration data from person attributes.
 */
public final class GenomicsHelper {

  private GenomicsHelper() {
  }

  /**
   * Collect genomic alteration entries from person attributes and store on {@link Person#genomics}.
   *
   * @param person Person to collect genomics for.
   */
  public static void collectGenomics(Person person) {
    if (person == null) {
      return;
    }

    Object raw = person.attributes.get("genomics_alterations");
    if (raw instanceof List<?>) {
      person.genomics.clear();
      List<?> alterations = (List<?>) raw;
      for (Object entry : alterations) {
        if (entry instanceof Map<?, ?>) {
          person.genomics.add(fromMap((Map<?, ?>) entry));
        }
      }
      return;
    }

    if (!person.genomics.isEmpty()) {
      // already populated elsewhere (ex: module Java logic), keep it
      return;
    }

    GenomicAlteration single = fromMap(person.attributes);
    if (hasAnyData(single)) {
      person.genomics.add(single);
    }
  }

  private static GenomicAlteration fromMap(Map<?, ?> map) {
    GenomicAlteration alteration = new GenomicAlteration();
    alteration.dateGenomics = stringValue(map.get("date_genomics"));
    alteration.methodGenomics = stringValue(map.get("method_genomics"));
    alteration.sourceGenomics = stringValue(map.get("source_genomics"));
    alteration.alterationType = stringValue(map.get("alteration_type"));
    alteration.gene = stringValue(map.get("gene"));
    alteration.geneOther = stringValue(map.get("gene_other"));
    alteration.fusion = stringValue(map.get("fusion"));
    alteration.structuralEvent = stringValue(map.get("structural_event"));
    alteration.alterationStatus = stringValue(map.get("alteration_status"));
    alteration.chromosome = stringValue(map.get("chromosome"));
    alteration.hgvsGenome = stringValue(map.get("hgvs_genome"));
    alteration.hgvsCoding = stringValue(map.get("hgvs_coding"));
    alteration.hgvsProtein = stringValue(map.get("hgvs_protein"));
    alteration.genomeVersion = stringValue(map.get("genome_version"));
    alteration.vaf = doubleValue(map.get("vaf"));
    alteration.abnormalCellsKaryo = doubleValue(map.get("abnormal_cells_karyo"));
    alteration.abnormalCellsFish = doubleValue(map.get("abnormal_cells_fish"));
    alteration.externalDbId = stringValue(map.get("external_db_id"));
    return alteration;
  }

  private static boolean hasAnyData(GenomicAlteration alteration) {
    return alteration.dateGenomics != null
        || alteration.methodGenomics != null
        || alteration.sourceGenomics != null
        || alteration.alterationType != null
        || alteration.gene != null
        || alteration.geneOther != null
        || alteration.fusion != null
        || alteration.structuralEvent != null
        || alteration.alterationStatus != null
        || alteration.chromosome != null
        || alteration.hgvsGenome != null
        || alteration.hgvsCoding != null
        || alteration.hgvsProtein != null
        || alteration.genomeVersion != null
        || alteration.vaf != null
        || alteration.abnormalCellsKaryo != null
        || alteration.abnormalCellsFish != null
        || alteration.externalDbId != null;
  }

  private static String stringValue(Object value) {
    return value == null ? null : value.toString();
  }

  private static Double doubleValue(Object value) {
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    if (value instanceof String) {
      try {
        return Double.parseDouble((String) value);
      } catch (NumberFormatException nfe) {
        return null;
      }
    }
    return null;
  }
}
