# Structured summary eval v2 — Gemma 4 E2B, 37 highlights, model time 0s

## Detected headings

- p3: L1 1 · β-Lactams, Vancomycin & Metronidazol ; L1 1.1 Classification ; L1 1.2 Mechanism of action ; L2 شرح
- p4: L1 1.3 Resistance & β-lactamase inhibitors ; L1 1.4 Penicillins — Pharmacokinetics ; L1 1.5 Natural penicillins ; L2 Penicillin G (Benzylpenicillin) ; L2 Clinical uses of Pen G
- p5: L1 Penicillin V (Phenoxymethylpenicillin) ; L2 Prophylactic uses of penicillins ; L1 1.6 Semisynthetic penicillins
- p9: L2 أهم ما يُسأل عنه في هذا الجزء ; L2 (+) Mnemonic — Anti-staph penicillins: "
- p10: L1 2 · Aminoglycoside Antibiotics ; L1 2.1 Introduction ; L1 Chemistry ; L1 2.2 Classification ; L1 2.3 Mechanism of action ; L2 شرح ; L1 2.4 Post-antibiotic effect & dosing

## Result

```
# 1.2 Mechanism of action
- All β-lactams interfere with bacterial cell-wall synthesis. The wall = peptidoglycan strands of alternating NAG (N-acetyl-glucosamine) and NAM (N-acetyl-muramic acid); NAM carries short peptide chains that are cross-linked — the last step, done by the enzyme transpeptidase.
- Penicillins (and cephalosporins) bind to and inhibit transpeptidase → no cross-linking → no wall formation. PBPs (Penicillin-Binding Proteins) = bacterial membrane enzymes involved in wall synthesis; penicillins inactivate them → morphological changes or lysis.
- Bactericidal only if active wall synthesis occurs — **active against growing, not resting cells**.

## شرح
- جدار البكتيريا مبني من سالسل peptidoglycan متشابكة بإنزيم transpeptidase (هو نفسه الـ PBP). البنسلين يعطّل هذا الإنزيم فيضعف الجدار وتنفجر البكتيريا. لذلك يقتل فقط البكتيريا النشطة النمو، ولا يُعطى مع مضاد bacteriostatic (ألنه يوقف النمو فيبطل مفعول البنسلين).

# 1.3 Resistance & β-lactamase inhibitors
Mechanism | Detail
1. Enzymatic cleavage | β-lactamases (penicillinases, cephalosporinases, carbapenemases) hydrolyze the amide bond of the β-lactam ring
2. Target (PBP) alteration | Low-affinity PBPs: mecA gene → PBP2a in MRSA; mutated PBP in S. pneumoniae
3. Porin-channel loss | Down-regulation/mutation of outer-membrane porins in Gram-negatives (e.g. OprD loss in Pseudomonas)
4. Active efflux pumps | Over-expressed multidrug efflux pumps pump drug out of the periplasmic space

β-lactamase inhibitor | Combined with | Brand
Clavulanic acid | Amoxicillin (co-amoxiclav) or Ticarcillin | Augmentin
Sulbactam | Ampicillin | Unasyn
Tazobactam | Piperacillin | Zosyn

# 1.4 Penicillins — Pharmacokinetics
- Absorption (oral): varies with acid stability and food; some poorly absorbed because of acid lability — Pen G. Food delays/decreases absorption of some (e.g. ampicillin) → give ≥1 h before or 2 h after meals.
- Intrathecal injection is **NOT recommended**, especially Pen G — may cause convulsions.
- Distribution: widely distributed but do not enter mammalian cells; do not cross BBB unless meninges are inflamed (then therapeutic levels reach CSF).
- Excretion: mostly rapid renal excretion, mainly by tubular secretion; partly in bile.

# 1.5 Natural penicillins
## Penicillin G (Benzylpenicillin)
- 60–70 % destroyed by gastric acid → given IV or IM.
- t1⁄2 ≈ 30 min after IM (main clinical problem) → prolonged by slow-release forms: Procaine penicillin G and Benzathine penicillin.

## Clinical uses of Pen G
Infection | Notes
Gonococcal | Resistance ↑ → Ceftriaxone 250 mg IM single dose (DOC), or Procaine Pen G 4.8 million units + Probenecid 1 g orally
Syphilis | Pen G or Procaine Pen G
Diphtheria | Specific antitoxin is the only effective treatment; Pen G eliminates the carrier state
Anthrax | Pen G = **drug of choice**
Rat-bite fever | Pen G = DOC

# Penicillin V (Phenoxymethylpenicillin)
- Acid-stable → oral. Spectrum ≈ Pen G, but Pen G is more active against Gram-negatives.
- Oral Pen V only for mild infections: respiratory infections in children, pharyngitis, otitis media, sinusitis.

## Prophylactic uses of penicillins
- Streptococcal disease in closed populations (schools, military); deep burns (Pen G/V).
- Recurrences of rheumatic fever (Pen V or G).
- Sexual contacts of gonorrhea/syphilis patients.

## أهم ما يُسأل عنه في هذا الجزء
- Imipenem + cilastatin (ألن dehydropeptidase في الكلية يدّمره) — والـseizures مع imipenem خصوًصا في الفشل الكلوي.
- Aztreonam: Gram-negative aerobes فقط، وآمن في حساسية البنسلين.
- Vancomycin: يُعطى IV لـ MRSA، وفمويًا فقط لـ C. difficile؛ وRed man syndrome سببه سرعة التسريب (ليست حساسية حقيقية).

# 2.1 Introduction
- Streptomycin — 1944, from the actinomycete Streptomyces griseus (first aminoglycoside).
- Bactericidal; interfere with protein synthesis; used for aerobic Gram –ve bacteria.
- Relatively low margin of safety → ototoxicity and nephrotoxicity.

# 2.3 Mechanism of action
- Result: BACTERICIDAL, concentration-dependent killing
- O2-dependent uptake → NOT active against anaerobes

# 2.4 Post-antibiotic effect & dosing
- Aminoglycosides show concentration-dependent killing and a significant post-antibiotic effect (PAE).
- Single daily dosing is at least as effective as, and no more toxic than, multiple dosing.
```

