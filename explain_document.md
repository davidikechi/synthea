# Synthea AML Module Guide

This guide explains how to build a Synthea JSON module that generates the CSV
files you need — particularly for AML (Acute Myeloid Leukemia) research with
genomics data.

No prior programming experience is needed. Think of this as a recipe book: each
section gives you one ingredient, and by the end you have a working module.

---

## Part 1: What is a Synthea module?

### The video game analogy

Imagine a video game where a character walks through rooms in a hospital, and
each room makes something happen:

- Room 1: The character is diagnosed with a disease.
- Room 2: A nurse takes a blood sample.
- Room 3: A doctor orders chemotherapy.
- Last room: The character leaves the hospital (the simulation ends).

A Synthea module works exactly like that. Each "room" is called a **state**. The
character (a synthetic patient) moves from one state to the next following
arrows called **transitions**. The whole map of rooms and arrows is the
**state machine**.

### The minimum you need

Every module must start somewhere and end somewhere:

```
[Initial] ---> (your clinical states) ---> [Terminal]
```

- `Initial` — the entrance; every module has exactly one.
- `Terminal` — the exit; every module has exactly one.

---

## Part 2: What goes in each CSV file?

Different types of states write rows to different CSV files when you export
with `--exporter.csv.export=true`.

| State type | CSV file it writes to |
|---|---|
| `ConditionOnset` | `conditions.csv` |
| `Encounter` / `EncounterEnd` | `encounters.csv` |
| `Observation` | `observations.csv` |
| `Procedure` | `procedures.csv` |
| `MedicationOrder` | `medications.csv` |
| `SetAttribute` with `genomics_alterations` | `genomics.csv` |

**Key rule for genomics:** The CSV exporter looks for a special person
attribute called `genomics_alterations`. If a `SetAttribute` state sets that
attribute to a list of alteration objects, the exporter writes one row per
alteration into `genomics.csv`. If `date_genomics` is omitted, the exporter
picks a realistic date automatically.

---

## Part 3: The building blocks, one at a time

Think of building a module like stacking LEGO bricks. Here is each brick and
what it does.

### Brick A — The scaffold (start here every time)

```json
{
  "name": "My AML Module",
  "gmf_version": 2,
  "states": {
    "Initial": {
      "type": "Initial",
      "direct_transition": "Terminal"
    },
    "Terminal": {
      "type": "Terminal"
    }
  }
}
```

This does nothing yet, but it is valid and will load without errors. Add
every other brick between `Initial` and `Terminal`.

---

### Brick B — Picking an AML subtype (SetAttribute + branching)

AML has multiple subtypes. Use a `SetAttribute` state to label the patient's
subtype, and use a `distributed_transition` to randomly assign one.

```json
"Assign_AML_Subtype": {
  "type": "Simple",
  "distributed_transition": [
    {"transition": "Set_FLT3",  "distribution": 0.30},
    {"transition": "Set_NPM1",  "distribution": 0.25},
    {"transition": "Set_APL",   "distribution": 0.15},
    {"transition": "Set_MDS",   "distribution": 0.20},
    {"transition": "Set_Ther",  "distribution": 0.10}
  ]
},

"Set_FLT3": {
  "type": "SetAttribute",
  "attribute": "aml_subtype",
  "value": "AML_FLT3_ITD",
  "direct_transition": "Next_State"
}
```

`distribution` values must add up to exactly `1.0`.

---

### Brick C — Diagnosing the disease (ConditionOnset)

This writes a row to `conditions.csv`.

```json
"AML_Diagnosis": {
  "type": "ConditionOnset",
  "codes": [
    {
      "system": "SNOMED-CT",
      "code": "91857003",
      "display": "Acute myeloid leukemia (disorder)"
    }
  ],
  "direct_transition": "Hospital_Encounter"
}
```

---

### Brick D — Opening a hospital visit (Encounter)

Clinical events like lab tests and procedures must happen inside an
`Encounter`. Think of it as "the hospital doors open."

