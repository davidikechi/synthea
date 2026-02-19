# How Synthea AML Modules Work — A Plain-English Guide

This guide explains how to build a Synthea module step by step.
No medical degree needed. If you can follow a recipe, you can follow this.

---

## What Is Synthea?

Synthea is a program that creates **fake but realistic patients** — complete with medical history, lab results, medications, and more. Researchers use these fake patients to test software and study diseases without using real people's private data.

This guide focuses on modeling **AML (Acute Myeloid Leukemia)**, a type of blood cancer. But the same ideas apply to any disease.

---

## Part 1: The Big Idea — A "Choose Your Own Adventure" Story

A Synthea module is like a **Choose Your Own Adventure book**.

- The patient starts at the beginning (`Initial` state).
- Each page (state) does one thing — like "the patient goes to the hospital" or "the doctor orders a blood test."
- At the end of each page, you go to the next one (a transition).
- The story ends at the last page (`Terminal` state).

Here is the simplest possible module:

```
Start (Initial)  →  End (Terminal)
```

A real AML module looks more like:

```
Start → Assign Cancer Type → Diagnosis → Hospital Visit →
Blood Tests → Bone Marrow Biopsy → DNA Testing → Treatment → End
```

Each arrow is a **transition**. Each box is a **state**.

---

## Part 2: What Are States?

States are the building blocks. Each one does exactly one job.

| State Type       | What It Does                                         | Shows Up In CSV       |
|------------------|------------------------------------------------------|-----------------------|
| `Initial`        | Starting point — every module needs exactly one      | (nothing)             |
| `Terminal`       | Ending point — every module needs exactly one        | (nothing)             |
| `Simple`         | A decision point — picks the next state              | (nothing)             |
| `SetAttribute`   | Saves a value, like "this patient has FLT3 AML"      | (nothing, but drives genomics.csv) |
| `ConditionOnset` | Gives the patient a diagnosis                        | `conditions.csv`      |
| `Encounter`      | Opens a hospital or clinic visit                     | `encounters.csv`      |
| `EncounterEnd`   | Closes the visit (must pair with every Encounter)    | `encounters.csv`      |
| `Observation`    | Records a lab test or measurement                    | `observations.csv`    |
| `Procedure`      | Records a procedure, like a biopsy                   | `procedures.csv`      |
| `MedicationOrder`| Prescribes a medication                              | `medications.csv`     |
| `Death`          | The patient dies (optional, disease-specific)        | `patients.csv`        |

> **Key rule:** Every lab result, medication, and procedure must happen **inside an open Encounter**. Think of the Encounter as the hospital visit — you can only get treated while you're actually at the hospital.

---

## Part 3: How CSV Files Get Created

When Synthea runs, it watches what states each patient passes through and writes rows to spreadsheets (CSV files). Here is what creates each file:

- **`conditions.csv`** — Every `ConditionOnset` state adds a row.
- **`encounters.csv`** — Every `Encounter` / `EncounterEnd` pair adds a row.
- **`observations.csv`** — Every `Observation` state adds a row.
- **`procedures.csv`** — Every `Procedure` state adds a row.
- **`medications.csv`** — Every `MedicationOrder` state adds a row.
- **`genomics.csv`** — Set the special attribute `genomics_alterations` in a `SetAttribute` state; the exporter writes one row per DNA change listed.

If a patient skips a state (for example, takes the "no treatment" path in a branch), those CSV rows do not appear for that patient.

---

## Part 4: Transitions — How the Story Branches

There are three main ways to move between states.

### Option A: Always go to the same next state (direct)

```json
"direct_transition": "Next_State_Name"
```

Use this when there is no choice — the patient always goes to this next step.

### Option B: Go to one of several states by chance (distributed)

```json
"distributed_transition": [
  { "transition": "Path_A", "distribution": 0.60 },
  { "transition": "Path_B", "distribution": 0.40 }
]
```

The numbers are probabilities that must add up to **1.0** (100%). Here, 60% of patients go to Path A and 40% go to Path B. Think of it like flipping a weighted coin.

### Option C: Go to a state based on a condition (complex)

```json
"complex_transition": [
  {
    "condition": {
      "condition_type": "Attribute",
      "attribute": "aml_subtype",
      "operator": "=",
      "value": "AML_FLT3_ITD"
    },
    "transition": "Give_Midostaurin"
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
```

