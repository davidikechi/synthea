# Understanding the AML Synthea Module — A Plain-English Guide

This guide explains how the AML (Acute Myeloid Leukemia) simulation works in this project.
You do not need a medical or programming background to read it — but some basic biology and
computer-science vocabulary will help along the way.

---

## Table of Contents

1. [What is Synthea and why does it exist?](#1-what-is-synthea-and-why-does-it-exist)
2. [What is a module?](#2-what-is-a-module)
3. [How does a module move a patient forward? (States and Transitions)](#3-how-does-a-module-move-a-patient-forward-states-and-transitions)
4. [The building blocks — every state type explained](#4-the-building-blocks--every-state-type-explained)
5. [How codes and labels are used](#5-how-codes-and-labels-are-used)
6. [The two AML modules in this project](#6-the-two-aml-modules-in-this-project)
7. [Walking through aml_disease_model.json from top to bottom](#7-walking-through-aml_disease_modeljson-from-top-to-bottom)
8. [How to run the model and what commands to use](#8-how-to-run-the-model-and-what-commands-to-use)
9. [What files does the model produce?](#9-what-files-does-the-model-produce)
10. [How to add something new to the model](#10-how-to-add-something-new-to-the-model)
11. [Common problems and how to fix them](#11-common-problems-and-how-to-fix-them)

---

## 1. What is Synthea and why does it exist?

**Synthea** is a computer program that creates *fake but realistic* patient medical records.
Think of it as a patient-history generator — it invents thousands of fictional people and
writes out their entire medical story: what diseases they got, what tests were run, what
medicines they took, and so on.

**Why do this?** Real patient data is private and protected by law. But medical researchers,
software developers, and students still need realistic data to test their tools and ideas.
Synthea solves this by producing data that *looks* real without containing any actual
person's information.

**What does it produce?** Synthea writes standard output files — spreadsheets (CSV) and
healthcare-specific formats (FHIR). Every row in those spreadsheets came from the rules
written in the module files.

**Is it random?** Partly. Synthea uses controlled randomness: if you give it the same
starting number (called a *seed*), it will always produce exactly the same patients. That
makes experiments repeatable.

---

## 2. What is a module?

A **module** is a rulebook written in a file ending in `.json`. It describes one piece of a
patient's health story. There is one module for heart disease, one for diabetes, one for
AML, and so on. All modules run at the same time for every patient, so each patient can
have multiple conditions.

The module file is structured like this:

```
{
  "name": "Name of the module",
  "gmf_version": 2,
  "states": {
    ... all the states ...
  }
}
```

The most important part is `"states"` — a list of named steps. A patient starts at the
first step and moves through the list one step at a time until they reach the end.

**The minimum valid module looks like this:**

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

This module does nothing — the patient enters and immediately exits. Every real module adds
meaningful steps between `Initial` and `Terminal`.

---

## 3. How does a module move a patient forward? (States and Transitions)

Imagine a patient walking through a hospital corridor. At each door they can:

- **Always go through the same door** — this is a `direct_transition`
- **Randomly go through one of several doors** (like a coin flip) — this is a
  `distributed_transition`
- **Choose a door based on a condition** ("only go left if you have a fever") — this is a
  `complex_transition`

### direct_transition

The patient always goes to exactly one next step:

```json
"direct_transition": "Next_Step_Name"
```

### distributed_transition

The patient randomly goes to one of several steps. The numbers must add up to exactly 1.0
(meaning 100%):

```json
"distributed_transition": [
  { "transition": "Step_A", "distribution": 0.27 },
  { "transition": "Step_B", "distribution": 0.25 },
  { "transition": "Step_C", "distribution": 0.48 }
]
```

In this example: 27% of patients go to Step_A, 25% to Step_B, and 48% to Step_C.

### complex_transition

The module checks a condition and routes the patient based on what is true. The first
matching condition wins. An entry without a condition is the fallback (default):

```json
"complex_transition": [
  {
    "condition": {
      "condition_type": "Attribute",
      "attribute": "aml_subtype",
      "operator": "==",
      "value": "AML_NPM1"
    },
    "transition": "NPM1_Branch"
  },
  {
    "transition": "Default_Branch"
  }
]
```

### Patient attributes — the patient's memory

**Attributes** are like sticky notes on a patient. A step can write a note (like `aml_subtype = AML_NPM1`)
and a later step can read that note to decide what to do. Attributes exist only inside the
simulation — they do not appear in the output spreadsheets on their own.

Two special attributes *do* affect the output:

| Attribute | What it does |
|-----------|--------------|
| `genomics_alterations` | When set to a list of genetic changes, those changes are written to `genomics.csv` |
| `cause_of_death` | Used by steps that record a patient's death |

---

## 4. The building blocks — every state type explained

### Initial

Every module has exactly one. It is the entry point — nothing happens here, it just points
to the next step.

```json
"Initial": {
  "type": "Initial",
  "direct_transition": "First_Real_Step"
}
```

### Terminal

Every module has exactly one. It is the exit point. Once a patient reaches this step, the
module is finished for that patient.

```json
"Terminal": {
  "type": "Terminal"
}
```

### Simple

A routing-only step. It does no medical work — its only purpose is to hold a transition
(usually a `distributed_transition` or `complex_transition`) that does not belong inside a
medical step.

```json
"Assign_AML_Subtype": {
  "type": "Simple",
  "distributed_transition": [
    { "transition": "Set_Subtype_NPM1",    "distribution": 0.27 },
    { "transition": "Set_Subtype_FLT3_ITD","distribution": 0.25 }
  ]
}
```

### SetAttribute

Writes a value to the patient's memory (an attribute). By itself it does not create any row
in the output spreadsheets — unless the attribute name is `genomics_alterations`.

```json
"Set_Subtype_NPM1": {
  "type": "SetAttribute",
  "attribute": "aml_subtype",
  "value": "AML_NPM1",
  "direct_transition": "Next_Step"
}
```

**Special case:** When `attribute` is `genomics_alterations` and `value` is a list of
genetic-change objects, the spreadsheet exporter reads this list and writes one row per
object into `genomics.csv`.

### Delay

Moves simulated time forward without doing any medical work. Used to model the patient
ageing before diagnosis, or waiting between hospital visits.

```json
"Delay_5": {
  "type": "Delay",
  "exact": { "quantity": 5, "unit": "years" },
  "direct_transition": "Next_Step"
}
```

Time units available: `"years"`, `"months"`, `"weeks"`, `"days"`, `"hours"`, `"minutes"`.

You can also use a random range:

```json
"Wait_For_Results": {
  "type": "Delay",
  "range": { "low": 3, "high": 7, "unit": "days" },
  "direct_transition": "Next_Step"
}
```

### ConditionOnset

Records a new diagnosis on the patient. This writes a row to `conditions.csv`.

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

`assign_to_attribute` saves a reference to this condition so it can be closed later by a
`ConditionEnd` step. If you forget to close a condition, it will show as still active in
the output.

### ConditionEnd

Closes an active condition. Updates `conditions.csv` with an end date.

```json
"End_AML_Condition": {
  "type": "ConditionEnd",
  "condition_onset": "AML_Condition_Onset",
  "direct_transition": "End_Encounter"
}
```

The value of `"condition_onset"` must match the **state name** of the `ConditionOnset`
that opened this condition.

### Encounter

Opens a hospital visit. All medical steps between an `Encounter` and its matching
`EncounterEnd` are linked to that visit. Writes a row to `encounters.csv`.

```json
"Inpatient_Encounter": {
  "type": "Encounter",
  "encounter_class": "inpatient",
  "reason": "AML_Condition_Onset",
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

Encounter class options: `"inpatient"`, `"ambulatory"`, `"emergency"`, `"wellness"`,
`"urgentcare"`.

### EncounterEnd

Closes the current hospital visit. Can record how the patient left (discharge to home,
transfer, etc.).

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

### Observation

Records a measurement — a lab test result, a vital sign, or a molecular test. Writes a row
to `observations.csv`.

**Numeric observation with a random range (e.g., white blood cell count):**

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
  "direct_transition": "Next_Step"
}
```

**Qualitative (positive/negative) observation:**

```json
"FLT3_ITD_Observation": {
  "type": "Observation",
  "category": "laboratory",
  "unit": "{Qualitative}",
  "codes": [...],
  "value_code": {
    "system": "SNOMED-CT",
    "code": "10828004",
    "display": "Positive (qualifier value)"
  },
  "direct_transition": "Next_Step"
}
```

Key fields for Observation:

| Field | Required | Notes |
|-------|----------|-------|
| `category` | Yes | `"laboratory"`, `"vital-signs"`, `"survey"`, `"imaging"` |
| `unit` | Yes | Use UCUM unit string; use `"{Qualitative}"` for positive/negative results |
| `codes` | Yes | LOINC codes are standard for observations |
| `range` | One of | Random numeric value between `low` and `high` |
| `exact` | One of | Fixed numeric value |
| `value_code` | One of | Coded result (Positive, Negative, etc.) |

### Procedure

Records a clinical procedure (a test, surgery, or treatment done to the patient). Writes a
row to `procedures.csv`.

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
  "reason": "AML_Condition_Onset",
  "direct_transition": "Next_Step"
}
```

`"reason"` should be the **state name** of the `ConditionOnset` that caused this procedure.

### MedicationOrder

Prescribes a medication. Writes a row to `medications.csv`.

```json
"Give_ATRA": {
  "type": "MedicationOrder",
  "assign_to_attribute": "atra_medication",
  "codes": [
    {
      "system": "RxNorm",
      "code": 83367,
      "display": "tretinoin 10 MG Oral Capsule"
    }
  ],
  "reason": "AML_Condition_Onset",
  "prescription": {
    "dosage": { "amount": 2, "frequency": 2, "period": 1, "unit": "days" },
    "duration": { "quantity": 28, "unit": "days" }
  },
  "direct_transition": "Next_Step"
}
```

### MedicationEnd

Stops an active medication. Updates `medications.csv` with a stop date.

```json
"End_ATRA": {
  "type": "MedicationEnd",
  "medication_order": "Give_ATRA",
  "direct_transition": "Next_Step"
}
```

`"medication_order"` must match the **state name** of the `MedicationOrder` step that
prescribed the drug.

---

## 5. How codes and labels are used

Every clinical step that produces output uses at least one code. A code is a standard
number from an international vocabulary that uniquely identifies a concept — a disease, a
test, a drug, or a procedure. Using these standard codes means the synthetic data can work
with real hospital systems.

```json
"codes": [
  {
    "system": "SNOMED-CT",
    "code": "91861009",
    "display": "Acute myeloid leukemia (disorder)"
  }
]
```

### Which vocabulary is used for what?

| Vocabulary | Used for | Real-world example |
|------------|----------|--------------------|
| **SNOMED-CT** | Conditions, procedures, encounters, qualitative results | `91861009` = AML disorder |
| **LOINC** | All lab tests and observations | `6690-2` = White blood cell count |
| **RxNorm** | All medications | `83367` = tretinoin 10 mg capsule |
| **NUBC** | Discharge codes | `"1"` = Discharge to Home |

---

## 6. The two AML modules in this project

| File | Folder | What it does | Produces CSV output? |
|------|--------|--------------|-----------------------|
| `acute_myeloid_leukemia.json` | `src/main/resources/modules/` | PCOR research module. Focuses on levofloxacin antibiotic use in young AML patients, febrile neutropenia, ICU transfer, and death outcomes. | Yes |
| `aml_disease_model.json` | `src/main/resources/modules/` | Full disease model. Covers all six major AML subtypes, complete diagnostic tests, subtype-specific drug treatments, genomic alterations, and complications. | Yes |

Both are real, runnable Synthea modules. Both must be present in the `modules/` folder.

### Which module runs when?

The **AcuteMyeloidLeukemiaApp** launcher controls this:

| What you type | What actually runs |
|---------------|--------------------|
| No `-m` flag at all | `acute_myeloid_leukemia` (default) |
| `-m aml_model` | `aml_disease_model` (alias resolves correctly) |
| `-m aml_disease_model` | `aml_disease_model` (canonical name) |
| `-m acute_myeloid_leukemia` | `acute_myeloid_leukemia` |

To run the **full disease model** directly with the base launcher (not the AML-specific one):

```bash
./run_synthea -m aml_disease_model -p 100 Massachusetts Bedford \
  --exporter.csv.export=true
```

To run **both modules together**:

```bash
# Linux / macOS (colon separator)
./run_synthea -m "acute_myeloid_leukemia:aml_disease_model" -p 100 Massachusetts Bedford \
  --exporter.csv.export=true

# Windows (semicolon separator)
./run_synthea -m "acute_myeloid_leukemia;aml_disease_model" -p 100 Massachusetts Bedford \
  --exporter.csv.export=true
```

---

## 7. Walking through aml_disease_model.json from top to bottom

The module is organized in six phases. Every patient who gets AML passes through all six.

```
Initial
  └── Incidence Gate (gender probability — most patients exit here)
        └── Cancerous
              └── Onset Delay (patient ages 0–21 years before getting AML)
                    └── Assign AML Subtype (6 branches — one per cancer type)
                          └── Set ELN Risk (Favorable / Intermediate / Adverse)
                                └── AML Condition Onset
                                      └── Inpatient Encounter opens
                                            ├── Phase 1: Blood and chemistry tests
                                            ├── Phase 2: Bone marrow procedures
                                            ├── Phase 3: Genetic tests + Genomics
                                            ├── Phase 4: Chemotherapy drugs
                                            ├── Phase 5: Supportive antibiotics / antivirals
                                            ├── Phase 6: Complication check + MRD test
                                            └── Encounter closes → Terminal
```

### Phase 0 — The incidence gate

Not everyone gets AML. In real life, about 0.2% of females and 0.3% of males develop it.
The gate replicates this: most patients pass straight to `Terminal` without getting AML.

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
    ...
  ]
}
```

> **Tip for testing:** Because so few patients get AML, you need a large population
> (`-p 5000` or more) to see any AML cases in the output. To test with smaller numbers,
> use `-a 0-21` to restrict to the age window, or use the `aml_disease_model` module
> directly (it removes the gate so every patient gets AML).

### Phase 0b — Age at onset

AML onset is spread across ages 0–21 years using 22 `Delay` states. Each patient randomly
waits 0, 1, 2, … or 21 years before getting their diagnosis, simulating different ages of
onset.

### Phase 1 — Subtype assignment and risk classification

Two attributes are set for every patient who gets AML:

| Attribute | Possible values | Purpose |
|-----------|-----------------|---------|
| `aml_subtype` | `AML_NPM1`, `AML_FLT3_ITD`, `APL_PML_RARA`, `AML_CBF_RUNX1_RUNX1T1`, `AML_IDH`, `AML_TP53_MDS` | Controls all later branching |
| `aml_eln_risk` | `Favorable`, `Intermediate`, `Adverse` | Standard oncology risk category |

How patients are distributed across subtypes:

| Subtype | % of AML patients | Risk level | Key genetic change |
|---------|--------------------|------------|--------------------|
| `AML_NPM1` | 27% | Favorable | NPM1 gene insertion |
| `AML_FLT3_ITD` | 25% | Intermediate | FLT3 gene duplication |
| `AML_TP53_MDS` | 20% | Adverse | TP53 gene mutation |
| `APL_PML_RARA` | 10% | Favorable | PML–RARA gene fusion |
| `AML_IDH` | 10% | Intermediate | IDH1 gene mutation |
| `AML_CBF_RUNX1_RUNX1T1` | 8% | Favorable | RUNX1–RUNX1T1 fusion |

### Phase 2 — Condition onset and hospital admission

After the subtype is assigned, the module records two things:

1. A `ConditionOnset` for the **specific subtype** (e.g., "AML with NPM1 mutation") —
   this appears in `conditions.csv` with a subtype-specific SNOMED code.
2. A `ConditionOnset` for the **general AML diagnosis** (SNOMED `91861009`) — this also
   appears in `conditions.csv`.

Then the patient is admitted to hospital (`Inpatient_Encounter` opens). Everything from
this point until `End_Encounter` is linked to this visit.

### Phase 3 — Blood tests and bone marrow workup

All patients get the same initial tests regardless of subtype:

| What | Type | Code (LOINC) | Normal range |
|------|------|------|-------------|
| White blood cell count | Lab | `6690-2` | 3–120 ×10³/µL |
| Haemoglobin | Lab | `718-7` | 6–10.5 g/dL |
| Platelets | Lab | `777-3` | 10–100 ×10³/µL |
| Neutrophil count (ANC) | Lab | `751-8` | 0.1–1.0 ×10³/µL |
| Blood blast percentage | Lab | `709-6` | 20–80% |
| LDH (enzyme) | Lab | `14804-9` | 300–2000 U/L |
| Creatinine (kidney) | Lab | `2160-0` | 0.4–1.2 mg/dL |
| Uric acid | Lab | `3084-1` | 4–12 mg/dL |

After the blood tests, four procedures are done on bone marrow:

| Procedure | State name | SNOMED code |
|-----------|-----------|-------------|
| Bone marrow biopsy | `Bone_Marrow_Biopsy` | `86273004` |
| Bone marrow blast % | `BM_Blast_Percentage` *(Observation)* | LOINC `26464-8` |
| Karyotype (chromosome picture) | `Karyotype_Procedure` | `734877004` |
| FISH (gene location test) | `FISH_Procedure` | `445484009` |
| NGS gene panel (full sequencing) | `NGS_Panel_Procedure` | `1186936003` |

### Phase 4 — Subtype-specific genetic tests and genomics

After the bone marrow workup, the module branches based on `aml_subtype`. Each branch does
a confirmatory test and then records the genetic changes in `genomics_alterations`.

**For fusion subtypes (APL, CBF):** An RT-PCR test is run first to confirm the gene fusion,
then genomics are recorded.

**For point-mutation subtypes (NPM1, FLT3, IDH, TP53/MDS):** A molecular observation is
recorded directly from the NGS results, then genomics are recorded.

Every subtype's genomics step is a `SetAttribute` with `"attribute": "genomics_alterations"`.
Each object in the list becomes one row in `genomics.csv`.

Example — NPM1 genomics:

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

**Genomics field reference:**

| Field | Required | Meaning |
|-------|----------|---------|
| `method_genomics` | Yes | How it was found: `"NGS"`, `"RT-PCR"`, `"FISH"`, `"Karyotype"` |
| `source_genomics` | Yes | Where the sample came from: `"Bone marrow"`, `"Peripheral blood"` |
| `alteration_type` | Yes | Type of change: `"SNV"`, `"Insertion"`, `"Deletion"`, `"Fusion"`, `"CNV"` |
| `gene` | Yes | Gene symbol (e.g. `"NPM1"`, `"FLT3"`, `"TP53"`) |
| `gene_other` | No | Second gene in a fusion (e.g. `"RARA"`) |
| `fusion` | No | Fusion name (e.g. `"PML::RARA"`) |
| `structural_event` | No | Chromosome rearrangement (e.g. `"t(15;17)(q24.1;q21.2)"`) |
| `alteration_status` | Yes | Clinical meaning: `"Pathogenic"`, `"VUS"`, `"Benign"` |
| `hgvs_coding` | No | DNA-level change notation (e.g. `"c.860_863dup"`) |
| `hgvs_protein` | No | Protein-level change notation (e.g. `"p.Trp288CysfsTer12"`) |
| `genome_version` | Yes | Reference genome: `"GRCh38"` or `"GRCh37"` |
| `vaf` | No | Fraction of cells with this change (0.0–1.0) |

### Phase 5 — Chemotherapy (induction therapy)

After genomics, all patients receive allopurinol (to prevent a complication called tumor
lysis syndrome), then branch to their subtype-specific chemotherapy:

| Subtype | Drug regimen |
|---------|-------------|
| APL | ATRA + arsenic trioxide |
| FLT3-ITD | Cytarabine + daunorubicin + midostaurin |
| NPM1 | Cytarabine + daunorubicin (classic 7+3) |
| CBF | Cytarabine + daunorubicin + gemtuzumab |
| IDH | Cytarabine + daunorubicin + ivosidenib |
| TP53/MDS | Venetoclax + azacitidine |

After the specific drugs, a general `Induction_Chemo_Procedure` step records the
chemotherapy session itself.

### Phase 6 — Supportive care

Three protective medications are given to every patient regardless of subtype:

| Drug | Purpose |
|------|---------|
| Levofloxacin 500 mg | Prevent bacterial infections during low neutrophil counts |
| Posaconazole suspension | Prevent fungal infections |
| Acyclovir 400 mg | Prevent herpes virus reactivation |

### Phase 7 — Complications and final assessment

Post-chemotherapy vital signs are recorded (ANC nadir and temperature). Then one of four
outcomes is randomly assigned:

| Outcome | Probability |
|---------|------------|
| Febrile neutropenia (fever + low white cells) | 55% |
| Tumor lysis syndrome (cell breakdown products spike) | 10% |
| DIC (clotting disorder) | 5% |
| No complication | 30% |

Finally, a minimal residual disease (MRD) flow cytometry procedure and result are recorded
for all patients. Then all active conditions and medications are closed, and the encounter
ends with "Discharge to Home".

---

## 8. How to run the model and what commands to use

### What you need

| Requirement | Version |
|-------------|---------|
| Java JDK | 11 or 17 (recommended) |
| Gradle | Bundled — no installation needed |
| Operating system | Linux, macOS, or Windows |

### Build the project

```bash
git clone https://github.com/synthetichealth/synthea.git
cd synthea
./gradlew build
```

### Run the full AML disease model

This runs `aml_disease_model.json` directly, generating 100 patients:

```bash
./run_synthea -m aml_disease_model -p 100 Massachusetts Bedford \
  --exporter.csv.export=true \
  --exporter.baseDirectory=./output_aml
```

> Because of the incidence gate, only about 0.2–0.3% of patients will have AML. With
> `-p 100` you may get zero AML patients. Use `-p 5000` or restrict the age range:

```bash
./run_synthea -m aml_disease_model -p 500 -a 0-21 Massachusetts Bedford \
  --exporter.csv.export=true
```

### Use the short alias

`aml_model` is an alias for `aml_disease_model`:

```bash
./run_synthea -m aml_model -p 500 -a 0-21 Massachusetts Bedford \
  --exporter.csv.export=true
```

### Reproducible run with a fixed seed

```bash
./run_synthea -m aml_disease_model -p 1000 -a 0-21 -s 424242 Massachusetts Bedford \
  --exporter.csv.export=true \
  --exporter.baseDirectory=./output_seed_424242
```

The same seed always produces the same patients — useful for comparing results.

### Run the AML launcher (AcuteMyeloidLeukemiaApp)

```bash
./run_synthea --aml -p 500 Massachusetts Bedford --exporter.csv.export=true
```

Or using the class flag:

```bash
./run_synthea -class aml -p 500 Massachusetts Bedford --exporter.csv.export=true
```

The AML launcher adds these extra options:

| Flag | What it does |
|------|-------------|
| `-class aml` | Uses the AML-specific launcher |
| `-start_date YYYYMMDD` | Sets the simulation start date |
| `-end_date YYYYMMDD` | Sets the simulation end date |
| `-gender 0-100` | Integer male percentage; runs twice (once male, once female) |
| `-m aml_model` | Remapped to `aml_disease_model` |

### Key command-line flags

| Flag | Effect |
|------|--------|
| `-p N` | Number of patients to generate |
| `-s N` | Random seed (same seed = same output) |
| `-a MIN-MAX` | Patient age range, e.g. `0-21` |
| `-g M` or `-g F` | Generate only male or only female patients |
| `-m NAME` | Run only modules whose filename matches NAME |
| `--exporter.csv.export=true` | Enable CSV output |
| `--exporter.baseDirectory=PATH` | Where to save output files |

### Enable CSV output permanently

Edit `src/main/resources/synthea.properties`:

```properties
exporter.csv.export = true
exporter.baseDirectory = ./output
```

---

## 9. What files does the model produce?

### Output folder layout

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
```

### What each file contains

| File | Written by | Key columns |
|------|------------|-------------|
| `patients.csv` | Lifecycle module | ID, birth date, death date, gender, race |
| `encounters.csv` | `Inpatient_Encounter` | Start, stop, patient ID, encounter class |
| `conditions.csv` | `AML_Condition_Onset` and complication steps | Start, stop, SNOMED code, description |
| `observations.csv` | All `Observation` steps | Date, LOINC code, value, units |
| `procedures.csv` | All `Procedure` steps | Date, SNOMED code, description, reason |
| `medications.csv` | All `MedicationOrder` steps | Start, stop, RxNorm code, description |
| `genomics.csv` | `Genomics_*` `SetAttribute` steps | Patient, gene, alteration type, HGVS codes, VAF, method |

### What to expect for a patient with AML (FLT3-ITD subtype)

**`conditions.csv`**
- AML with FLT3 mutation (SNOMED `413449008`)
- AML general diagnosis (SNOMED `91861009`)
- Febrile neutropenia (SNOMED `409089005`) — in 55% of patients

**`observations.csv`**
- WBC, Haemoglobin, Platelets, ANC, Blast %, LDH, Creatinine, Uric acid
- BM blast %, FLT3-ITD result (Positive)
- Post-induction ANC and temperature
- MRD result

**`procedures.csv`**
- Bone marrow biopsy, Karyotype, FISH, NGS panel
- Chemotherapy procedure
- MRD flow cytometry

**`medications.csv`**
- Allopurinol, Cytarabine, Daunorubicin, Midostaurin
- Levofloxacin, Posaconazole, Acyclovir

**`genomics.csv`**
- One row: gene=`FLT3`, alteration_type=`Insertion`, method=`NGS`, VAF=0.42

---

## 10. How to add something new to the model

### Add a new AML subtype

1. Add a new entry in `Assign_AML_Subtype`'s `distributed_transition`. Make sure all
   probabilities still add up to 1.0.
2. Create a `Set_Subtype_NEWNAME` state that sets `aml_subtype` to the new value.
3. Create a `Genomics_NEWNAME` state with the correct alteration objects.
4. Add the new subtype to `Subtype_Assay_Branch` (diagnostic routing).
5. Add the new subtype to `Induction_Therapy_Branch` (treatment routing).
6. Create the medication states for the new treatment.

### Add a new lab test

Add an `Observation` state anywhere **inside the encounter** (after `Inpatient_Encounter`,
before `End_Encounter`) and wire it in with `direct_transition`:

```json
"My_New_Lab": {
  "type": "Observation",
  "category": "laboratory",
  "unit": "ng/mL",
  "codes": [
    { "system": "LOINC", "code": "XXXX-X", "display": "My new test" }
  ],
  "range": { "low": 0.0, "high": 100.0 },
  "direct_transition": "Next_Step"
}
```

### Add a second genetic change to an existing subtype

Edit the `"value"` list in the relevant `Genomics_*` state. Each object becomes one row in
`genomics.csv`:

```json
"Genomics_NPM1": {
  "type": "SetAttribute",
  "attribute": "genomics_alterations",
  "value": [
    { "gene": "NPM1", "alteration_type": "Insertion", ... },
    { "gene": "DNMT3A", "alteration_type": "SNV", ... }
  ],
  "direct_transition": "Allopurinol_TLS_Prophylaxis"
}
```

### Validate your changes before running

Run this Python script to check for broken links between states:

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

Then do a zero-patient run to check the module loads without errors:

```bash
./run_synthea -m aml_disease_model -p 0 Massachusetts Bedford
```

---

## 11. Common problems and how to fix them

### `genomics.csv` is empty or not created

**Why it happens:** The `genomics_alterations` attribute was never set, or CSV export is
not turned on.

**How to fix:**
1. Make sure `--exporter.csv.export=true` is on the command line.
2. Check `conditions.csv` for SNOMED `91861009`. If there are no AML rows, patients never
   reached `AML_Condition_Onset`, so the genomics steps never fired.
3. Make sure `Genomics_*` states come **after** `AML_Condition_Onset` in the flow.

### No AML rows appear in `conditions.csv`

**Why it happens:** The incidence gate sent all patients to `Terminal` before they got AML.

**How to fix:** Use a larger population (`-p 5000` or more), or restrict the age range
with `-a 0-21` so that Synthea focuses on the age window where AML onset happens.

### NullPointerException at startup

**Why it happens:** A `.json` file in the `modules/` folder is missing the required
`"states"` key. Synthea tries to read `"states"` from every file in that folder without
checking first.

**How to fix:** Either add a valid `"states"` block with at least `Initial` and `Terminal`,
or move the file to a different folder (e.g. `src/main/resources/reference/`).

### Module not showing up in the run log

**Why it happens:** The `-m` filter does not match the module's filename.

The file is `aml_disease_model.json`. Its registered path is `aml_disease_model`. These
patterns all match:

```
-m aml_disease_model      exact match
-m aml_disease*           wildcard
-m *disease*              wildcard
-m aml_model              alias — resolved to aml_disease_model automatically
```

These do **not** match:

```
-m acute_myeloid_leukemia   different module entirely
-m disease_model            not a substring of the filename
```

### Distributions do not add up to 1.0

Synthea will throw an error at startup if probabilities do not sum to exactly 1.0. Check
with this Python snippet:

```python
for state_name, state in module["states"].items():
    dt = state.get("distributed_transition", [])
    if dt:
        total = sum(entry["distribution"] for entry in dt)
        if abs(total - 1.0) > 1e-6:
            print(f"Problem in {state_name}: sums to {total}")
```

### A medication end state silently fails

If a `MedicationEnd` step references a medication that was never ordered (because the
patient took a different branch), Synthea silently skips it — this is safe and expected.
If you see that a medication is NOT ending when you expect it to, confirm that the
`medication_order` value in `MedicationEnd` exactly matches the **state name** of the
`MedicationOrder` step that prescribed it.