```json
"Hospital_Encounter": {
  "type": "Encounter",
  "encounter_class": "inpatient",
  "codes": [
    {
      "system": "SNOMED-CT",
      "code": "183807002",
      "display": "Inpatient stay"
    }
  ],
  "direct_transition": "Blood_Test"
}
```

Always pair an `Encounter` with an `EncounterEnd` later in the chain — that
closes the hospital doors.

---

### Brick E — Recording a lab result (Observation)

Each observation writes a row to `observations.csv`. Use `range` to produce
a random value between `low` and `high`.

```json
"Blast_Percentage": {
  "type": "Observation",
  "category": "laboratory",
  "unit": "%",
  "codes": [
    {"system": "LOINC", "code": "26464-8", "display": "Blasts/100 cells in Bone marrow"}
  ],
  "range": {"low": 20, "high": 95},
  "direct_transition": "Next_Lab"
}
```

---

### Brick F — Performing a procedure (Procedure)

Writes a row to `procedures.csv`.

```json
"Bone_Marrow_Biopsy": {
  "type": "Procedure",
  "codes": [
    {
      "system": "SNOMED-CT",
      "code": "86273004",
      "display": "Biopsy of bone marrow"
    }
  ],
  "direct_transition": "Capture_Genomics"
}
```

---

### Brick G — Capturing genomic data (SetAttribute → genomics.csv)

This is the most important brick if you want `genomics.csv` rows. Set the
`genomics_alterations` attribute to a list. Each item in the list becomes
one row in `genomics.csv`.

```json
"Capture_Genomics": {
  "type": "SetAttribute",
  "attribute": "genomics_alterations",
  "value": [
    {
      "method_genomics": "NGS",
      "source_genomics": "Bone marrow",
      "alteration_type": "ITD",
      "gene": "FLT3",
      "alteration_status": "Pathogenic",
      "chromosome": "13",
      "hgvs_coding": "c.1770_1942dup",
      "genome_version": "GRCh38",
      "vaf": 0.48
    }
  ],
  "direct_transition": "Give_Medication"
}
```

**What do those fields mean?**

| Field | Plain English meaning |
|---|---|
| `method_genomics` | How was the DNA read? (e.g., NGS = Next-Generation Sequencing) |
| `source_genomics` | Where did the sample come from? (e.g., bone marrow) |
| `alteration_type` | What kind of DNA change? (SNV = single letter swap, ITD = repeated stretch, fusion = two genes stuck together) |
| `gene` | Which gene is affected? (e.g., FLT3, NPM1, TP53) |
| `alteration_status` | Is it harmful? (`Pathogenic` = yes) |
| `chromosome` | Which of the 23 chromosome pairs holds this gene? |
| `hgvs_coding` | The scientific shorthand for the exact DNA change |
| `genome_version` | Which version of the human genome map was used? (usually GRCh38) |
| `vaf` | Variant allele fraction — what fraction of cells carry this mutation (0.0 – 1.0) |

---

### Brick H — Ordering a medication (MedicationOrder)

Writes a row to `medications.csv`.

```json
"Give_Midostaurin": {
  "type": "MedicationOrder",
  "codes": [
    {"system": "RxNorm", "code": "1111495", "display": "midostaurin 25 MG Oral Capsule"}
  ],
  "reason": "AML_FLT3_ITD",
  "direct_transition": "End_Visit"
}
```

---

### Brick I — Closing the hospital visit (EncounterEnd)

```json
"End_Visit": {
  "type": "EncounterEnd",
  "direct_transition": "Terminal"
}
```

---

## Part 4: Putting it all together — a complete working example

Here is a module that:

1. Randomly assigns one of five AML subtypes.
2. Diagnoses the patient with AML.
3. Opens an inpatient hospital encounter.
4. Records three lab tests (blast %, neutrophil count, platelet count).
5. Performs a bone marrow biopsy.
6. Captures subtype-specific genomic alterations into `genomics.csv`.
7. Orders subtype-appropriate medications.
8. Closes the encounter.