This is like an if-else statement in code. The first condition that is true wins.

---

## Part 5: A Real AML Module, Step by Step

Let's walk through the full `aml_model.json` module that ships with this project.

### Step 1 — Assign a Subtype

Every AML patient gets randomly assigned one of five subtypes. This is like a dice roll at the very start of the story.

```json
"Assign_AML_Subtype": {
  "type": "Simple",
  "distributed_transition": [
    { "transition": "Set_Subtype_FLT3_ITD", "distribution": 0.30 },
    { "transition": "Set_Subtype_NPM1",     "distribution": 0.30 },
    { "transition": "Set_Subtype_APL",      "distribution": 0.15 },
    { "transition": "Set_Subtype_CBF",      "distribution": 0.15 },
    { "transition": "Set_Subtype_TP53",     "distribution": 0.10 }
  ]
}
```

**What are these subtypes?** They are different versions of AML, each defined by different DNA mutations:

| Subtype      | What It Means (plain English)                                          |
|--------------|------------------------------------------------------------------------|
| `AML_FLT3_ITD` | A gene called FLT3 has a copy-paste error that makes cells grow out of control |
| `AML_NPM1`   | A gene called NPM1 has a small insertion (extra letters in the DNA code) |
| `APL_PML_RARA` | Two chromosomes swapped pieces — causes a special "acute promyelocytic" form |
| `AML_CBF`    | Two genes fused together into one (RUNX1 and RUNX1T1)                  |
| `AML_TP53`   | The "guardian of the genome" gene (TP53) is broken — highest-risk form  |

### Step 2 — Save the Subtype

Each `Set_Subtype_*` state saves the subtype into a variable called `aml_subtype`. This works like a sticky note that travels with the patient through the rest of the module.

```json
"Set_Subtype_FLT3_ITD": {
  "type": "SetAttribute",
  "attribute": "aml_subtype",
  "value": "AML_FLT3_ITD",
  "direct_transition": "AML_Condition_Onset"
}
```

### Step 3 — Diagnose the Patient

The `ConditionOnset` state officially gives the patient their AML diagnosis. The codes (SNOMED-CT) are medical dictionary codes that hospitals and researchers use universally.

```json
"AML_Condition_Onset": {
  "type": "ConditionOnset",
  "assign_to_attribute": "aml_condition",
  "codes": [
    {
      "system": "SNOMED-CT",
      "code": "91857003",
      "display": "Acute myeloid leukemia (disorder)"
    }
  ],
  "direct_transition": "Diagnosis_Inpatient_Encounter"
}
```

This writes a row to `conditions.csv`.

### Step 4 — Open a Hospital Visit

The `Encounter` state opens the hospital admission. Everything that happens next (labs, biopsy, medications) happens during this stay.

```json
"Diagnosis_Inpatient_Encounter": {
  "type": "Encounter",
  "encounter_class": "inpatient",
  "reason": "aml_condition",
  ...
}
```

### Step 5 — Blood Tests (Observations)

Six lab tests are recorded. Each one writes a row to `observations.csv`.

| Test                 | Why Doctors Order It                                             |
|----------------------|------------------------------------------------------------------|
| White blood cell count (WBC) | AML often causes very high WBC due to cancerous cells |
| Peripheral blasts    | Counts how many immature "blast" cells are in the blood         |
| Hemoglobin           | AML patients are often anemic (low red blood cells)             |
| Platelets            | Low platelets → higher bleeding risk                            |
| ANC (neutrophils)    | Low neutrophils → higher infection risk                         |
| LDH                  | High LDH signals rapid cell turnover, common in AML             |

Each observation has a `range` — two numbers between which Synthea picks a random value for that patient.

```json
"WBC_Lab": {
  "type": "Observation",
  "category": "laboratory",
  "unit": "10*3/uL",
  "codes": [{ "system": "LOINC", "code": "6690-2", "display": "Leukocytes..." }],
  "range": { "low": 10.0, "high": 100.0 },
  "direct_transition": "Peripheral_Blast_Lab"
}
```

### Step 6 — Bone Marrow Biopsy

