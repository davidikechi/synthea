# How Synthea Disease Modules Work
### A Plain-English Guide for Non-Technical Readers

---

## Introduction: What Is Synthea?

Imagine you want to study how a disease behaves across thousands of patients — what symptoms they develop, what tests doctors order, what medicines they receive, what complications arise. Recruiting thousands of real patients takes years, costs millions, and raises serious privacy concerns.

Synthea solves this by generating **synthetic (fake but realistic) patient records** using computer simulations. You tell it how many patients you want, and it simulates each one from birth through their medical history, producing the same kind of data files a real hospital would generate.

The heart of Synthea is its **module system** — a set of rules that describe how a disease unfolds inside a patient's body and medical journey. This guide explains exactly how those rules work, what they produce, and why.

---

## Part 1: The Big Picture — A Patient as a Story

Think of each patient as a character in a story. The disease module is the **plot script** — a set of scenes the character moves through in order. Some scenes are inevitable (every patient with this disease gets a blood test). Some are conditional (only patients with this particular variant get this particular drug). Some are random (55% of patients develop this complication, 45% do not).

The module defines all possible scenes and the rules for moving between them. Synthea reads those rules and plays out the story for each patient, recording every event as it happens.

---

## Part 2: States — The Building Blocks

Every module is made of **states**. A state is a single moment or event in a patient's journey. When a patient enters a state, something happens (or doesn't), and then they move to the next state.

Think of states like rooms in a building. A patient walks through one room at a time, and each room either does something to them or just points them to the next room.

There are several kinds of rooms (state types):

---

### 2.1 Initial — The Front Door

Every module has exactly one `Initial` state. It is the very first room every patient enters when the module starts running. Its only job is to point the patient toward the first real event.

**Real-world analogy:** A patient walks into a hospital. The receptionist checks them in and points them toward the right department. The receptionist's desk is the `Initial` state.

**What it writes to the CSVs:** Nothing by itself — it just routes the patient onward.

---

### 2.2 Terminal — The Exit

Every journey through a module must end somewhere. The `Terminal` state is the exit door. Once a patient reaches it, this module is done with them. They may continue aging and living in Synthea's broader simulation, but this disease module has finished.

**Real-world analogy:** The patient leaves the hospital after discharge. The module's story is over.

**What it writes to the CSVs:** Nothing — it is purely a signal that the module has finished.

---

### 2.3 Simple — The Corridor

A `Simple` state does nothing by itself. It has no medical meaning. Its only purpose is to act as a decision point or corridor — it looks at the patient's current situation and points them toward the right next room.

You use `Simple` states when you need to branch the path based on some condition (e.g. "if this patient has variant X, go left; otherwise go right").

**Real-world analogy:** A corridor with a sign that says "Turn left for oncology, turn right for cardiology." The corridor itself does nothing — it just routes you.

**What it writes to the CSVs:** Nothing.

---

### 2.4 Delay — The Waiting Room

A `Delay` state makes time pass. The patient sits and waits — for days, months, or years — before moving to the next state. This models the passage of time between events in a disease (e.g. a disease that takes years to develop after birth, or a recovery period between treatment and follow-up).

**Real-world analogy:** A waiting room. Nothing medical happens, but time passes before the patient is called in.

**What it writes to the CSVs:** Nothing directly, but it advances the patient's internal clock, so all subsequent events get a later timestamp.

---

### 2.5 SetAttribute — The Patient's Notes

A `SetAttribute` state writes a note in the patient's internal record. This note is not a medical event — it is a label or flag used by other states to make decisions later. For example, "this patient has subtype A" or "this patient's risk level is high."

These attributes are invisible to the output CSVs — they live only in memory while the simulation runs. Their purpose is to allow later states to ask "what kind of patient is this?" and branch accordingly.

**Real-world analogy:** A nurse writes "allergic to penicillin" on a sticky note and puts it in the patient's file. The note itself is not a medical event — it just informs future decisions.

**What it writes to the CSVs:** Nothing directly. However, it influences which states the patient visits next, and *those* states write to the CSVs.

---

### 2.6 ConditionOnset — The Diagnosis

