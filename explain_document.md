# AML Synthea Module — Technical Reference

This document explains the full architecture of the AML simulation modules in this repository,
how every state type works, how the `aml_disease_model.json` module is structured from top to
bottom, and how to set up, run, and extend it.

---

## Contents

1. [What Synthea Does](#1-what-synthea-does)
2. [The Generic Module Framework — Core Concepts](#2-the-generic-module-framework--core-concepts)
3. [Every State Type Used in the AML Model](#3-every-state-type-used-in-the-aml-model)
4. [How Transitions Work](#4-how-transitions-work)
5. [How Codes and Ontologies Are Used](#5-how-codes-and-ontologies-are-used)
6. [The Two AML Modules in This Repository](#6-the-two-aml-modules-in-this-repository)
7. [aml\_disease\_model.json — Full Walkthrough](#7-aml_disease_modeljson--full-walkthrough)
8. [Setting Up and Running the Model](#8-setting-up-and-running-the-model)
9. [Expected CSV Output](#9-expected-csv-output)
10. [How to Extend the Model](#10-how-to-extend-the-model)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. What Synthea Does

Synthea is a synthetic patient generator. It simulates realistic but entirely fictional
patient life histories — including diagnoses, laboratory results, medications, procedures,
vital signs, and genomic findings — and writes them to standard output formats (CSV, FHIR,
C-CDA).

The key design principle is **reproducibility**: given the same random seed (`-s`), the same
configuration, and the same modules, Synthea will always produce the same cohort. This makes
it suitable for testing software pipelines, building reference datasets, and evaluating
research hypotheses without ever touching real patient data.

Synthea generates each patient by running a set of **modules** in parallel. Each module is
responsible for one aspect of the patient's health history. Modules fire independently; a
patient's AML module runs at the same time as their lifecycle module, cardiovascular module,
and so on.

---

## 2. The Generic Module Framework — Core Concepts

### 2.1 A module is a state machine

Every `.json` module file describes a **directed state graph**. Each patient starts at the
`Initial` state and moves forward one state at a time. States never loop back by default —
the graph is acyclic.

The minimum valid module has exactly two states:

```json
{
  "name": "My Module",
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

This module does nothing — patients pass through immediately. **A module that only contains
`Initial` and `Terminal` produces zero rows in any CSV file.** Every meaningful state must
appear between them.

### 2.2 The module file structure

| Top-level key   | Required | Purpose |
|-----------------|----------|---------|
| `"name"`        | Yes      | Human-readable module name (appears in logs and `Modules:` output) |
| `"gmf_version"` | Yes      | Must be `2` for current Synthea |
| `"states"`      | Yes      | Object whose keys are state names and values are state definitions |
| `"remarks"`     | No       | Array of documentation strings; ignored at runtime |

### 2.3 Attributes — the patient's memory

**Attributes** are named variables stored on each patient. They persist for the patient's
entire simulation. They are how states communicate with each other — one state writes a value,
a later state reads it to decide what to do.

```json
"Set_Subtype_FLT3_ITD": {
  "type": "SetAttribute",
  "attribute": "aml_subtype",
  "value": "AML_FLT3_ITD",
  "direct_transition": "Set_ELN_Risk_Intermediate"
}
```

Later states read `aml_subtype` in `complex_transition` conditions to route the patient to
the correct treatment branch. Attributes never appear directly in CSV output — they are
internal state.

There are two special attributes that **do** affect CSV output:

| Attribute | Effect |
|-----------|--------|
| `"genomics_alterations"` | When set to a list of alteration objects, the CSV exporter writes one row per alteration to `genomics.csv` |
| `"cause_of_death"` | Referenced by `Death` states |

### 2.4 `assign_to_attribute` — saving a condition or medication reference

`ConditionOnset` and `MedicationOrder` states accept an optional `"assign_to_attribute"` key.
This saves a reference to the condition or medication so it can be ended later by
`ConditionEnd` or `MedicationEnd` using `"condition_onset"` or `"medication_order"`.

```json
"AML_Condition_Onset": {
  "type": "ConditionOnset",
  "assign_to_attribute": "aml_condition",   ← saves reference here
  ...
}

"End_AML_Condition": {
  "type": "ConditionEnd",
  "condition_onset": "aml_condition",        ← resolves that reference
  ...
}
```

If you forget to end a condition, it will remain open in the patient record indefinitely,
which causes it to appear as "active" in `conditions.csv`.

---

## 3. Every State Type Used in the AML Model

### 3.1 `Initial`

The mandatory entry point. Every module has exactly one. It does no clinical work — it only
fires a transition.

```json
"Initial": {
  "type": "Initial",
  "complex_transition": [ ... ]
}
```

### 3.2 `Terminal`

The mandatory exit point. Once a patient reaches `Terminal`, this module is done for that
patient. No further states fire.

```json
"Terminal": {
  "type": "Terminal"
}
```

### 3.3 `Simple`

A routing-only state. It does no clinical work. Its entire purpose is to hold a transition
— typically a `distributed_transition` or `complex_transition` — when you want branching
logic that doesn't belong inside a clinical state.

```json
"Assign_AML_Subtype": {
  "type": "Simple",
  "distributed_transition": [
    { "transition": "Set_Subtype_NPM1",    "distribution": 0.27 },
    { "transition": "Set_Subtype_FLT3_ITD","distribution": 0.25 },
    { "transition": "Set_Subtype_APL",     "distribution": 0.10 },
    { "transition": "Set_Subtype_CBF",     "distribution": 0.08 },
    { "transition": "Set_Subtype_IDH",     "distribution": 0.10 },
    { "transition": "Set_Subtype_TP53_MDS","distribution": 0.20 }
  ]
}
```

### 3.4 `SetAttribute`

Sets a named attribute on the patient. Produces **no CSV output** unless the attribute is
`"genomics_alterations"`.

```json
"Set_Subtype_NPM1": {
  "type": "SetAttribute",
  "attribute": "aml_subtype",
  "value": "AML_NPM1",
  "direct_transition": "Set_ELN_Risk_Favorable"
}
```

**Special case — genomics:** When `"attribute"` is `"genomics_alterations"` and `"value"` is
a list of alteration objects, the CSV exporter reads this and writes one row per object to
`genomics.csv`. See [Section 7.6](#76-genomic-alterations) for details.

### 3.5 `Delay`

Advances simulated time without doing any clinical work. Used to model onset age (the patient
ages before diagnosis) or gaps between care phases.

```json
"Delay_5": {
  "type": "Delay",
  "exact": { "quantity": 5, "unit": "years" },
  "direct_transition": "Assign_AML_Subtype"
}
```

Time units: `"years"`, `"months"`, `"weeks"`, `"days"`, `"hours"`, `"minutes"`.

For a random delay with a range, use `"range"`:

```json
"Wait_For_Results": {
  "type": "Delay",
  "range": { "low": 3, "high": 7, "unit": "days" },
  "direct_transition": "Next_State"
}
```

For a Gaussian delay (mean ± standard deviation), use `"distribution"`:

```json
"Pre_Treatment_Wait": {
  "type": "Delay",
  "distribution": {
    "kind": "GAUSSIAN",
    "parameters": { "mean": 180, "standardDeviation": 60 }
  },
  "unit": "minutes",
  "direct_transition": "Next_State"
}
```

### 3.6 `ConditionOnset`

Records a diagnosis on the patient. Writes a row to `conditions.csv`.

```json
"AML_Condition_Onset": {
  "type": "ConditionOnset",
  "assign_to_attribute": "aml_condition",
  "codes": [
    {
      "system": "SNOMED-CT",
      "code": "91861009",
      "display": "Acute myeloid leukemia (disorder)"
    }
  ],
  "direct_transition": "Inpatient_Encounter"
}
```

Key fields:

| Field | Required | Notes |
|-------|----------|-------|
| `"codes"` | Yes | Array of ontology codes; at least one required |
| `"assign_to_attribute"` | Recommended | Saves reference so the condition can be ended later |
| `"target_encounter"` | No | Associates the onset with a specific encounter state by name |

### 3.7 `ConditionEnd`

Resolves an active condition. Updates `conditions.csv` with an end date.

```json
"End_AML_Condition": {
  "type": "ConditionEnd",
  "condition_onset": "aml_condition",
  "direct_transition": "End_Encounter"
}
```

The `"condition_onset"` value must match the `"assign_to_attribute"` value used in the
corresponding `ConditionOnset` state.

### 3.8 `Encounter`

Opens a clinical encounter (visit). **All clinical events between an `Encounter` and its
matching `EncounterEnd` are associated with that visit.** Writes a row to `encounters.csv`.

```json
"Inpatient_Encounter": {
  "type": "Encounter",
  "encounter_class": "inpatient",
  "reason": "aml_condition",
  "codes": [
    {
      "system": "SNOMED-CT",
      "code": "185347001",
      "display": "Encounter for problem (procedure)"
    }
  ],
  "direct_transition": "CBC_WBC"
}
```

`"encounter_class"` values: `"inpatient"`, `"ambulatory"`, `"emergency"`, `"wellness"`,
`"urgentcare"`.

`"reason"` should be the attribute name of the condition that prompted this visit
(set via `assign_to_attribute` on the `ConditionOnset`).

### 3.9 `EncounterEnd`

Closes the current encounter. Can include a discharge disposition.

```json
"End_Encounter": {
  "type": "EncounterEnd",
  "discharge_disposition": {
    "system": "NUBC",
    "code": "1",
    "display": "Discharge to Home"
  },
  "direct_transition": "Terminal"
}
```

### 3.10 `Observation`

Records a measurement — lab result, vital sign, or molecular test result. Writes a row to
`observations.csv`.

**Numeric observation with a random range:**

```json
"CBC_WBC": {
  "type": "Observation",
  "category": "laboratory",
  "unit": "10*3/uL",
  "codes": [
    {
      "system": "LOINC",
      "code": "6690-2",
      "display": "Leukocytes [#/volume] in Blood by Automated count"
    }
  ],
  "range": { "low": 3.0, "high": 120.0 },
  "direct_transition": "CBC_Hemoglobin"
}
```

**Qualitative (coded) observation:**

```json
"FLT3_ITD_Observation": {
  "type": "Observation",
  "category": "laboratory",
  "unit": "{Qualitative}",
  "codes": [
    {
      "system": "LOINC",
      "code": "85176-4",
      "display": "FLT3 gene internal tandem duplication [Presence] in Blood or Tissue by Molecular genetics method"
    }
  ],
  "value_code": {
    "system": "SNOMED-CT",
    "code": "10828004",
    "display": "Positive (qualifier value)"
  },
  "direct_transition": "Genomics_FLT3_ITD"
}
```

Key fields:

| Field | Required | Notes |
|-------|----------|-------|
| `"category"` | Yes | `"laboratory"`, `"vital-signs"`, `"survey"`, `"imaging"` |
| `"unit"` | Yes | UCUM unit string; use `"{Qualitative}"` for coded results |
| `"codes"` | Yes | LOINC is strongly preferred for observations |
| `"range"` | One of | Random numeric value between `low` and `high` |
| `"exact"` | One of | Fixed numeric value |
| `"value_code"` | One of | Coded result (Positive/Negative/etc.) |

### 3.11 `Procedure`

Records a clinical procedure. Writes a row to `procedures.csv`.

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
  "duration": { "low": 30, "high": 60, "unit": "minutes" },
  "reason": "aml_condition",
  "direct_transition": "BM_Blast_Percentage"
}
```

`"reason"` is the attribute name of the condition that prompted the procedure.
`"duration"` advances simulated time by a random amount in the given range.

### 3.12 `MedicationOrder`

Prescribes a medication. Writes a row to `medications.csv`.

```json
"Give_ATRA": {
  "type": "MedicationOrder",
  "codes": [
    {
      "system": "RxNorm",
      "code": "83367",
      "display": "tretinoin 10 MG Oral Capsule"
    }
  ],
  "reason": "aml_condition",
  "prescription": {
    "dosage": { "amount": 2, "frequency": 2, "period": 1, "unit": "days" },
    "duration": { "quantity": 28, "unit": "days" }
  },
  "direct_transition": "Give_ATO"
}
```

`"assign_to_attribute"` can be used to save a reference for later `MedicationEnd`.

### 3.13 `MedicationEnd`

Ends an active medication order. Updates `medications.csv` with a stop date.

```json
"End_Levofloxacin": {
  "type": "MedicationEnd",
  "medication_order": "levofloxacin_med",
  "direct_transition": "End_AML"
}
```

---

## 4. How Transitions Work

### 4.1 `direct_transition`

The simplest case: always go to exactly one named state.

```json
"direct_transition": "Next_State"
```

### 4.2 `distributed_transition`

Choose one of several states randomly according to probabilities. Probabilities must sum to
exactly `1.0`.

```json
"distributed_transition": [
  { "transition": "Set_Subtype_NPM1",    "distribution": 0.27 },
  { "transition": "Set_Subtype_FLT3_ITD","distribution": 0.25 },
  { "transition": "Set_Subtype_APL",     "distribution": 0.10 },
  { "transition": "Set_Subtype_CBF",     "distribution": 0.08 },
  { "transition": "Set_Subtype_IDH",     "distribution": 0.10 },
  { "transition": "Set_Subtype_TP53_MDS","distribution": 0.20 }
]
```

### 4.3 `complex_transition`

The most powerful transition type. Each entry has a `"condition"` and a target. The first
condition that evaluates to `true` wins. An entry with no `"condition"` is a catch-all
default and must appear last.

```json
"complex_transition": [
  {
    "condition": {
      "condition_type": "Attribute",
      "attribute": "aml_subtype",
      "operator": "==",
      "value": "APL_PML_RARA"
    },
    "transition": "RT_PCR_PML_RARA"
  },
  {
    "condition": {
      "condition_type": "Attribute",
      "attribute": "aml_subtype",
      "operator": "==",
      "value": "AML_FLT3_ITD"
    },
    "transition": "FLT3_ITD_Observation"
  },
  {
    "transition": "Genomics_TP53_MDS"   ← default, no condition
  }
]
```

`complex_transition` can also carry `"distributions"` (not `"transition"`) on each entry,
making each branch itself probabilistic. This is how the incidence gate works:

```json
"complex_transition": [
  {
    "condition": { "condition_type": "Gender", "gender": "F" },
    "distributions": [
      { "transition": "Cancerous", "distribution": 0.002 },
      { "transition": "Terminal",  "distribution": 0.998 }
    ]
  },
  {
    "condition": { "condition_type": "Gender", "gender": "M" },
    "distributions": [
      { "transition": "Cancerous", "distribution": 0.003 },
      { "transition": "Terminal",  "distribution": 0.997 }
    ]
  }
]
```

### 4.4 Condition types in transitions

| `condition_type` | What it checks |
|-----------------|----------------|
| `"Gender"` | `"gender": "M"` or `"F"` |
| `"Attribute"` | Named attribute with `operator` and `value`. Operators: `"=="`, `"!="`, `"<"`, `">"`, `"<="`, `">="`, `"is nil"`, `"is not nil"` |
| `"Age"` | `"operator"` and `"quantity"` / `"unit"` |
| `"Active Condition"` | Whether a condition with given codes is currently active |
| `"Active Medication"` | Whether a medication with given codes is currently active |

---

## 5. How Codes and Ontologies Are Used

Every clinical state that produces CSV output uses at least one ontology code. Codes are
always an array, allowing multiple vocabulary entries for the same concept.

```json
"codes": [
  {
    "system": "SNOMED-CT",
    "code": "91861009",
    "display": "Acute myeloid leukemia (disorder)"
  }
]
```

### Code systems used in `aml_disease_model.json`

| System | Used for | Example |
|--------|----------|---------|
| **SNOMED-CT** | Conditions, procedures, encounter types, qualitative result values | `91861009` = AML disorder |
| **LOINC** | All observations and lab tests | `6690-2` = WBC count |
| **RxNorm** | All medications | `83367` = tretinoin 10 MG |
| **NUBC** | Discharge dispositions | `"1"` = Discharge to Home |

### Why LOINC for observations?

LOINC (Logical Observation Identifiers Names and Codes) is the international standard for
laboratory and clinical observations. Synthea's CSV exporter and FHIR exporter both propagate
LOINC codes into output records. Using LOINC codes means your synthetic data is
interoperable with real-world lab systems and FHIR servers.

### Why SNOMED-CT for conditions and procedures?

SNOMED-CT is the standard used by clinical decision support systems, EHRs, and FHIR resources
for diagnoses and procedure records. Conditions in `conditions.csv` carry the SNOMED-CT code
from the `ConditionOnset` state.

### Why RxNorm for medications?

RxNorm is the US standard drug vocabulary. Synthea's medication export uses RxNorm codes to
identify specific drug products and routes.

---

## 6. The Two AML Modules in This Repository

| File | Path | Purpose | Produces CSV? |
|------|------|---------|---------------|
| `acute_myeloid_leukemia.json` | `src/main/resources/modules/` | PCOR research module: models levofloxacin prophylaxis in patients ≤21 years; febrile neutropenia; ICU transfer; mortality | Yes |
| `aml_disease_model.json` | `src/main/resources/modules/` | Full WHO-HAEM5 / ELN-2022 disease model: 6 subtypes, complete diagnostic workup, subtype-specific therapies, genomics, complications, MRD | Yes |

Both files are **runnable Synthea modules** — they have `"states"` with `Initial` through
`Terminal`, and both must be present in the `modules/` folder to be loadable.

A **reference-only knowledge document** (`aml_disease_model.json` as it originally existed
before this work) cannot live in `modules/` because the Synthea module loader calls
`definition.get("states").getAsJsonObject()` unconditionally on every `.json` file it finds
there — a file without a `"states"` key causes a `NullPointerException` at startup.

### Which module runs when?

When you use the `--aml` flag or `-class aml`, `AcuteMyeloidLeukemiaApp` is the launcher.

- If you pass **no `-m` flag**, the launcher injects `-m acute_myeloid_leukemia` automatically.
- If you pass `-m aml_model` or `-m aml_disease_model`, the launcher **remaps** these aliases
  to `acute_myeloid_leukemia`.
- If you pass `-m aml_disease_model` directly and want the new full model to run, **use the
  base `App` launcher** (no `--aml` flag):
  ```bash
  ./run_synthea -m aml_disease_model -p 100 Massachusetts Bedford \
    --exporter.csv.export=true
  ```

To run **both modules together** (full cohort gets both clinical pathways):

```bash
./run_synthea -m "acute_myeloid_leukemia:aml_disease_model" -p 100 \
  Massachusetts Bedford --exporter.csv.export=true
```

(Use `:` as the separator on Linux/macOS, `;` on Windows.)

---

## 7. `aml_disease_model.json` — Full Walkthrough

The module is structured in six sequential phases. Every patient who gets AML passes through
all six in order.

```
Initial
  └── Incidence Gate (gender probability)
        └── Cancerous
              └── Onset Delay (age 0–21)
                    └── Assign_AML_Subtype (6 branches)
                          └── Set ELN Risk
                                └── AML_Condition_Onset
                                      └── Inpatient_Encounter
                                            ├── Phase 1: CBC + Chemistry Labs
                                            ├── Phase 2: Diagnostic Procedures
                                            ├── Phase 3: Subtype Assays + Genomics
                                            ├── Phase 4: Induction Therapy (subtype-specific)
                                            ├── Phase 5: Supportive Care
                                            ├── Phase 6: Post-Induction Assessment + Complications
                                            └── End_Encounter → Terminal
```

### 7.1 Incidence gate and onset delay

**Why this is needed:** Synthea runs every module on every patient. If there is no gate,
every patient gets AML, which is not realistic. The gate gives 0.2% of females and 0.3% of
males an AML diagnosis, consistent with ACS Cancer Facts & Figures 2022.

```json
"Initial": {
  "type": "Initial",
  "complex_transition": [
    {
      "condition": { "condition_type": "Gender", "gender": "F" },
      "distributions": [
        { "transition": "Cancerous", "distribution": 0.002 },
        { "transition": "Terminal",  "distribution": 0.998 }
      ]
    },
    {
      "condition": { "condition_type": "Gender", "gender": "M" },
      "distributions": [
        { "transition": "Cancerous", "distribution": 0.003 },
        { "transition": "Terminal",  "distribution": 0.997 }
      ]
    }
  ]
}
```

Once a patient clears the incidence gate, they enter `Cancerous`, which uses a
`distributed_transition` across 22 Delay states (`Delay_0` through `Delay_21`) to simulate
AML onset at various ages. Each delay advances simulated time by the stated number of years
before reaching `Assign_AML_Subtype`.

> **Practical note for testing:** The incidence gate means you need a large population
> (`-p 5000` or more) to see AML patients in the output. For focused testing, bypass the
> gate by generating a population with a pre-specified seed and filtering output, or set
> `-a 0-21` to restrict to the age window.

### 7.2 Subtype assignment and ELN risk classification

Two attributes are set for every patient who gets AML:

| Attribute | Values | Purpose |
|-----------|--------|---------|
| `aml_subtype` | `AML_NPM1`, `AML_FLT3_ITD`, `APL_PML_RARA`, `AML_CBF_RUNX1_RUNX1T1`, `AML_IDH`, `AML_TP53_MDS` | Drives all downstream branching |
| `aml_eln_risk` | `Favorable`, `Intermediate`, `Adverse` | ELN-2022 risk category |

Subtype proportions (from `Assign_AML_Subtype`):

| Subtype | Proportion | ELN-2022 risk | Defining lesion |
|---------|-----------|---------------|-----------------|
| `AML_NPM1` | 27% | Favorable | NPM1 insertion c.860_863dup |
| `AML_FLT3_ITD` | 25% | Intermediate | FLT3-ITD insertion |
| `AML_TP53_MDS` | 20% | Adverse | TP53 SNV + ASXL1 frameshift |
| `APL_PML_RARA` | 10% | Favorable | PML::RARA fusion t(15;17) |
| `AML_IDH` | 10% | Intermediate | IDH1 p.Arg132Cys SNV |
| `AML_CBF_RUNX1_RUNX1T1` | 8% | Favorable | RUNX1::RUNX1T1 fusion t(8;21) |

### 7.3 Condition onset and encounter

After subtype and risk are set, a single `ConditionOnset` fires with SNOMED-CT code
`91861009` (Acute myeloid leukemia). The condition reference is saved to `aml_condition`.

An inpatient encounter opens immediately after. All subsequent states — labs, procedures,
medications, observations — are associated with this encounter until `End_Encounter` fires.

### 7.4 Phase 1 — CBC and chemistry laboratory panel

All patients receive the same initial lab workup regardless of subtype. Every state here is
an `Observation` of category `"laboratory"` with a LOINC code and a random range reflecting
typical AML presentation values.

| State | LOINC | Unit | Range | Clinical rationale |
|-------|-------|------|-------|--------------------|
| `CBC_WBC` | `6690-2` | 10\*3/uL | 3–120 | WBC elevated in most AML; range spans hypocellular to hyperleukocytic |
| `CBC_Hemoglobin` | `718-7` | g/dL | 6–10.5 | Anaemia from marrow failure |
| `CBC_Platelets` | `777-3` | 10\*3/uL | 10–100 | Thrombocytopaenia from marrow displacement |
| `CBC_ANC` | `751-8` | 10\*3/uL | 0.1–1.0 | Severe neutropaenia; defines infection risk |
| `Peripheral_Blood_Blasts` | `709-6` | % | 20–80 | ≥20% blasts required for AML diagnosis |
| `LDH_Chemistry` | `14804-9` | U/L | 300–2000 | Elevated from rapid cell turnover |
| `Creatinine_Chemistry` | `2160-0` | mg/dL | 0.4–1.2 | Baseline renal function before nephrotoxic drugs |
| `Uric_Acid_Chemistry` | `3084-1` | mg/dL | 4–12 | Pre-treatment TLS risk assessment |

### 7.5 Phase 2 — Diagnostic procedures and bone marrow workup

Four procedures fire for all patients. Each is a `Procedure` with a SNOMED-CT code, a
duration range, and `"reason": "aml_condition"`.

| State | SNOMED-CT | Duration |
|-------|-----------|----------|
| `Bone_Marrow_Biopsy` | `86273004` | 30–60 min |
| `BM_Blast_Percentage` *(Observation)* | LOINC `26464-8` | — |
| `Karyotype_Procedure` | `734877004` | 60–120 min |
| `FISH_Procedure` | `445484009` | 30–60 min |
| `NGS_Panel_Procedure` | `1186936003` | 60–120 min |

`BM_Blast_Percentage` is an `Observation` (not a `Procedure`) that immediately follows the
biopsy — it records the blast % result from the marrow sample (range 20–90%).

After `NGS_Panel_Procedure`, the module reaches `Subtype_Assay_Branch`, which uses
`complex_transition` on `aml_subtype` to route each patient to their confirmatory assay.

### 7.6 Genomic alterations

Each subtype has a dedicated `Genomics_*` state of type `SetAttribute` that sets
`"genomics_alterations"` to a list of alteration objects. The CSV exporter reads this
attribute and writes one row to `genomics.csv` per object.

#### Fusion subtypes (APL, CBF) — RT-PCR confirmatory path

APL and CBF patients first receive an RT-PCR procedure (`RT_PCR_PML_RARA` or
`RT_PCR_RUNX1_RUNX1T1`) and a qualitative fusion result observation, then the genomics
attribute is set.

Example — APL:

```json
"Genomics_APL": {
  "type": "SetAttribute",
  "attribute": "genomics_alterations",
  "value": [
    {
      "method_genomics": "RT-PCR",
      "source_genomics": "Bone marrow",
      "alteration_type": "Fusion",
      "gene": "PML",
      "gene_other": "RARA",
      "fusion": "PML::RARA",
      "structural_event": "t(15;17)(q24.1;q21.2)",
      "alteration_status": "Pathogenic",
      "genome_version": "GRCh38",
      "vaf": 0.50
    }
  ],
  "direct_transition": "Allopurinol_TLS_Prophylaxis"
}
```

#### SNV/Insertion subtypes (NPM1, FLT3-ITD, IDH, TP53/MDS) — NGS path

These subtypes skip RT-PCR and go directly from the molecular observation to their
`Genomics_*` state.

Example — NPM1:

```json
"Genomics_NPM1": {
  "type": "SetAttribute",
  "attribute": "genomics_alterations",
  "value": [
    {
      "method_genomics": "NGS",
      "source_genomics": "Bone marrow",
      "alteration_type": "Insertion",
      "gene": "NPM1",
      "alteration_status": "Pathogenic",
      "hgvs_coding": "c.860_863dup",
      "hgvs_protein": "p.Trp288CysfsTer12",
      "genome_version": "GRCh38",
      "vaf": 0.45
    }
  ],
  "direct_transition": "Allopurinol_TLS_Prophylaxis"
}
```

TP53/MDS is the only subtype with **two alterations** in the list — one row per alteration
appears in `genomics.csv`:

```json
"Genomics_TP53_MDS": {
  "type": "SetAttribute",
  "attribute": "genomics_alterations",
  "value": [
    {
      "method_genomics": "NGS", "gene": "TP53",
      "alteration_type": "SNV",
      "hgvs_coding": "c.817C>T", "hgvs_protein": "p.Arg273Cys",
      "vaf": 0.55, ...
    },
    {
      "method_genomics": "NGS", "gene": "ASXL1",
      "alteration_type": "SNV",
      "hgvs_coding": "c.1900_1901dup", "hgvs_protein": "p.Glu634GlyfsTer12",
      "vaf": 0.38, ...
    }
  ],
  "direct_transition": "Allopurinol_TLS_Prophylaxis"
}
```

#### All genomics fields

| Field | Required | Notes |
|-------|----------|-------|
| `method_genomics` | Yes | Testing method: `"NGS"`, `"RT-PCR"`, `"FISH"`, `"Karyotype"`, `"Flow cytometry"` |
| `source_genomics` | Yes | Sample source: `"Bone marrow"`, `"Peripheral blood"` |
| `alteration_type` | Yes | `"SNV"`, `"Insertion"`, `"Deletion"`, `"Fusion"`, `"CNV"` |
| `gene` | Yes | HGNC gene symbol |
| `gene_other` | No | Second gene partner in a fusion |
| `fusion` | No | Fusion name string e.g. `"PML::RARA"` |
| `structural_event` | No | Cytogenetic notation e.g. `"t(15;17)(q24.1;q21.2)"` |
| `alteration_status` | Yes | `"Pathogenic"`, `"VUS"`, `"Benign"` |
| `hgvs_coding` | No | HGVS cDNA notation |
| `hgvs_protein` | No | HGVS protein notation |
| `genome_version` | Yes | `"GRCh38"` or `"GRCh37"` |
| `vaf` | No | Variant allele frequency (0.0–1.0) |
| `date_genomics` | No | If omitted, the exporter assigns a date on/after condition onset |
| `chromosome` | No | Chromosome identifier |
| `abnormal_cells_karyo` | No | Percent abnormal cells by karyotype |
| `abnormal_cells_fish` | No | Percent abnormal cells by FISH |
| `external_db_id` | No | External database identifier (ClinVar, COSMIC, etc.) |

### 7.7 Phase 4 — Induction therapy

After genomics are set, `Allopurinol_TLS_Prophylaxis` fires for all patients (TLS
prophylaxis starts before chemotherapy), then `Induction_Therapy_Branch` uses
`complex_transition` on `aml_subtype` to route to the correct regimen.

| Subtype | Regimen | States |
|---------|---------|--------|
| `APL_PML_RARA` | ATRA + arsenic trioxide | `Give_ATRA` → `Give_ATO` |
| `AML_FLT3_ITD` | Cytarabine + daunorubicin + midostaurin (7+3+M) | `Give_Cytarabine_FLT3` → `Give_Daunorubicin_FLT3` → `Give_Midostaurin` |
| `AML_NPM1` | Cytarabine + daunorubicin (7+3) | `Give_Cytarabine_NPM1` → `Give_Daunorubicin_NPM1` |
| `AML_CBF_RUNX1_RUNX1T1` | Cytarabine + daunorubicin + gemtuzumab ozogamicin | `Give_Cytarabine_CBF` → `Give_Daunorubicin_CBF` → `Give_Gemtuzumab` |
| `AML_IDH` | Cytarabine + daunorubicin + ivosidenib | `Give_Cytarabine_IDH` → `Give_Daunorubicin_IDH` → `Give_Ivosidenib` |
| `AML_TP53_MDS` | Venetoclax + azacitidine (HMA-based) | `Give_Venetoclax` → `Give_Azacitidine` |

All medication states use `"reason": "aml_condition"` and include a `"prescription"` block
with dosage and duration.

After the last medication for each regimen, control flows to `Induction_Chemo_Procedure`
(SNOMED-CT `367336001`, Chemotherapy procedure).

### 7.8 Phase 5 — Supportive care (all subtypes)

Three prophylactic medications are given to every patient regardless of subtype:

| State | Drug | RxNorm | Rationale |
|-------|------|--------|-----------|
| `Antimicrobial_Prophylaxis` | Levofloxacin 500 mg | `199885` | Antibacterial prophylaxis during neutropaenia |
| `Antifungal_Prophylaxis` | Posaconazole 40 mg/mL suspension | `703569` | Antifungal prophylaxis during induction |
| `Antiviral_Prophylaxis` | Acyclovir 400 mg | `14581` | HSV/VZV prophylaxis during immunosuppression |

### 7.9 Phase 6 — Post-induction assessment and complications

Two post-induction vital observations fire first: `Post_Induction_ANC` (ANC nadir,
range 0.1–0.5 ×10³/µL) and `Post_Induction_Temperature`.

Then `Complication_Branch` stochastically assigns one complication (or none) via
`distributed_transition`:

| State | Probability | SNOMED-CT code |
|-------|------------|----------------|
| `Febrile_Neutropenia_Onset` | 55% | `409089005` |
| `Tumor_Lysis_Syndrome_Onset` | 10% | `426263006` |
| `DIC_Onset` | 5% | `234466008` |
| *(none — proceed to MRD)* | 30% | — |

All three complication states use `assign_to_attribute` so the conditions can be properly
ended. All patients then receive:

- `MRD_Flow_Cytometry` — procedure (SNOMED-CT `116148004`)
- `MRD_Result_Observation` — quantitative MRD result (LOINC `59776-2`, range 0.001–5.0%)

Then all active conditions are closed (`End_Febrile_Neutropenia` → `End_TLS` → `End_DIC` →
`End_AML_Condition`) and the encounter is closed with discharge disposition "Discharge to
Home".

---

## 8. Setting Up and Running the Model

### 8.1 Prerequisites

| Requirement | Version |
|-------------|---------|
| Java JDK | 11 or 17 (LTS recommended) |
| Gradle | Bundled via `./gradlew` wrapper — no install needed |
| Operating system | Linux, macOS, or Windows |

### 8.2 Build

```bash
git clone https://github.com/synthetichealth/synthea.git
cd synthea
./gradlew build
```

This compiles the code and runs all unit tests. A successful build produces no test failures.

### 8.3 Running the full AML disease model

The `aml_disease_model` module is a standard Synthea module. Run it directly with `-m`:

```bash
./run_synthea -m aml_disease_model -p 5000 Massachusetts Bedford \
  --exporter.csv.export=true \
  --exporter.baseDirectory=./output_aml_disease
```

Use `5000` or more patients because the incidence gate (0.2–0.3%) means only ~10–15 patients
per 5000 will actually have AML. With `-p 100`, you may get 0 AML cases by chance.

**To guarantee AML patients appear, restrict the age range:**

```bash
./run_synthea -m aml_disease_model -p 500 -a 0-21 Massachusetts Bedford \
  --exporter.csv.export=true \
  --exporter.baseDirectory=./output_aml_disease
```

The onset delay (0–21 years) means patients only develop AML within the age window. Fixing
`-a 0-21` eliminates the search space wasted on older patients.

**Reproducible run with a fixed seed:**

```bash
./run_synthea -m aml_disease_model -p 1000 -a 0-21 -s 424242 Massachusetts Bedford \
  --exporter.csv.export=true \
  --exporter.baseDirectory=./output_aml_seed_424242
```

The same seed, module, population, and age range always produce the same patients.

### 8.4 Running the AML launcher (AcuteMyeloidLeukemiaApp)

The AML launcher is a wrapper around the base `App` that defaults to the
`acute_myeloid_leukemia` PCOR module:

```bash
./run_synthea --aml -p 500 Massachusetts Bedford --exporter.csv.export=true
```

Or equivalently:

```bash
./run_synthea -class aml -p 500 Massachusetts Bedford --exporter.csv.export=true
```

The AML launcher adds extra flags:

| Flag | Effect |
|------|--------|
| `-class aml` | Routes to `AcuteMyeloidLeukemiaApp`; consumed and not passed to `App` |
| `-start_date YYYYMMDD` | Alias for `-r` (reference date) |
| `-end_date YYYYMMDD` | Alias for `-e` (end date) |
| `-gender 0-100` | Integer male percentage; launcher splits the population and runs twice |
| `-m aml_model` or `-m aml_disease_model` | Remapped to `acute_myeloid_leukemia` |

**To run `aml_disease_model` through the AML launcher**, pass it directly with the base `-m`
flag and **no `--aml`**:

```bash
./run_synthea -m aml_disease_model -p 500 -a 0-21 Massachusetts Bedford \
  --exporter.csv.export=true
```

### 8.5 Key command-line flags reference

| Flag | Type | Effect |
|------|------|--------|
| `-p N` | Integer | Population size |
| `-s N` | Long integer | Random seed (same seed = same output) |
| `-a MIN-MAX` | Age range | Restrict generated patient ages, e.g. `0-21` |
| `-g M` or `-g F` | Gender | Generate only male or only female patients |
| `-m NAME` | Module filter | Only load modules whose filename contains `NAME`; supports wildcards (`allerg*`) and multiple values separated by `:` (Linux) or `;` (Windows) |
| `-d PATH` | Directory | Add an additional local module directory |
| `-c PATH` | File | Load a local `synthea.properties` override file |
| `-r YYYYMMDD` | Date | Reference date for simulation start |
| `-e YYYYMMDD` | Date | End date for simulation |
| `--exporter.csv.export=true` | Boolean property | Enable CSV output |
| `--exporter.baseDirectory=PATH` | String property | Output directory |
| `--exporter.fhir.export=true` | Boolean property | Enable FHIR R4 output |

Any property from `src/main/resources/synthea.properties` can be overridden on the command
line with `--property.name=value`.

### 8.6 Enabling CSV export in `synthea.properties`

Instead of passing `--exporter.csv.export=true` every run, set it permanently:

```properties
# src/main/resources/synthea.properties
exporter.csv.export = true
exporter.baseDirectory = ./output
```

---

## 9. Expected CSV Output

### 9.1 Output directory layout

```
output/
  csv/
    patients.csv
    encounters.csv
    conditions.csv
    observations.csv
    procedures.csv
    medications.csv
    genomics.csv
    allergies.csv
    careplans.csv
    ... (other standard Synthea CSVs)
```

### 9.2 What `aml_disease_model.json` writes to each file

| CSV file | Written by | Columns of interest |
|----------|-----------|---------------------|
| `patients.csv` | Lifecycle module | `Id`, `BIRTHDATE`, `DEATHDATE`, `GENDER`, `RACE` |
| `encounters.csv` | `Inpatient_Encounter` | `START`, `STOP`, `PATIENT`, `ENCOUNTERCLASS=inpatient`, `REASONCODE=185347001` |
| `conditions.csv` | `AML_Condition_Onset` and complication onset states | `START`, `STOP`, `CODE` (SNOMED-CT), `DESCRIPTION` |
| `observations.csv` | All `Observation` states | `DATE`, `PATIENT`, `CODE` (LOINC), `VALUE`, `UNITS`, `TYPE` |
| `procedures.csv` | All `Procedure` states | `DATE`, `PATIENT`, `CODE` (SNOMED-CT), `DESCRIPTION`, `REASONCODE` |
| `medications.csv` | All `MedicationOrder` states | `START`, `STOP`, `PATIENT`, `CODE` (RxNorm), `DESCRIPTION`, `REASONCODE` |
| `genomics.csv` | `Genomics_*` `SetAttribute` states | `PATIENT`, `GENE`, `ALTERATION_TYPE`, `HGVS_CODING`, `VAF`, `METHOD_GENOMICS`, etc. |

### 9.3 Per-subtype output checklist

For a patient with `aml_subtype = AML_FLT3_ITD`, you should see:

**`conditions.csv`**
- AML onset row: SNOMED-CT `91861009`
- Febrile neutropenia row (55% probability): SNOMED-CT `409089005`

**`observations.csv`**
- LOINC `6690-2` — WBC
- LOINC `718-7` — Haemoglobin
- LOINC `777-3` — Platelets
- LOINC `751-8` — ANC (twice: at admission and post-induction)
- LOINC `709-6` — Peripheral blast %
- LOINC `14804-9` — LDH
- LOINC `2160-0` — Creatinine
- LOINC `3084-1` — Uric acid
- LOINC `26464-8` — BM blast %
- LOINC `85176-4` — FLT3-ITD result (Positive)
- LOINC `8310-5` — Temperature (post-induction)
- LOINC `59776-2` — MRD result

**`procedures.csv`**
- SNOMED-CT `86273004` — Bone marrow biopsy
- SNOMED-CT `734877004` — Karyotype
- SNOMED-CT `445484009` — FISH
- SNOMED-CT `1186936003` — NGS panel
- SNOMED-CT `367336001` — Chemotherapy
- SNOMED-CT `116148004` — Flow cytometry (MRD)

**`medications.csv`**
- RxNorm `1152` — Allopurinol
- RxNorm `197361` — Cytarabine
- RxNorm `3002` — Daunorubicin
- RxNorm `1860487` — Midostaurin
- RxNorm `199885` — Levofloxacin
- RxNorm `703569` — Posaconazole
- RxNorm `14581` — Acyclovir

**`genomics.csv`**
- One row: gene=`FLT3`, alteration\_type=`Insertion`, method=`NGS`, VAF=0.42

---

## 10. How to Extend the Model

### 10.1 Adding a new subtype

1. Add a new `SetAttribute` state under `Assign_AML_Subtype` and adjust the distributions so
   they still sum to `1.0`.
2. Add `Set_Subtype_NEWNAME` and a corresponding `Set_ELN_Risk_*` target.
3. Add a `Genomics_NEWNAME` state with the correct alteration object(s).
4. Add the new subtype to the `Subtype_Assay_Branch` `complex_transition`.
5. Add the new subtype to the `Induction_Therapy_Branch` `complex_transition` and create the
   corresponding medication states.

### 10.2 Adding a new observation

Add an `Observation` state at the point in the flow where you want it recorded. Place it
**inside the encounter** (after `Inpatient_Encounter`, before `End_Encounter`). Wire it into
the chain with `direct_transition` on the preceding state.

```json
"New_Lab": {
  "type": "Observation",
  "category": "laboratory",
  "unit": "ng/mL",
  "codes": [
    { "system": "LOINC", "code": "XXXX-X", "display": "My new test" }
  ],
  "range": { "low": 0.0, "high": 100.0 },
  "direct_transition": "Next_State"
}
```

### 10.3 Adding a second genomic alteration to an existing subtype

Edit the `"value"` list in the relevant `Genomics_*` state. Each object in the list becomes
one row in `genomics.csv`.

```json
"Genomics_NPM1": {
  "type": "SetAttribute",
  "attribute": "genomics_alterations",
  "value": [
    {
      "gene": "NPM1", "alteration_type": "Insertion",
      "hgvs_coding": "c.860_863dup", "vaf": 0.45, ...
    },
    {
      "gene": "DNMT3A", "alteration_type": "SNV",
      "hgvs_coding": "c.2644C>T", "hgvs_protein": "p.Arg882Cys",
      "vaf": 0.48, ...
    }
  ],
  "direct_transition": "Allopurinol_TLS_Prophylaxis"
}
```

### 10.4 Adding consolidation therapy

After `End_Encounter`, the patient currently goes to `Terminal`. To add a consolidation
phase, replace `"direct_transition": "Terminal"` on `End_Encounter` with a transition to a
new encounter state for consolidation, then chain consolidation medication states, then
`EncounterEnd` → `Terminal`.

### 10.5 Validating your changes

Run a quick structural check in Python before running Synthea:

```python
import json

with open("src/main/resources/modules/aml_disease_model.json") as f:
    module = json.load(f)

state_names = set(module["states"].keys())
referenced = set()

def collect_transitions(obj):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ("direct_transition", "transition"):
                referenced.add(v)
            elif k == "condition_onset":
                pass  # handled separately
            else:
                collect_transitions(v)
    elif isinstance(obj, list):
        for item in obj:
            collect_transitions(item)

collect_transitions(module["states"])

missing = referenced - state_names
print("Missing transition targets:", missing or "none")
print("Total states:", len(state_names))
```

Then run Synthea with `-p 0` to verify the module loads without errors:

```bash
./run_synthea -m aml_disease_model -p 0 Massachusetts Bedford
```

A zero-population run loads all modules and validates them but generates no patients. If there
is a syntax error or broken transition in the JSON, it will appear here rather than partway
through a long generation run.

---

## 11. Troubleshooting

### `genomics.csv` is empty or missing

**Cause:** The module is running but no patient ever reaches a `Genomics_*` state, or the
attribute is set before `ConditionOnset`.

**Fix:** Confirm:
1. The `genomics_alterations` attribute is set **after** `AML_Condition_Onset` fires.
2. The patient actually has AML — check `conditions.csv` for SNOMED-CT `91861009`.
3. CSV export is enabled: `--exporter.csv.export=true`.

### No AML rows appear in `conditions.csv`

**Cause:** The incidence gate eliminated all patients before they reached `AML_Condition_Onset`.

**Fix:** Use a large population (`-p 5000+`) or restrict the age range (`-a 0-21`). With a
very small population and no age restriction, the 0.002–0.003 incidence probability means
you may get zero AML patients by chance.

### NullPointerException at startup

**Cause:** A `.json` file in `src/main/resources/modules/` is missing the required `"states"`
key. Synthea calls `definition.get("states").getAsJsonObject()` unconditionally on every file
it finds in that directory.

**Fix:** Either add a valid `"states"` block with at least `Initial` and `Terminal`, or move
the non-module file to a different directory (e.g. `src/main/resources/reference/`).

### Module not appearing in `Modules:` output

**Cause:** The `-m` filter does not match the module's filename path.

The module filename is `aml_disease_model.json`. Its registered path is `aml_disease_model`.
The `-m` filter uses Apache Commons `WildcardFileFilter` (case-insensitive). These all match:

```
-m aml_disease_model        exact match
-m aml_disease*             wildcard
-m *disease*                wildcard
```

These do **not** match (different name):

```
-m aml_model                no file named aml_model.json
-m acute_myeloid_leukemia   different module entirely
```

When using the `--aml` launcher, `-m aml_model` and `-m aml_disease_model` are remapped to
`acute_myeloid_leukemia`. Use the base launcher if you want `aml_disease_model` to run.

### Medication end states cause errors

If you add `MedicationEnd` states, ensure `"medication_order"` matches the
`"assign_to_attribute"` value from the corresponding `MedicationOrder`. If the medication was
never ordered (because the patient took a different subtype branch), `MedicationEnd` on a
nil reference will be silently skipped, which is safe.

### Distributions do not sum to 1.0

Synthea will throw an exception at load time if the distributions in a
`distributed_transition` or `complex_transition.distributions` array do not sum to exactly
`1.0` (within floating-point tolerance). Use a Python check:

```python
for state_name, state in module["states"].items():
    dt = state.get("distributed_transition", [])
    if dt:
        total = sum(entry["distribution"] for entry in dt)
        if abs(total - 1.0) > 1e-6:
            print(f"BAD DISTRIBUTION in {state_name}: sums to {total}")
```
