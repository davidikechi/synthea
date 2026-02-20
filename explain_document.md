# Synthea AML Module Guide — Plain Language Edition

This guide explains how Synthea creates fake patient records for Acute Myeloid Leukemia (AML). No prior experience required. We start with the basics and build up step by step.

---

## What Is Synthea?

Synthea is a program that **makes up realistic-looking medical records** for fictional patients. Doctors and researchers use these fake records to test software and study diseases — without ever needing real patient data.

You tell Synthea:
- How many patients to create
- What disease to focus on
- Where the patients "live" (state, city)

Synthea then produces output files — called **CSV files** — that look like real hospital records (diagnoses, lab results, medications, etc.).

---

## What Is AML?

**Acute Myeloid Leukemia (AML)** is a type of blood cancer. It affects the bone marrow, which is the spongy tissue inside your bones where blood cells are made. In AML, the bone marrow makes too many abnormal white blood cells.

AML is serious and is treated with chemotherapy. Some patients also receive a bone marrow transplant.

---

## Part 1 — How Synthea Knows What to Do: Modules

Synthea uses **modules** to decide what happens to each patient. A module is a JSON file that acts like a flowchart or a board game:

- The patient starts at a box called **`Initial`**
- The module moves the patient from box to box
- Each box ("state") does one thing — diagnose a disease, give a medication, record a lab test, etc.
- The patient eventually reaches the **`Terminal`** box and the module ends

Think of it like a Choose-Your-Own-Adventure book, but for medical histories.

---

## Part 2 — The Two AML Files (and Why They Are Different)

There are two JSON files related to AML:

| File | What it is | Runs as a module? |
|------|-----------|-------------------|
| `acute_myeloid_leukemia.json` | The real AML simulation — it moves patients through diagnosis, chemo, and complications | **Yes** |
| `aml_disease_model.json` | A reference document describing AML subtypes, genes, and treatments | **No** |

**`aml_disease_model.json` is like a textbook page.** It lists facts about AML (gene mutations, drug names, lab tests). It is NOT a simulation. It does not generate any CSV rows on its own.

**`acute_myeloid_leukemia.json` is the actual simulation.** This is the module that runs patients through a clinical story and produces output.

---

## Part 3 — How States Produce CSV Output

When a state in a module runs, it can write a row to a CSV file:

| State type | CSV file it writes to |
|------------|----------------------|
| `ConditionOnset` | `conditions.csv` |
| `Encounter` | `encounters.csv` |
| `Observation` | `observations.csv` |
| `Procedure` | `procedures.csv` |
| `MedicationOrder` | `medications.csv` |
| `SetAttribute` with `genomics_alterations` | `genomics.csv` |

If a module has no clinical states (just `Initial → Terminal`), it writes **nothing**. That is why `aml_disease_model.json` produces no output — it has no clinical states.

---

## Part 4 — How to Run the AML Simulation

### The easiest way — use the `--aml` flag

```bash
run_synthea --aml -p 100 Massachusetts Bedford --exporter.csv.export=true
```

This automatically runs the correct AML module (`acute_myeloid_leukemia`) for 100 patients.

### Using `-class aml` (same thing, different syntax)

```bash
run_synthea -class aml -p 100 Massachusetts Bedford --exporter.csv.export=true
```

### What each part means

| Argument | What it does |
|----------|-------------|
| `--aml` or `-class aml` | Tells Synthea to use the AML launcher |
| `-p 100` | Create 100 patients |
| `Massachusetts Bedford` | Set the location |
| `--exporter.csv.export=true` | Turn on CSV file output |
| `-s 42` | Set a random seed — same seed = same patients every time |
| `-a 5-21` | Only generate patients aged 5–21 |
| `-g M` or `-g F` | Only generate male or female patients |
| `--exporter.baseDirectory=./output` | Where to save the files |

### Specifying a module with `-m`

You can also pass a module name directly:

```bash
run_synthea -class aml -m aml_model -p 100 Massachusetts Bedford --exporter.csv.export=true
```

The names `aml_model` and `aml_disease_model` are both understood as shorthand for the real executable module `acute_myeloid_leukemia`. Synthea automatically maps them for you.

---

## Part 5 — What CSVs to Expect

After running, look in your output folder:

```
output/
  csv/
    patients.csv
    conditions.csv
    encounters.csv
    medications.csv
    procedures.csv
    observations.csv
    genomics.csv
```

### What each file contains

- **`patients.csv`** — One row per fake patient (age, gender, birthdate, etc.)
- **`conditions.csv`** — Diagnoses (e.g., AML was diagnosed on a certain date)
- **`encounters.csv`** — Hospital visits
- **`medications.csv`** — Drugs given (e.g., levofloxacin, chemotherapy)
- **`procedures.csv`** — Medical procedures (e.g., bone marrow biopsy)
- **`observations.csv`** — Lab results and measurements (e.g., neutrophil count)
- **`genomics.csv`** — Gene mutation data (only present if the module sets `genomics_alterations`)

---

## Part 6 — How to Add Genomics to a Module

**Genomics data** (gene mutations found in the cancer) goes into `genomics.csv`. To make this work, the module must set a special variable called `genomics_alterations` using a `SetAttribute` state.

### Example state

```json
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
    }
  ],
  "direct_transition": "Next_State"
}
```

### What each field means