This is where medicine begins. A `ConditionOnset` state records that the patient has developed a disease or condition. It writes a row to `conditions.csv` with the diagnosis name, the date it started, and a standardised medical code.

Every condition that appears in `conditions.csv` was created by a `ConditionOnset` state.

**Real-world analogy:** The doctor writes "Diagnosis: Type 2 Diabetes" in the patient's chart for the first time. That moment — when the condition is officially recorded — is a `ConditionOnset`.

**What it writes to the CSVs:**
- `conditions.csv` → one row: `START, STOP, PATIENT, ENCOUNTER, CODE, DESCRIPTION`

The `STOP` column is left blank until a matching `ConditionEnd` state later closes the condition.

---

### 2.7 ConditionEnd — The Resolution

When a condition is cured, resolved, or ends (either the patient recovers or, in some models, dies from it), a `ConditionEnd` state fills in the `STOP` date on the corresponding row in `conditions.csv`.

**Real-world analogy:** The doctor writes "Resolved: Type 2 Diabetes in remission" in the chart, stamped with today's date.

**What it writes to the CSVs:**
- `conditions.csv` → updates the `STOP` column on the matching condition row.

---

### 2.8 Encounter — The Hospital Visit

An `Encounter` state represents a patient arriving at a medical facility for care — an inpatient hospital stay, an outpatient clinic visit, or an emergency room visit. All observations, procedures, and medications that happen *inside* a visit must be linked to an open encounter.

**Real-world analogy:** The patient physically arrives at the hospital or clinic. A new entry is opened in the system: "Patient admitted, 9:00am, Monday."

**What it writes to the CSVs:**
- `encounters.csv` → one row: `START, STOP, PATIENT, CODE, DESCRIPTION, REASONCODE, COST`

---

### 2.9 EncounterEnd — Discharge

When the visit is over, an `EncounterEnd` state closes it, recording the discharge time and how the patient left (e.g. discharged home, transferred to another facility).

**Real-world analogy:** "Patient discharged, 3:00pm, Wednesday. Discharge to home."

**What it writes to the CSVs:**
- `encounters.csv` → updates the `STOP` column on the matching encounter row.

---

### 2.10 Observation — The Test Result

An `Observation` state records a measurement or test result taken during an encounter. This includes blood tests, vital signs, imaging results, and any other data point measured about the patient. Each observation has a value and a unit.

There are two kinds of values:
- **Numeric** — a number with a unit (e.g. white blood cell count: 12.3 × 10³/µL)
- **Coded** — a qualitative result expressed as a code (e.g. "Positive" or "Negative")

**Real-world analogy:** The nurse takes your blood pressure: 128/82 mmHg. The lab calls back with your cholesterol: 210 mg/dL. These are observations.

**What it writes to the CSVs:**
- `observations.csv` → one row: `DATE, PATIENT, ENCOUNTER, CATEGORY, CODE, DESCRIPTION, VALUE, UNITS, TYPE`

The `TYPE` column tells downstream tools how to interpret the value:
- `numeric` → the value is a number (maps to the OMOP **Measurement** table)
- `text` → the value is a word or code like "Positive" (maps to the OMOP **Observation** table)

---

### 2.11 Procedure — The Medical Action

A `Procedure` state records that a medical procedure was performed on the patient — a biopsy, a surgery, a chemotherapy infusion, a scan. It has a start time, a duration, and a reason (linked back to the condition that caused it).

**Real-world analogy:** The surgeon performs an appendectomy. The procedure is logged in the patient's record: what was done, when, how long it took, and why.

**What it writes to the CSVs:**
- `procedures.csv` → one row: `START, STOP, PATIENT, ENCOUNTER, CODE, DESCRIPTION, REASONCODE, COST`

---

### 2.12 MedicationOrder — The Prescription

A `MedicationOrder` state records that a medication was prescribed and started. It includes the drug name, the dose, how often it is taken, and for how long. Like conditions, medications stay "open" (active) until a matching `MedicationEnd` state closes them.

**Real-world analogy:** The doctor writes a prescription: "Metformin 500mg, twice daily, for 90 days." The pharmacist fills it. The medication is now active.