```json
{
  "name": "AML Comprehensive Example",
  "gmf_version": 2,
  "states": {

    "Initial": {
      "type": "Initial",
      "direct_transition": "Assign_AML_Subtype"
    },

    "Assign_AML_Subtype": {
      "type": "Simple",
      "distributed_transition": [
        {"transition": "Set_Subtype_FLT3", "distribution": 0.30},
        {"transition": "Set_Subtype_NPM1", "distribution": 0.25},
        {"transition": "Set_Subtype_APL",  "distribution": 0.15},
        {"transition": "Set_Subtype_MDS",  "distribution": 0.20},
        {"transition": "Set_Subtype_Ther", "distribution": 0.10}
      ]
    },

    "Set_Subtype_FLT3": {
      "type": "SetAttribute", "attribute": "aml_subtype", "value": "AML_FLT3_ITD",
      "direct_transition": "AML_Condition_Onset"
    },
    "Set_Subtype_NPM1": {
      "type": "SetAttribute", "attribute": "aml_subtype", "value": "AML_NPM1",
      "direct_transition": "AML_Condition_Onset"
    },
    "Set_Subtype_APL": {
      "type": "SetAttribute", "attribute": "aml_subtype", "value": "APL_PML_RARA",
      "direct_transition": "AML_Condition_Onset"
    },
    "Set_Subtype_MDS": {
      "type": "SetAttribute", "attribute": "aml_subtype", "value": "AML_MDS_Related",
      "direct_transition": "AML_Condition_Onset"
    },
    "Set_Subtype_Ther": {
      "type": "SetAttribute", "attribute": "aml_subtype", "value": "AML_Therapy_Related",
      "direct_transition": "AML_Condition_Onset"
    },

    "AML_Condition_Onset": {
      "type": "ConditionOnset",
      "codes": [{"system": "SNOMED-CT", "code": "91857003", "display": "Acute myeloid leukemia (disorder)"}],
      "direct_transition": "Inpatient_Encounter"
    },

    "Inpatient_Encounter": {
      "type": "Encounter",
      "encounter_class": "inpatient",
      "codes": [{"system": "SNOMED-CT", "code": "183807002", "display": "Inpatient stay"}],
      "direct_transition": "Blast_Pct_Obs"
    },

    "Blast_Pct_Obs": {
      "type": "Observation", "category": "laboratory", "unit": "%",
      "codes": [{"system": "LOINC", "code": "26464-8", "display": "Blasts/100 cells in Bone marrow"}],
      "range": {"low": 20, "high": 95},
      "direct_transition": "ANC_Obs"
    },

    "ANC_Obs": {
      "type": "Observation", "category": "laboratory", "unit": "10*3/uL",
      "codes": [{"system": "LOINC", "code": "751-8", "display": "Neutrophils [#/volume] in Blood by Automated count"}],
      "range": {"low": 0.2, "high": 1.5},
      "direct_transition": "Platelet_Obs"
    },

    "Platelet_Obs": {
      "type": "Observation", "category": "laboratory", "unit": "10*3/uL",
      "codes": [{"system": "LOINC", "code": "777-3", "display": "Platelets [#/volume] in Blood by Automated count"}],
      "range": {"low": 20, "high": 80},
      "direct_transition": "Bone_Marrow_Biopsy"
    },

    "Bone_Marrow_Biopsy": {
      "type": "Procedure",
      "codes": [{"system": "SNOMED-CT", "code": "86273004", "display": "Biopsy of bone marrow"}],
      "direct_transition": "Genomics_Branch"
    },

    "Genomics_Branch": {
      "type": "Simple",
      "complex_transition": [
        {
          "condition": {"condition_type": "Attribute", "attribute": "aml_subtype", "operator": "=", "value": "AML_FLT3_ITD"},
          "transition": "Capture_Genomics_FLT3"
        },
        {
          "condition": {"condition_type": "Attribute", "attribute": "aml_subtype", "operator": "=", "value": "AML_NPM1"},
          "transition": "Capture_Genomics_NPM1"
        },
        {
          "condition": {"condition_type": "Attribute", "attribute": "aml_subtype", "operator": "=", "value": "APL_PML_RARA"},
          "transition": "Capture_Genomics_APL"
        },
        {
          "condition": {"condition_type": "Attribute", "attribute": "aml_subtype", "operator": "=", "value": "AML_MDS_Related"},
          "transition": "Capture_Genomics_MDS"
        },
        {"transition": "Capture_Genomics_Therapy"}
      ]
    },

    "Capture_Genomics_FLT3": {
      "type": "SetAttribute",
      "attribute": "genomics_alterations",
      "value": [{
        "method_genomics": "NGS", "source_genomics": "Bone marrow",
        "alteration_type": "ITD", "gene": "FLT3",
        "alteration_status": "Pathogenic", "chromosome": "13",
        "hgvs_coding": "c.1770_1942dup", "genome_version": "GRCh38", "vaf": 0.48
      }],
      "direct_transition": "Give_Cytarabine_FLT3"
    },

    "Capture_Genomics_NPM1": {
      "type": "SetAttribute",
      "attribute": "genomics_alterations",
      "value": [{
        "method_genomics": "NGS", "source_genomics": "Bone marrow",
        "alteration_type": "indel", "gene": "NPM1",
        "alteration_status": "Pathogenic", "chromosome": "5",
        "hgvs_coding": "c.860_863dup", "hgvs_protein": "p.Trp288CysfsTer12",
        "genome_version": "GRCh38", "vaf": 0.45
      }],
      "direct_transition": "Give_Daunorubicin"
    },

    "Capture_Genomics_APL": {
      "type": "SetAttribute",
      "attribute": "genomics_alterations",
      "value": [{
        "method_genomics": "RT-PCR", "source_genomics": "Bone marrow",
        "alteration_type": "fusion", "gene": "PML",
        "fusion": "PML::RARA", "structural_event": "t(15;17)(q24.1;q21.2)",
        "alteration_status": "Pathogenic", "genome_version": "GRCh38", "vaf": 0.52
      }],
      "direct_transition": "Give_ATRA"
    },

    "Capture_Genomics_MDS": {
      "type": "SetAttribute",
      "attribute": "genomics_alterations",
      "value": [{
        "method_genomics": "NGS", "source_genomics": "Bone marrow",
        "alteration_type": "SNV", "gene": "ASXL1",
        "alteration_status": "Pathogenic", "chromosome": "20",
        "hgvs_coding": "c.1934dupG", "hgvs_protein": "p.Gly646TrpfsTer12",
        "genome_version": "GRCh38", "vaf": 0.38
      }],
      "direct_transition": "Give_Venetoclax"
    },

    "Capture_Genomics_Therapy": {
      "type": "SetAttribute",
      "attribute": "genomics_alterations",
      "value": [{
        "method_genomics": "NGS", "source_genomics": "Bone marrow",
        "alteration_type": "SNV", "gene": "TP53",
        "alteration_status": "Pathogenic", "chromosome": "17",
        "hgvs_coding": "c.817C>T", "hgvs_protein": "p.Arg273Cys",
        "genome_version": "GRCh38", "vaf": 0.41
      }],
      "direct_transition": "Give_Cytarabine_Therapy"
    },

    "Give_Cytarabine_FLT3": {
      "type": "MedicationOrder",
      "codes": [{"system": "RxNorm", "code": "197361", "display": "cytarabine 100 MG Injection"}],
      "reason": "AML_FLT3_ITD", "direct_transition": "Give_Midostaurin"
    },
    "Give_Midostaurin": {
      "type": "MedicationOrder",
      "codes": [{"system": "RxNorm", "code": "1111495", "display": "midostaurin 25 MG Oral Capsule"}],
      "reason": "AML_FLT3_ITD", "direct_transition": "End_Encounter"
    },

    "Give_Daunorubicin": {
      "type": "MedicationOrder",
      "codes": [{"system": "RxNorm", "code": "197519", "display": "daunorubicin 5 MG/ML Injection"}],
      "reason": "AML_NPM1", "direct_transition": "End_Encounter"
    },

    "Give_ATRA": {
      "type": "MedicationOrder",
      "codes": [{"system": "RxNorm", "code": "723", "display": "tretinoin 10 MG Oral Capsule"}],
      "reason": "APL_PML_RARA", "direct_transition": "Give_Arsenic"
    },
    "Give_Arsenic": {
      "type": "MedicationOrder",
      "codes": [{"system": "RxNorm", "code": "17419", "display": "arsenic trioxide 1 MG/ML Injection"}],
      "reason": "APL_PML_RARA", "direct_transition": "End_Encounter"
    },

    "Give_Venetoclax": {
      "type": "MedicationOrder",
      "codes": [{"system": "RxNorm", "code": "1858040", "display": "venetoclax 100 MG Oral Tablet"}],
      "reason": "AML_MDS_Related", "direct_transition": "End_Encounter"
    },

    "Give_Cytarabine_Therapy": {
      "type": "MedicationOrder",
      "codes": [{"system": "RxNorm", "code": "197361", "display": "cytarabine 100 MG Injection"}],
      "reason": "AML_Therapy_Related", "direct_transition": "End_Encounter"
    },

    "End_Encounter": {
      "type": "EncounterEnd",
      "direct_transition": "Terminal"
    },

    "Terminal": {
      "type": "Terminal"
    }

  }
}
```