A `Procedure` state records the bone marrow biopsy. Doctors stick a needle into the hip bone to remove a small sample of bone marrow and count blast cells directly. If blasts are ≥20%, AML is confirmed.

```json
"Bone_Marrow_Biopsy": {
  "type": "Procedure",
  "codes": [{ "system": "SNOMED-CT", "code": "86273004", "display": "Biopsy of bone marrow" }],
  "duration": { "low": 30, "high": 60, "unit": "minutes" },
  ...
}
```

### Step 7 — Record DNA Changes (Genomics)

This is the most important part for generating `genomics.csv`. A `SetAttribute` state sets the special `genomics_alterations` attribute to a list of DNA changes found in this patient's cancer cells.

The module branches based on the subtype set in Step 1:

```json
"Capture_Genomics_FLT3": {
  "type": "SetAttribute",
  "attribute": "genomics_alterations",
  "value": [
    {
      "method_genomics": "NGS",
      "source_genomics": "Bone marrow",
      "alteration_type": "ITD",
      "gene": "FLT3",
      "alteration_status": "Pathogenic",
      "hgvs_coding": "c.1770_1771ins18",
      "genome_version": "GRCh38",
      "vaf": 0.48,
      "chromosome": "13"
    },
    ...
  ],
  "direct_transition": "Therapy_Branch"
}
```

**Field glossary:**

| Field              | Plain-English Meaning                                                          |
|--------------------|--------------------------------------------------------------------------------|
| `method_genomics`  | How the DNA was tested — NGS = Next-Generation Sequencing; FISH = a dye test  |
| `source_genomics`  | Where the sample came from (usually "Bone marrow")                             |
| `alteration_type`  | What kind of DNA change — SNV (single letter change), ITD (repeated section), Fusion (two genes merged), Insertion |
| `gene`             | Which gene is affected                                                         |
| `alteration_status`| Whether the change is harmful — "Pathogenic" means yes                        |
| `hgvs_coding`      | The exact DNA address of the change (like a GPS coordinate in the genome)      |
| `hgvs_protein`     | How the DNA change changes the protein the gene makes                          |
| `genome_version`   | Which version of the human genome map is being used (GRCh38 is current)       |
| `vaf`              | Variant allele fraction — roughly, what percentage of cancer cells have this change |
| `chromosome`       | Which chromosome the gene lives on                                             |

Each item in the `value` list becomes **one row** in `genomics.csv`.

### Step 8 — Treatment

The module picks a treatment based on the subtype. This mirrors real oncology practice:

| Subtype         | Treatment Given                               | Why                              |
|-----------------|-----------------------------------------------|----------------------------------|
| `AML_FLT3_ITD`  | Cytarabine + Midostaurin                      | Midostaurin targets FLT3 directly |
| `AML_NPM1`      | Daunorubicin + Cytarabine (standard "7+3")    | Standard induction chemotherapy  |
| `APL_PML_RARA`  | Tretinoin (ATRA) + Arsenic trioxide           | Forces blasts to mature and die  |
| `AML_CBF`       | Cytarabine alone                              | Favorable prognosis, less aggressive treatment |
| `AML_TP53`      | Venetoclax + Azacitidine                      | For high-risk or older patients  |

Each `MedicationOrder` state writes a row to `medications.csv`.

### Step 9 — MRD Test and Discharge

After treatment, a residual disease test (MRD) checks how many blast cells are left. Then `EncounterEnd` closes the hospital visit and the patient reaches `Terminal` — the end of the story.

---

## Part 6: Genomics Fields Reference

Here is a complete table of all fields you can include in each genomic alteration object:

