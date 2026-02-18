# Synthea AML Module Modeling Guide (Beginner-Friendly)

This guide explains **how to model a Synthea JSON module** so it generates the CSV outputs you expect.
It is focused on AML, including:

- state-transition modeling basics,
- multiple AML subtype paths,
- medications, procedures, lab tests, observations,
- genomics export behavior,
- and exactly how to run generation with useful CLI arguments.

---

## 1) Mental model: Synthea module = state machine

A Synthea module (`*.json`) is a **directed state graph**.
Each state does one thing (example: set an attribute, start a condition, write an observation, administer a medication), then transitions to another state.

At minimum, your module has:

- `Initial` state (entry point),
- one or more clinical/action states,
- `Terminal` state (end).

Typical state keys used in AML modeling:

- `Initial`
- `SetAttribute`
- `ConditionOnset`
- `Encounter`
- `Observation`
- `Procedure`
- `MedicationOrder` / `MedicationAdministration`
- `Delay`
- `Simple` / `Guard` logic with transitions
- `Terminal`

---

## 2) How CSVs are produced from states

Different states drive different CSV rows (if CSV export is enabled):

- `ConditionOnset` → `conditions.csv`
- `Encounter`/`EncounterEnd` → `encounters.csv`
- `Observation` (including lab-category) → `observations.csv`
- `Procedure` → `procedures.csv`
- `Medication*` states → `medications.csv`
- genomics attributes (`genomics_alterations`) → `genomics.csv`

For genomics specifically:

- If you set `genomics_alterations` as a list of alteration objects on the person, export will write one row per alteration.
- If `date_genomics` is omitted, exporter auto-generates a realistic date bounded by timeline constraints.

---

## 3) Building blocks in small steps

Below is the suggested order to build an AML module.

### Step A: Start with a clean scaffold

```json
{
  "name": "AML Comprehensive Example",
  "remarks": ["Example AML module with subtype branching and genomics"],
  "gmf_version": 2,
  "states": {
    "Initial": {
      "type": "Initial",
      "direct_transition": "Assign_AML_Subtype"
    },
    "Terminal": {
      "type": "Terminal"
    }
  }
}
```

### Step B: Assign subtype (attribute + branching)

Use `SetAttribute` to mark subtype and branch flows with distributions.

Example subtypes:

- `AML_FLT3_ITD`
- `AML_NPM1`
- `APL_PML_RARA` (acute promyelocytic leukemia subtype)

### Step C: Onset condition and open a treatment encounter

Use `ConditionOnset` for AML diagnosis, then an inpatient encounter.

### Step D: Add interventions per subtype

Per branch, add realistic med/procedure/lab/observation states.

### Step E: Capture genomics

Set `genomics_alterations` once subtype is known or once testing is performed.

---

## 4) Full AML example with subtypes, meds, procedures, labs, observations, genomics