---

## Part 5: Why the example works — a plain-English walkthrough

Here is what happens for each synthetic patient, step by step:

```
[Initial]
    |
    v
[Assign_AML_Subtype] ← randomly picks one of five subtypes
    |
    v
[Set_Subtype_FLT3 / NPM1 / APL / MDS / Therapy] ← labels the patient
    |
    v
[AML_Condition_Onset] ← writes a row to conditions.csv
    |
    v
[Inpatient_Encounter] ← opens a hospital stay → writes to encounters.csv
    |
    v
[Blast_Pct_Obs]  ← blast % in bone marrow  → observations.csv
[ANC_Obs]        ← white cell count         → observations.csv
[Platelet_Obs]   ← platelet count           → observations.csv
    |
    v
[Bone_Marrow_Biopsy] ← biopsy procedure    → procedures.csv
    |
    v
[Genomics_Branch] ← checks which subtype this patient has
    |
    +-- FLT3  → [Capture_Genomics_FLT3] → genomics.csv (FLT3 ITD row)
    +-- NPM1  → [Capture_Genomics_NPM1] → genomics.csv (NPM1 indel row)
    +-- APL   → [Capture_Genomics_APL]  → genomics.csv (PML::RARA fusion row)
    +-- MDS   → [Capture_Genomics_MDS]  → genomics.csv (ASXL1 SNV row)
    +-- Ther  → [Capture_Genomics_Therapy] → genomics.csv (TP53 SNV row)
    |
    v
[Give_Medication(s)] ← matched to subtype  → medications.csv
    |
    v
[End_Encounter] ← closes the hospital stay → encounters.csv
    |
    v
[Terminal]
```