## Log

  dropped check → fallback (terms)
- [1.2 Mechanism of action › None] model 0.0s, fallback=True, new numbers=[], unknown=[]
  dropped check → fallback (negation)
- [1.2 Mechanism of action › شرح] model 0.0s, fallback=True, new numbers=[], unknown=[]
- [1.3 Resistance & β-lactamase inhibitors › None] table by code (4 rows, header=yes)
- [1.3 Resistance & β-lactamase inhibitors › None] table by code (3 rows, header=yes)
  dropped check → fallback (number)
- [1.4 Penicillins — Pharmacokinetics › None] model 0.0s, fallback=True, new numbers=[], unknown=[]
- [1.5 Natural penicillins › Penicillin G (Benzylpenicillin)] small part by code
- [1.5 Natural penicillins › Clinical uses of Pen G] table by code (5 rows, header=yes)
- [Penicillin V (Phenoxymethylpenicillin) › None] small part by code
  dropped check → fallback (terms)
- [Penicillin V (Phenoxymethylpenicillin) › Prophylactic uses of penicillins] model 0.0s, fallback=True, new numbers=[], unknown=[]
  dropped check → fallback (terms)
- [None › أهم ما يُسأل عنه في هذا الجزء] model 0.0s, fallback=True, new numbers=[], unknown=[]
  dropped check → fallback (number)
- [2.1 Introduction › None] model 0.0s, fallback=True, new numbers=[], unknown=[]
- [2.3 Mechanism of action › None] small part by code
- [2.4 Post-antibiotic effect & dosing › None] small part by code

## Comparison tables

### # Penicillin V (Phenoxymethylpenicillin) — 0s — REJECTED (terms)
```

```

### # 1.4 Penicillins — Pharmacokinetics — 0s — REJECTED (number)
```

```

### # 2.1 Introduction — 0s — REJECTED (number)
```

```

## Mind map tree

```
Antibiotics
  ● 1.2 Mechanism of action
        · All β-lactams interfere with bacterial cell-wall synthesis. The wall =
        · Penicillins (and cephalosporins) bind to and inhibit transpeptidase → 
        · Bactericidal only if active wall synthesis occurs — **active against g
    ├─ شرح
        · جدار البكتيريا مبني من سالسل peptidoglycan متشابكة بإنزيم transpeptida
  ● 1.3 Resistance & β-lactamase inhibitors
        ▦ Mechanism
        ▦ 1. Enzymatic cleavage
        ▦ 2. Target (PBP) alteration
        ▦ 3. Porin-channel loss
        ▦ 4. Active efflux pumps
        ▦ β-lactamase inhibitor
        ▦ Clavulanic acid
        ▦ Sulbactam
        ▦ Tazobactam
  ● 1.4 Penicillins — Pharmacokinetics
        · Absorption (oral): varies with acid stability and food; some poorly ab
        · Intrathecal injection is **NOT recommended**, especially Pen G — may c
        · Distribution: widely distributed but do not enter mammalian cells; do 
        · Excretion: mostly rapid renal excretion, mainly by tubular secretion; 
  ● 1.5 Natural penicillins
    ├─ Penicillin G (Benzylpenicillin)
        · 60–70 % destroyed by gastric acid → given IV or IM.
        · t1⁄2 ≈ 30 min after IM (main clinical problem) → prolonged by slow-rel
    ├─ Clinical uses of Pen G
        ▦ Infection
        ▦ Gonococcal
        ▦ Syphilis
        ▦ Diphtheria
        ▦ Anthrax
        ▦ Rat-bite fever
  ● Penicillin V (Phenoxymethylpenicillin)
        · Acid-stable → oral. Spectrum ≈ Pen G, but Pen G is more active against
        · Oral Pen V only for mild infections: respiratory infections in childre
    ├─ Prophylactic uses of penicillins
        · Streptococcal disease in closed populations (schools, military); deep 
        · Recurrences of rheumatic fever (Pen V or G).
        · Sexual contacts of gonorrhea/syphilis patients.
    ├─ أهم ما يُسأل عنه في هذا الجزء
        · Imipenem + cilastatin (ألن dehydropeptidase في الكلية يدّمره) — والـse
        · Aztreonam: Gram-negative aerobes فقط، وآمن في حساسية البنسلين.
        · Vancomycin: يُعطى IV لـ MRSA، وفمويًا فقط لـ C. difficile؛ وRed man sy
  ● 2.1 Introduction
        · Streptomycin — 1944, from the actinomycete Streptomyces griseus (first
        · Bactericidal; interfere with protein synthesis; used for aerobic Gram 
        · Relatively low margin of safety → ototoxicity and nephrotoxicity.
  ● 2.3 Mechanism of action
        · Result: BACTERICIDAL, concentration-dependent killing
        · O2-dependent uptake → NOT active against anaerobes
  ● 2.4 Post-antibiotic effect & dosing
        · Aminoglycosides show concentration-dependent killing and a significant
        · Single daily dosing is at least as effective as, and no more toxic tha
```