**What it writes to the CSVs:**
- `medications.csv` → one row: `START, STOP, PATIENT, ENCOUNTER, CODE, DESCRIPTION, REASONCODE, COST`

The `STOP` column is blank until `MedicationEnd` fills it in.

---

### 2.13 MedicationEnd — Stopping a Drug

A `MedicationEnd` state records that a medication was stopped — the course finished, the drug was discontinued, or the patient no longer needs it.

**Real-world analogy:** The antibiotic course is complete. The doctor marks the prescription as finished in the chart.

**What it writes to the CSVs:**
- `medications.csv` → updates the `STOP` column on the matching medication row.

---

## Part 3: Transitions — How Patients Move Between States

States would be useless without rules for how patients move between them. These rules are called **transitions**. There are three types.

---

### 3.1 Direct Transition — The Straight Path

The simplest transition. After this state, always go to that state. No conditions, no randomness — just a straight line.

```
Blood Test → Lab Result → Specialist Referral
```

**Real-world analogy:** After you check in at reception, you always go to the waiting room. No exceptions.

---

### 3.2 Distributed Transition — The Dice Roll

Some things in medicine are probabilistic. Not every patient gets the same complication. A `distributed_transition` assigns a percentage chance to each possible next state, and Synthea rolls the dice.

```
After chemotherapy:
  55% → develops Febrile Neutropenia
  10% → develops Tumour Lysis Syndrome
   5% → develops Blood Clotting Disorder
  30% → no complication (goes straight to follow-up)
```

**Real-world analogy:** Imagine a spinner divided into four sections by those percentages. Each patient spins it once and goes wherever the arrow points.

The percentages must always add up to exactly 100%.

---

### 3.3 Complex Transition — The Conditional Branch

The most powerful transition. It checks a condition about the patient first, then decides which state to go to based on the answer.

```
After genetic testing:
  If aml_subtype = "NPM1"  → go to NPM1 treatment
  If aml_subtype = "FLT3"  → go to FLT3 treatment
  If aml_subtype = "APL"   → go to APL treatment
  Otherwise               → go to standard treatment
```

You can combine conditions and probabilities in the same transition — for example, "if the patient is male, 40% chance of complication A and 60% chance of complication B."

**Real-world analogy:** A doctor reads your test results and says "because you have X, I'm going to treat you with Y rather than Z." The decision depends on what the test found.

---

## Part 4: The Output Files — What Gets Written Where

Every state that produces medical data writes to one or more CSV files. Here is what each file contains and which states fill it.

---

### patients.csv

**What it is:** The master list of all simulated patients — one row per patient.

**What's in it:** Patient ID, date of birth, date of death (if applicable), gender, race, address, and insurance information.

**What fills it:** Synthea itself, not any specific state. Every patient who is simulated gets a row here regardless of what happens to them.

**Real-world analogy:** The hospital's patient registry — a list of everyone who has ever been registered, even if they never came back.

---

### conditions.csv

**What it is:** Every diagnosis ever recorded for every patient.

**What's in it:** When the condition started, when it ended (blank if still active), which patient, which encounter it was diagnosed in, and the medical code + name of the condition.

**What fills it:** `ConditionOnset` states (add a row) and `ConditionEnd` states (fill in the stop date).

**Real-world analogy:** A patient's problem list — every diagnosis they have ever received, with dates.

**Key insight:** A patient can have multiple rows here — one per condition they developed. If a patient develops the main disease plus two complications, they get three rows in `conditions.csv`.

---

### encounters.csv

**What it is:** Every hospital or clinic visit.

**What's in it:** When the visit started, when it ended, which patient, what type of visit (inpatient, outpatient, emergency), the reason for the visit, and the cost.

**What fills it:** `Encounter` states (open a row) and `EncounterEnd` states (close it).

**Real-world analogy:** A billing record — a log of every time the patient came through the door.

---

### observations.csv

**What it is:** Every test result or measurement ever recorded.

**What's in it:** The date, which patient, which encounter, the category (laboratory test or vital sign), the medical code, the name of the test, the value, the unit, and the type (numeric or text).

**What fills it:** `Observation` states.

**Real-world analogy:** The results tab in an electronic health record — every blood test, every vital sign reading, every measurement ever taken on this patient.