| Field | Meaning |
|-------|---------|
| `method_genomics` | How the gene was tested (e.g., `"NGS"` = next-generation sequencing) |
| `source_genomics` | Where the sample came from (e.g., `"Bone marrow"`) |
| `alteration_type` | Type of mutation (e.g., `"SNV"` = single nucleotide variant) |
| `gene` | Which gene has the mutation (e.g., `"FLT3"`, `"NPM1"`) |
| `alteration_status` | Whether it is harmful (`"Pathogenic"`) |
| `hgvs_coding` | Scientific notation for the DNA change |
| `hgvs_protein` | Scientific notation for the protein change |
| `genome_version` | Which version of the human genome map was used |
| `vaf` | Variant allele frequency — roughly, what fraction of cells have this mutation |

**Important:** Place `Capture_Genomics` **after** the AML diagnosis state, not before. You can only report a gene mutation after the cancer is diagnosed.

If you leave out `date_genomics`, Synthea automatically picks a realistic date based on when the patient was diagnosed.

---

## Part 7 — A Complete Simple AML Module Example

Below is a minimal but working AML module. It:
1. Assigns a random AML subtype
2. Records the diagnosis
3. Opens a hospital visit
4. Runs a lab test
5. Gives a medication
6. Records genomic findings
7. Ends the visit

```json
{
  "name": "AML Simple Example",
  "gmf_version": 2,
  "states": {
    "Initial": {
      "type": "Initial",
      "direct_transition": "Assign_Subtype"
    },

    "Assign_Subtype": {
      "type": "Simple",
      "distributed_transition": [
        {"transition": "Set_FLT3",  "distribution": 0.45},
        {"transition": "Set_NPM1",  "distribution": 0.35},
        {"transition": "Set_APL",   "distribution": 0.20}
      ]
    },

    "Set_FLT3": {
      "type": "SetAttribute",
      "attribute": "aml_subtype",
      "value": "AML_FLT3_ITD",
      "direct_transition": "Diagnose_AML"
    },
    "Set_NPM1": {
      "type": "SetAttribute",
      "attribute": "aml_subtype",
      "value": "AML_NPM1",
      "direct_transition": "Diagnose_AML"
    },
    "Set_APL": {
      "type": "SetAttribute",
      "attribute": "aml_subtype",
      "value": "APL_PML_RARA",
      "direct_transition": "Diagnose_AML"
    },

    "Diagnose_AML": {
      "type": "ConditionOnset",
      "codes": [
        {
          "system": "SNOMED-CT",
          "code": "91861009",
          "display": "Acute myeloid leukemia (disorder)"
        }
      ],
      "direct_transition": "Hospital_Visit"
    },

    "Hospital_Visit": {
      "type": "Encounter",
      "encounter_class": "inpatient",
      "codes": [
        {
          "system": "SNOMED-CT",
          "code": "183807002",
          "display": "Inpatient stay"
        }
      ],
      "direct_transition": "Blast_Lab"
    },

    "Blast_Lab": {
      "type": "Observation",
      "category": "laboratory",
      "unit": "%",
      "codes": [
        {"system": "LOINC", "code": "709-6", "display": "Blasts/100 leukocytes in Blood"}
      ],
      "value": 38,
      "direct_transition": "Give_Chemo"
    },

    "Give_Chemo": {
      "type": "MedicationOrder",
      "codes": [
        {"system": "RxNorm", "code": "197361", "display": "cytarabine 100 MG Injection"}
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
        }
      ],
      "direct_transition": "End_Visit"
    },

    "End_Visit": {
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

## Part 8 — Checklist: Is My Module Working?

Run a small test first:

```bash
run_synthea -class aml -s 1 -p 10 Massachusetts Bedford --exporter.csv.export=true
```

Then check each file:

- [ ] `patients.csv` has 10 rows
- [ ] `conditions.csv` has rows with AML diagnosis codes
- [ ] `encounters.csv` has inpatient visit rows
- [ ] `medications.csv` has chemotherapy drug rows
- [ ] `observations.csv` has lab result rows
- [ ] `genomics.csv` has gene mutation rows
- [ ] Running again with `-s 1` gives the same results (reproducibility check)

---

## Part 9 — Common Mistakes and How to Fix Them

| Problem | Likely cause | Fix |
|---------|-------------|-----|
| No CSV output at all | CSV export is off | Add `--exporter.csv.export=true` |
| `genomics.csv` is empty | Module never sets `genomics_alterations` | Add a `SetAttribute` state for genomics |
| Wrong module running | Using a reference file instead of the simulation | Use `acute_myeloid_leukemia` or pass `-m aml_model` via the AML launcher |
| Same output every run | That's intentional with a fixed `-s` seed | Remove `-s` or change the number for variety |
| Module does nothing | `Initial → Terminal` with no clinical states | Add `ConditionOnset`, `Encounter`, and other states |

---

## Part 10 — Tips for Building Your Own AML Module

1. **Start small.** Begin with `Initial → Diagnose → Encounter → Terminal`. Add more states one at a time.
2. **One action per state.** Each state should do exactly one thing (one drug, one lab, one observation). This makes debugging easy.
3. **Put genomics after diagnosis.** Always place the `genomics_alterations` attribute after `ConditionOnset`.
4. **Use the seed `-s` when testing.** A fixed seed means you get the same patients every run, so you can compare outputs easily.
5. **Check the CSV files, not just the module.** The module might look right but still produce no output if a state type is wrong.
6. **Use attributes for branching.** Store the subtype in a variable (`aml_subtype`) and branch based on it using `complex_transition`.