| Field                  | Required? | Notes                                                |
|------------------------|-----------|------------------------------------------------------|
| `method_genomics`      | Yes       | How the test was done (NGS, FISH, RT-PCR, Karyotype) |
| `source_genomics`      | Yes       | Sample source (e.g., "Bone marrow", "Peripheral blood") |
| `alteration_type`      | Yes       | SNV, Insertion, Deletion, Fusion, ITD, CNV           |
| `gene`                 | Yes       | Gene name (e.g., FLT3, NPM1, TP53)                  |
| `alteration_status`    | Yes       | Pathogenic, Likely Pathogenic, VUS, Benign           |
| `hgvs_coding`          | No        | DNA-level change notation                            |
| `hgvs_protein`         | No        | Protein-level change notation                        |
| `genome_version`       | No        | GRCh38 or GRCh37                                     |
| `vaf`                  | No        | 0.0–1.0 (fraction of cells with this mutation)       |
| `chromosome`           | No        | Chromosome number or letter (1–22, X, Y)             |
| `gene_other`           | No        | Second gene in a fusion (e.g., RARA in PML::RARA)    |
| `fusion`               | No        | Fusion name in standard format (e.g., PML::RARA)     |
| `structural_event`     | No        | Cytogenetic notation (e.g., t(15;17)(q24.1;q21.2))   |
| `date_genomics`        | No        | If omitted, Synthea auto-generates a realistic date  |
| `external_db_id`       | No        | Optional database identifier (e.g., ClinVar ID)      |

> **Tip:** If you leave out `date_genomics`, Synthea will automatically assign a realistic date that falls between the patient's birth and their last clinical event. You do not need to set it manually.

---

## Part 7: Running the Module

### The simplest command

```bash
./run_synthea --aml -m aml_model -p 100 Massachusetts Bedford \
  --exporter.csv.export=true
```

This generates 100 synthetic AML patients and writes CSV files.

### Or use the class flag (same result)

```bash
./run_synthea -class aml -m aml_model -p 100 Massachusetts Bedford \
  --exporter.csv.export=true
```

### Common flags explained

| Flag | What It Does | Example |
|------|--------------|---------|
| `--aml` | Activates the AML launcher | `--aml` |
| `-class aml` | Same as `--aml`, alternative syntax | `-class aml` |
| `-m aml_model` | Loads only the `aml_model` module | `-m aml_model` |
| `-p 100` | Generate 100 patients | `-p 500` |
| `-s 42` | Set a random seed so results repeat exactly | `-s 99999` |
| `-a 5-21` | Only generate patients aged 5–21 | `-a 40-70` |
| `-g M` | Only generate male patients | `-g F` |
| `-gender 60` | 60% male, 40% female (AML launcher only) | `-gender 55` |
| `--exporter.csv.export=true` | Turn on CSV output | required for CSVs |
| `--exporter.baseDirectory=./out` | Where to save output files | `--exporter.baseDirectory=./my_run` |

### A reproducible example run

```bash
./run_synthea --aml -m aml_model \
  -p 200 -s 424242 -a 18-75 \
  Massachusetts Bedford \
  --exporter.csv.export=true \
  --exporter.baseDirectory=./output_aml
```

Every time you run this exact command, you get the exact same synthetic patients.

### Checking the output

```bash
ls ./output_aml/csv/
```

You should see these files:

```
conditions.csv
encounters.csv
medications.csv
observations.csv
patients.csv
procedures.csv
genomics.csv
```

Open `genomics.csv` to see one row per DNA alteration, with one patient being able to have multiple rows.

---

## Part 8: Tips for Building Your Own Module

1. **Start small, then add.** Get `Initial → ConditionOnset → Encounter → EncounterEnd → Terminal` working first, then add labs and medications.

2. **Always close your Encounter.** Every `Encounter` state needs a matching `EncounterEnd` later. Forgetting this will break the module.

3. **Use attributes to remember things.** If a patient's subtype or risk group matters later in the module, save it with `SetAttribute` and read it with a `complex_transition`.

4. **Set `genomics_alterations` after diagnosis.** The exporter uses the condition onset date to bound the genomics date. Set attributes only after `ConditionOnset`.

5. **Test with a small number of patients first.** Use `-p 10` while building, then scale up.

6. **Check CSVs, not just the module file.** The only way to know it works is to run it and look at the output files.

---

## Part 9: Checklist Before You Call the Module Done

- [ ] `conditions.csv` has an AML row for each patient
- [ ] `encounters.csv` has at least one visit per patient
- [ ] `medications.csv` shows the correct drugs for each subtype
- [ ] `procedures.csv` includes the bone marrow biopsy
- [ ] `observations.csv` has lab values (WBC, blasts, ANC, etc.)
- [ ] `genomics.csv` has at least one row per patient, with gene names and alteration types filled in
- [ ] Running the command twice with the same `-s` seed gives the same output