**Key insight:** The `TYPE` column determines how downstream analysis tools interpret the value. `numeric` values (like a blood count of 4.5) go into measurement tables. `text` values (like "Positive") go into observation tables.

---

### procedures.csv

**What it is:** Every medical procedure performed.

**What's in it:** Start and stop time, which patient, which encounter, the procedure code and name, the reason it was done, and the cost.

**What fills it:** `Procedure` states.

**Real-world analogy:** The surgical log — every operation, biopsy, infusion, and scan performed on this patient.

---

### medications.csv

**What it is:** Every medication ever prescribed and administered.

**What's in it:** When the medication started, when it stopped (blank if still active), which patient, which encounter, the drug code and name, the reason it was prescribed, the dose instructions, and the cost.

**What fills it:** `MedicationOrder` states (open a row) and `MedicationEnd` states (close it).

**Real-world analogy:** The medication administration record — a full history of every drug the patient has ever taken, with start and stop dates.

**Key insight:** If a `MedicationEnd` state is missing for a drug, the drug will appear in `medications.csv` with a blank `STOP` date, as though the patient is still taking it forever. This is a common modelling mistake.

---

### genomics.csv

**What it is:** Genomic alteration data for patients — mutations, gene fusions, structural variants.

**What's in it:** Patient ID, date, the method used to detect the alteration (e.g. NGS, RT-PCR), the source tissue, the type of alteration (SNV, insertion, fusion), the gene affected, the specific change in DNA and protein notation, and the variant allele frequency.

**What fills it:** A special `SetAttribute` state that sets an attribute called `genomics_alterations` to a list of alteration objects. Synthea reads this attribute at export time and converts each object into a row.

**Real-world analogy:** A genetic pathology report — the results of sequencing the patient's tumour DNA, listing every mutation found.

**Key insight:** This file is unique because it is not filled by a standard state type. Instead, a `SetAttribute` state stores the genomic data as a structured list in memory, and a dedicated exporter reads that list and writes it out. One patient can produce multiple rows if they have multiple alterations (e.g. a patient with two mutations produces two rows).

---

## Part 5: Putting It All Together — A Patient's Journey

Let us walk through a hypothetical disease module from start to finish, tracing what gets written to each file at each step.

---

**Step 1 — Module starts**

The patient enters `Initial`. Nothing is written. They are routed straight to the next state.

*CSV writes: none*

---

**Step 2 — Time passes (Delay)**

The patient waits 40 years. This models the disease appearing in middle age. The simulation clock advances.

*CSV writes: none*

---

**Step 3 — Disease is assigned (SetAttribute)**

The module randomly assigns the patient to "Subtype A" using a distributed transition. This label is stored in memory.

*CSV writes: none*

---

**Step 4 — Diagnosis recorded (ConditionOnset)**

The patient is diagnosed with the disease. A row is written to `conditions.csv` with today's date in the `START` column and the `STOP` column left blank.

*CSV writes:* `conditions.csv` ← 1 new row

---

**Step 5 — Hospital admission (Encounter)**

The patient is admitted to hospital. A row is written to `encounters.csv`.

*CSV writes:* `encounters.csv` ← 1 new row

---

**Step 6 — Blood tests (Observation × 3)**

Three blood tests are run. Three rows are written to `observations.csv`, all with `TYPE = numeric`.

*CSV writes:* `observations.csv` ← 3 new rows

---

**Step 7 — Biopsy (Procedure)**

A biopsy is performed. One row is written to `procedures.csv`.

*CSV writes:* `procedures.csv` ← 1 new row

---

**Step 8 — Genomic mutation stored (SetAttribute)**

The biopsy result reveals a mutation. A `SetAttribute` state stores the mutation details in the patient's `genomics_alterations` attribute.

*CSV writes: none yet* (written at export time)

---

**Step 9 — Medication started (MedicationOrder)**

Treatment is prescribed. One row is written to `medications.csv` with the start date and a blank stop date.

*CSV writes:* `medications.csv` ← 1 new row

---

**Step 10 — Complication (distributed transition, 60% chance)**

The dice roll lands on "complication." A second `ConditionOnset` fires.

*CSV writes:* `conditions.csv` ← 1 new row (the complication)

