package org.mitre.synthea.helpers;

/**
 * Represents a single genomic alteration for CSV export.
 */
public class GenomicAlteration {
  public String dateGenomics;
  public String methodGenomics;
  public String sourceGenomics;
  public String alterationType;
  public String gene;
  public String geneOther;
  public String fusion;
  public String structuralEvent;
  public String alterationStatus;
  public String chromosome;
  public String hgvsGenome;
  public String hgvsCoding;
  public String hgvsProtein;
  public String genomeVersion;
  public Double vaf;
  public Double abnormalCellsKaryo;
  public Double abnormalCellsFish;
  public String externalDbId;
}