> Notes:
> - This is an instructional example. You can split it into submodules later.
> - Codes are illustrative; replace with your preferred vocabulary strategy.

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
        {"transition": "Set_Subtype_FLT3", "distribution": 0.45},
        {"transition": "Set_Subtype_NPM1", "distribution": 0.35},
        {"transition": "Set_Subtype_APL", "distribution": 0.20}
      ]
    },

    "Set_Subtype_FLT3": {
      "type": "SetAttribute",
      "attribute": "aml_subtype",
      "value": "AML_FLT3_ITD",
      "direct_transition": "AML_Condition_Onset"
    },
    "Set_Subtype_NPM1": {
      "type": "SetAttribute",
      "attribute": "aml_subtype",
      "value": "AML_NPM1",
      "direct_transition": "AML_Condition_Onset"
    },
    "Set_Subtype_APL": {
      "type": "SetAttribute",
      "attribute": "aml_subtype",
      "value": "APL_PML_RARA",
      "direct_transition": "AML_Condition_Onset"
    },

    "AML_Condition_Onset": {
      "type": "ConditionOnset",
      "codes": [
        {
          "system": "SNOMED-CT",
          "code": "91861009",
          "display": "Acute myeloid leukemia (disorder)"
        }
      ],
      "direct_transition": "Inpatient_Encounter"
    },

    "Inpatient_Encounter": {
      "type": "Encounter",
      "encounter_class": "inpatient",
      "codes": [
        {
          "system": "SNOMED-CT",
          "code": "183807002",
          "display": "Inpatient stay"
        }
      ],
      "direct_transition": "CBC_Lab"
    },

    "CBC_Lab": {
      "type": "Observation",
      "category": "laboratory",
      "unit": "10*3/uL",
      "codes": [
        {"system": "LOINC", "code": "26464-8", "display": "Leukocytes [#/volume] in Blood"}
      ],
      "value": 22.5,
      "direct_transition": "Blast_Observation"
    },

    "Blast_Observation": {
      "type": "Observation",
      "category": "laboratory",
      "unit": "%",
      "codes": [
        {"system": "LOINC", "code": "709-6", "display": "Blasts/100 leukocytes in Blood"}
      ],
      "value": 38,
      "direct_transition": "Bone_Marrow_Biopsy"
    },

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
    },

    "Capture_Genomics": {
      "type": "SetAttribute",
      "attribute": "genomics_alterations",
      "value": [
        {
          "method_genomics": "NGS",
          "source_genomics": "Bone marrow",
          "alteration_type": "SNV",
          "gene": "FLT3",
          "alteration_status": "Pathogenic",
          "hgvs_coding": "c.2503G>T",
          "hgvs_protein": "p.Asp835Tyr",
          "genome_version": "GRCh38",
          "vaf": 0.42
        },
        {
          "method_genomics": "NGS",
          "source_genomics": "Bone marrow",
          "alteration_type": "SNV",
          "gene": "NPM1",
          "alteration_status": "Pathogenic",
          "hgvs_coding": "c.860_863dup",
          "hgvs_protein": "p.Trp288CysfsTer12",
          "genome_version": "GRCh38",
          "vaf": 0.35
        }
      ],
      "direct_transition": "Subtype_Therapy_Branch"
    },

    "Subtype_Therapy_Branch": {
      "type": "Simple",
      "complex_transition": [
        {
          "condition": {
            "condition_type": "Attribute",
            "attribute": "aml_subtype",
            "operator": "=",
            "value": "AML_FLT3_ITD"
          },
          "transition": "Give_Cytarabine"
        },
        {
          "condition": {
            "condition_type": "Attribute",
            "attribute": "aml_subtype",
            "operator": "=",
            "value": "AML_NPM1"
          },
          "transition": "Give_Daunorubicin"
        },
        {
          "condition": {
            "condition_type": "Attribute",
            "attribute": "aml_subtype",
            "operator": "=",
            "value": "APL_PML_RARA"
          },
          "transition": "Give_ATRA"
        }
      ]
    },

    "Give_Cytarabine": {
      "type": "MedicationOrder",
      "codes": [
        {"system": "RxNorm", "code": "197361", "display": "cytarabine 100 MG Injection"}
      ],
      "reason": "AML",
      "direct_transition": "Give_Midostaurin"
    },
    "Give_Midostaurin": {
      "type": "MedicationOrder",
      "codes": [
        {"system": "RxNorm", "code": "2056826", "display": "midostaurin 25 MG Oral Capsule"}
      ],
      "reason": "AML_FLT3_ITD",
      "direct_transition": "Response_Observation"
    },

    "Give_Daunorubicin": {
      "type": "MedicationOrder",
      "codes": [
        {"system": "RxNorm", "code": "197519", "display": "daunorubicin 5 MG/ML Injection"}
      ],
      "reason": "AML",
      "direct_transition": "Give_Cytarabine_NPM1"
    },
    "Give_Cytarabine_NPM1": {
      "type": "MedicationOrder",
      "codes": [
        {"system": "RxNorm", "code": "197361", "display": "cytarabine 100 MG Injection"}
      ],
      "reason": "AML_NPM1",
      "direct_transition": "Response_Observation"
    },

    "Give_ATRA": {
      "type": "MedicationOrder",
      "codes": [
        {"system": "RxNorm", "code": "83367", "display": "tretinoin 10 MG Oral Capsule"}
      ],
      "reason": "APL",
      "direct_transition": "Give_Arsenic"
    },
    "Give_Arsenic": {
      "type": "MedicationOrder",
      "codes": [
        {"system": "RxNorm", "code": "115698", "display": "arsenic trioxide 1 MG/ML Injection"}
      ],
      "reason": "APL",
      "direct_transition": "Response_Observation"
    },

    "Response_Observation": {
      "type": "Observation",
      "category": "survey",
      "unit": "%",
      "codes": [
        {"system": "LOINC", "code": "30451-9", "display": "Disease response assessment"}
      ],
      "value": 60,
      "direct_transition": "Neutropenia_Check"
    },

    "Neutropenia_Check": {
      "type": "Observation",
      "category": "laboratory",
      "unit": "10*3/uL",
      "codes": [
        {"system": "LOINC", "code": "751-8", "display": "Neutrophils [#/volume] in Blood"}
      ],
      "value": 0.7,
      "direct_transition": "Encounter_End"
    },

    "Encounter_End": {
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

## 5) Why this model works

This example intentionally demonstrates:

1. **Subtype branching** (`distributed_transition` + subtype attribute).
2. **Condition registration** (AML onset).
3. **Encounter context** (clinical events in visit).
4. **Labs/observations** (CBC, blasts, ANC, response).
5. **Procedures** (bone marrow biopsy).
6. **Multiple medications** (different regimen per subtype).
7. **Genomics payload** (`genomics_alterations`) used by CSV genomics export.
8. **Predictable transitions** (clear `direct_transition` chain).

---

## 6) Modeling genomics correctly

### Required pattern

Use a `SetAttribute` like:

- `attribute`: `genomics_alterations`
- `value`: list of objects (one row per genomic alteration)

### Typical fields in each alteration object

- `date_genomics` *(optional now; exporter can generate)*
- `method_genomics`
- `source_genomics`
- `alteration_type`
- `gene`
- `gene_other`
- `fusion`
- `structural_event`
- `alteration_status`
- `chromosome`
- `hgvs_genome`
- `hgvs_coding`
- `hgvs_protein`
- `genome_version`
- `vaf`
- `abnormal_cells_karyo`
- `abnormal_cells_fish`
- `external_db_id`

### Date behavior recommendation

If your upstream data source does not provide genomic date, omit `date_genomics`.
The exporter will generate it with realistic bounds. If a date is provided, it is normalized/clamped to the same timeline window so it does not land before condition onset/birth or after death/export time.

---

## 7) Running Synthea for AML modules

You can run the AML launcher using `--aml`.

## Basic command

```bash
run_synthea --aml -p 100 Massachusetts Bedford --exporter.csv.export=true
```

## Common arguments and what they do

- `--aml`
  - Runs the AML-focused app entrypoint.
  - Ensures `acute_myeloid_leukemia` module is enabled.

- `-p <number>`
  - Population size (number of synthetic people).
  - Example: `-p 1000`.

- `<State> <City>`
  - Geographic target used for demographics/providers.
  - Example: `Massachusetts Bedford`.

- `-s <seed>`
  - Random seed for reproducibility.
  - Same seed + same config typically gives same synthetic cohort pattern.

- `-a <minAge-maxAge>`
  - Age filter for generated population.
  - Example: `-a 5-21` for pediatric-focused AML simulations.

- `-g <M|F>`
  - Force gender selection.

- `--exporter.csv.export=true`
  - Enables CSV export files.

- `--exporter.baseDirectory=<path>`
  - Choose output directory.
  - Useful for test runs and comparisons.

- `--exporter.csv.included_files=<csv list>`
  - Optional. Restrict output to certain CSVs.
  - Example: `patients.csv,conditions.csv,medications.csv,observations.csv,procedures.csv,genomics.csv`.

## Example reproducible run

```bash
run_synthea --aml -p 200 -s 424242 -a 10-21 Massachusetts Bedford \
  --exporter.csv.export=true \
  --exporter.baseDirectory=./output_aml_example
```

## Verifying generated CSVs

```bash
ls -1 ./output_aml_example/csv
```

At minimum for this modeling style, inspect:

- `patients.csv`
- `conditions.csv`
- `encounters.csv`
- `medications.csv`
- `procedures.csv`
- `observations.csv`
- `genomics.csv`

---

## 8) Practical tips for realistic AML modeling

1. **Separate diagnosis and treatment phases**
   - Keep onset, workup, induction, consolidation in distinct state groups.

2. **Use delays intentionally**
   - Add `Delay` states between workup and treatment to model clinical timing.

3. **Avoid giant monolithic states**
   - One clinical intent per state keeps debugging easy.

4. **Use attributes for decisions**
   - Store subtype/risk flags in attributes and branch using conditions.

5. **Validate by CSV, not by eyeballing module only**
   - Always run a small population and check resulting files.

6. **Start deterministic, then add variability**
   - Start with direct transitions, then introduce distributions after validation.

---

## 9) Minimal genomics-only snippet (drop-in)

If you already have AML flow and just want genomics export:

```json
"Capture_AML_Genomics": {
  "type": "SetAttribute",
  "attribute": "genomics_alterations",
  "value": [
    {
      "method_genomics": "NGS",
      "source_genomics": "Bone marrow",
      "alteration_type": "SNV",
      "gene": "FLT3",
      "alteration_status": "Pathogenic",
      "hgvs_coding": "c.2503G>T",
      "hgvs_protein": "p.Asp835Tyr",
      "genome_version": "GRCh38",
      "vaf": 0.42
    }
  ],
  "direct_transition": "Next_State"
}
```

Then route your AML flow through `Capture_AML_Genomics`.

---

## 10) Checklist before calling model "done"

- [ ] AML condition appears in `conditions.csv`
- [ ] subtype branching works (inspect attribute-driven branches in debug runs)
- [ ] meds/procedures/labs/observations appear in corresponding CSVs
- [ ] `genomics.csv` has rows with expected genes/fields
- [ ] genomic dates are present and clinically bounded
- [ ] run is reproducible with fixed `-s` seed

---

If you want, next step can be to split this single-module example into a cleaner
production layout:

- `aml_core.json`
- `aml_subtypes.json`
- `aml_supportive_care.json`
- `aml_genomics.json`

so each part is easier to maintain and test.