---

**Step 11 — Medication ended (MedicationEnd)**

The treatment course finishes. The `STOP` date is filled in on the medication row.

*CSV writes:* `medications.csv` ← updates existing row

---

**Step 12 — Conditions ended (ConditionEnd × 2)**

Both the complication and the main disease are resolved. Both `STOP` dates are filled in.

*CSV writes:* `conditions.csv` ← updates 2 existing rows

---

**Step 13 — Discharge (EncounterEnd)**

The patient is discharged. The `STOP` date is filled in on the encounter row.

*CSV writes:* `encounters.csv` ← updates existing row

---

**Step 14 — Module ends (Terminal)**

The patient exits the module. At export time, Synthea reads the `genomics_alterations` attribute and writes it out.

*CSV writes:* `genomics.csv` ← 1 new row (the mutation from Step 8)

---

**Final tally for this one patient:**

| File | Rows written |
|---|---|
| `patients.csv` | 1 (always) |
| `conditions.csv` | 2 (disease + complication) |
| `encounters.csv` | 1 |
| `observations.csv` | 3 |
| `procedures.csv` | 1 |
| `medications.csv` | 1 |
| `genomics.csv` | 1 |

---

## Part 6: What Controls the Numbers?

When you run a module on many patients, three things determine how many rows end up in each file:

### 1. How many patients you simulate (`-p N`)
Every patient adds at least one row to `patients.csv` and one row to `conditions.csv` (the main diagnosis). Scale everything else proportionally.

### 2. The distribution percentages
Any `distributed_transition` or `complex_transition` splits patients into groups. If 20% of patients get Complication X, you get roughly 20% × N rows in `conditions.csv` for that complication. The subtype distribution works the same way — if 27% of patients are Subtype A, roughly 27% × N rows in `conditions.csv` will show Subtype A's diagnosis.

### 3. How many alterations, observations, or medications each subtype carries
A patient with 2 genomic mutations produces 2 rows in `genomics.csv`. A patient who receives 5 different medications produces 5 rows in `medications.csv`. If one subtype has more tests or drugs than another, patients in that subtype produce more rows. The total row count for any file is:

```
total rows = sum over all subtypes of (patients in subtype × rows per patient in that subtype)
```

---

## Part 7: Common Mistakes and What They Look Like

| Mistake | Symptom in CSVs |
|---|---|
| `reason` on a `MedicationOrder` uses the attribute name instead of the state name | `medications.csv` rows have no linked condition; medications appear unconnected |
| `ConditionEnd` references the attribute name instead of the state name | `conditions.csv` `STOP` dates never get filled — conditions appear never-ending |
| Missing `MedicationEnd` states | `medications.csv` `STOP` dates are blank — patient appears to be on all drugs forever |
| Subtype stored only as `SetAttribute`, no `ConditionOnset` | Subtype never appears in `conditions.csv` — invisible in the data |
| Population incidence gate left on when generating a disease cohort | Only 0.3% of patients develop the disease — 99.7% produce empty records |
| Genomic data stored but not as a list under `genomics_alterations` attribute | `genomics.csv` is empty or missing rows |

---

## Summary

A Synthea disease module is a directed flowchart of states. Each state represents one moment in a patient's medical story — a diagnosis, a test, a drug, a procedure, a complication. Patients move through states following transition rules that can be deterministic (always go here next), probabilistic (X% chance of going here), or conditional (go here if the patient has attribute Y).

Every medically meaningful state writes to one CSV file:

- `ConditionOnset` / `ConditionEnd` → `conditions.csv`
- `Encounter` / `EncounterEnd` → `encounters.csv`
- `Observation` → `observations.csv`
- `Procedure` → `procedures.csv`
- `MedicationOrder` / `MedicationEnd` → `medications.csv`
- `SetAttribute` (genomics list) → `genomics.csv`

The number of rows in each file is determined by how many patients you simulate, what percentage of them reach each state, and how many events each state produces. Everything else — the disease name, the drug names, the test codes — is configuration that you supply when writing the module.

---

*Written as a general guide to the Synthea Generic Module Framework. No medical domain knowledge is required to understand these mechanics.*