Every box that has an arrow to a CSV file adds at least one row for this
patient. Run 100 patients and you get 100 condition rows, 300 observation
rows (3 per patient), 100 procedure rows, 100 genomic alteration rows, and
so on.

---

## Part 6: How to run it

### The `aml_model` module

`src/main/resources/modules/aml_model.json` is the ready-to-run version of
the module above. Load it with:

```bash
run_synthea -class aml -m aml_model -p 100 Massachusetts Bedford \
  --exporter.csv.export=true
```

| Flag | What it does |
|---|---|
| `-class aml` | Routes through the AML launcher (`AcuteMyeloidLeukemiaApp`) |
| `-m aml_model` | Loads only the `aml_model` module |
| `-p 100` | Generates 100 synthetic patients |
| `Massachusetts Bedford` | Uses Massachusetts demographics |
| `--exporter.csv.export=true` | Turns on CSV file output |

### Other useful flags

| Flag | Example | What it does |
|---|---|---|
| `-s` | `-s 42` | Sets a random seed so results are reproducible |
| `-a` | `-a 30-70` | Limits patient ages to 30–70 years |
| `-g` | `-g M` | Forces all patients to be male (`F` for female) |
| `--exporter.baseDirectory` | `--exporter.baseDirectory=./my_output` | Sets where files are saved |

### Reproducible example

```bash
run_synthea -class aml -m aml_model -p 200 -s 424242 -a 20-75 \
  Massachusetts Bedford \
  --exporter.csv.export=true \
  --exporter.baseDirectory=./output_aml
```

### Checking your output

```bash
ls ./output_aml/csv
```

You should see at minimum:

- `patients.csv`
- `conditions.csv`
- `encounters.csv`
- `observations.csv`
- `procedures.csv`
- `medications.csv`
- `genomics.csv`

---

## Part 7: The genomics fields — a field-by-field reference

Each item in the `genomics_alterations` list can have these fields:

| Field | Required? | Description |
|---|---|---|
| `method_genomics` | Yes | How DNA was sequenced (e.g., `NGS`, `RT-PCR`, `FISH`) |
| `source_genomics` | Yes | Tissue source (e.g., `Bone marrow`, `Peripheral blood`) |
| `alteration_type` | Yes | Type of change: `SNV`, `indel`, `ITD`, `fusion`, `CNV` |
| `gene` | Yes | Gene symbol (e.g., `FLT3`, `NPM1`, `TP53`) |
| `alteration_status` | Yes | Clinical meaning: `Pathogenic`, `Benign`, `VUS` |
| `chromosome` | No | Chromosome number as a string (e.g., `"13"`) |
| `hgvs_coding` | No | Standard coding-DNA notation (e.g., `c.817C>T`) |
| `hgvs_protein` | No | Standard protein notation (e.g., `p.Arg273Cys`) |
| `genome_version` | No | Reference genome build (e.g., `GRCh38`) |
| `vaf` | No | Fraction of cells with the mutation, 0.0–1.0 |
| `fusion` | No | Fusion gene name (e.g., `PML::RARA`) |
| `structural_event` | No | Cytogenetic shorthand (e.g., `t(15;17)(q24.1;q21.2)`) |
| `date_genomics` | No | If omitted, exporter generates a realistic date automatically |
| `gene_other` | No | Second gene in a fusion event |
| `external_db_id` | No | ID in an external database (e.g., ClinVar ID) |

---

## Part 8: Tips before you call your module "done"

1. **One clinical action per state** — keep states small and focused. It makes
   debugging much easier.

2. **Always test with a small population first** — run with `-p 10` before
   `-p 10000`. Check the CSV files after each run.

3. **Genomics must come after the `ConditionOnset`** — set
   `genomics_alterations` only after the AML diagnosis state, otherwise the
   date calculation may produce incorrect results.

4. **Make it reproducible** — always use `-s <seed>` when sharing results with
   teammates so they can recreate the same data.

5. **Use attributes to drive branches** — store decisions (subtype, risk tier,
   treatment response) in `SetAttribute` and read them back with
   `complex_transition` conditions. This keeps the module easy to extend.

---

## Quick checklist

- [ ] `conditions.csv` contains an AML row for each patient
- [ ] `encounters.csv` contains inpatient visits
- [ ] `observations.csv` contains lab results (blast %, ANC, platelets, etc.)
- [ ] `procedures.csv` contains the bone marrow biopsy
- [ ] `medications.csv` contains the subtype-appropriate treatment
- [ ] `genomics.csv` contains at least one alteration row per patient
- [ ] Genomic dates in `genomics.csv` look clinically reasonable
- [ ] Results are reproducible when you re-run with the same `-s` seed
