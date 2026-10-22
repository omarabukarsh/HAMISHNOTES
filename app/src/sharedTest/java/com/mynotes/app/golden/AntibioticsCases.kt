package com.mynotes.app.golden

/**
 * حالات مرجعية من ملف antibiotics.pdf (المرفق من صاحب المشروع).
 * كل حالة: خطوط هايلايتر بمواضع ثابتة على الصفحة، والنص الصحيح المتوقع كما يظهر في الملف
 * (كُتب من الصفحة نفسها، وتُحقق كلماته العربية مع pdftotext/poppler)، لا من ناتج البرنامج.
 *
 * markTexts   نص كل تمييز بعد الدمج بصيغة التطبيق: فقرات بسطر فارغ، "# " عنوان، " | " بين الخلايا
 * summary     الملخص في وضع «مرتّب» كما يعرضه درج الملخصات ويصدّره (Pipeline.render)
 * critical    مقاطع يجب أن تبقى حرفيًا في الملخص (أرقام، جرعات، وحدات، مصطلحات)
 *
 * الصفحات بأرقام من 0 (الصفحة 5 في الملف = 4).
 */
data class GoldenCase(
    val id: String,
    val about: String,
    val strokes: List<Stroke>,
    val markTexts: List<String>,
    val summary: List<String>,
    val critical: List<String> = emptyList()
)

private fun line(page: Int, y: Float, x0: Float, x1: Float, color: androidx.compose.ui.graphics.Color = Pipeline.YELLOW) =
    Stroke(page, stroke(y, x0 - 0.004f, x1 + 0.004f), color)

object AntibioticsCases {

    /** الصفحة 5: عنوان مرقّم ثم أربع نقاط (فقرات) فيها جرعات وأوقات. */
    val headingAndBullets = GoldenCase(
        id = "p5-heading-bullets",
        about = "عنوان 1.4 لا يندمج مع الفقرات، وكل نقطة فقرة مستقلة، والأرقام كما هي",
        strokes = listOf(
            line(4, 0.4946f, 0.058f, 0.471f),
            line(4, 0.5302f, 0.079f, 0.941f),
            line(4, 0.5528f, 0.079f, 0.912f),
            line(4, 0.5755f, 0.079f, 0.250f),
            line(4, 0.5994f, 0.079f, 0.848f),
            line(4, 0.6246f, 0.079f, 0.932f),
            line(4, 0.6460f, 0.079f, 0.476f),
            line(4, 0.6711f, 0.079f, 0.756f)
        ),
        markTexts = listOf(
            "# 1.4 Penicillins — Pharmacokinetics\n\n" +
                "Absorption (oral): varies with acid stability and food; some poorly absorbed because of acid lability — Pen\n" +
                "G. Food delays/decreases absorption of some (e.g. ampicillin) → give ≥1 h before or 2 h after meals.\n" +
                "Parenteral: IM, IV, SC.\n\n" +
                "Intrathecal injection is NOT recommended, especially Pen G — may cause convulsions.\n\n" +
                "Distribution: widely distributed but do not enter mammalian cells; do not cross BBB unless meninges\n" +
                "are inflamed (then therapeutic levels reach CSF).\n\n" +
                "Excretion: mostly rapid renal excretion, mainly by tubular secretion; partly in bile."
        ),
        summary = listOf(
            "H: 1.4 Penicillins — Pharmacokinetics",
            "P: Absorption (oral): varies with acid stability and food; some poorly absorbed because of acid lability — Pen G. " +
                "Food delays/decreases absorption of some (e.g. ampicillin) → give ≥1 h before or 2 h after meals. Parenteral: IM, IV, SC.",
            "P: Intrathecal injection is NOT recommended, especially Pen G — may cause convulsions.",
            "P: Distribution: widely distributed but do not enter mammalian cells; do not cross BBB unless meninges are inflamed " +
                "(then therapeutic levels reach CSF).",
            "P: Excretion: mostly rapid renal excretion, mainly by tubular secretion; partly in bile."
        ),
        critical = listOf("≥1 h before or 2 h after meals", "IM, IV, SC")
    )

    /** الصفحة 5: جدولان متتاليان، الأول بخلايا من سطرين، والثاني بثلاثة أعمدة. */
    val twoTables = GoldenCase(
        id = "p5-two-tables",
        about = "جدولان متتاليان لا يندمجان، وخلية من سطرين تبقى في صفها وعمودها",
        strokes = listOf(
            line(4, 0.1253f, 0.067f, 0.927f),
            line(4, 0.1568f, 0.067f, 0.927f),
            line(4, 0.1769f, 0.332f, 0.569f),
            line(4, 0.2084f, 0.067f, 0.840f),
            line(4, 0.2285f, 0.332f, 0.423f),
            line(4, 0.2600f, 0.067f, 0.898f),
            line(4, 0.2801f, 0.332f, 0.557f),
            line(4, 0.3116f, 0.067f, 0.919f),
            line(4, 0.3556f, 0.067f, 0.927f),
            line(4, 0.3871f, 0.067f, 0.886f),
            line(4, 0.4173f, 0.067f, 0.861f),
            line(4, 0.4475f, 0.067f, 0.851f)
        ),
        markTexts = listOf(
            "Mechanism | Detail\n" +
                "1. Enzymatic cleavage | β-lactamases (penicillinases, cephalosporinases, carbapenemases) hydrolyze the amide bond of the β-lactam ring\n" +
                "2. Target (PBP) alteration | Low-affinity PBPs: mecA gene → PBP2a in MRSA; mutated PBP in S. pneumoniae\n" +
                "3. Porin-channel loss | Down-regulation/mutation of outer-membrane porins in Gram-negatives (e.g. OprD loss in Pseudomonas)\n" +
                "4. Active efflux pumps | Over-expressed multidrug efflux pumps pump drug out of the periplasmic space\n\n" +
                "β-lactamase inhibitor | Combined with | Brand\n" +
                "Clavulanic acid | Amoxicillin (co-amoxiclav) or Ticarcillin | Augmentin®\n" +
                "Sulbactam | Ampicillin | Unasyn®\n" +
                "Tazobactam | Piperacillin | Zosyn®"
        ),
        summary = listOf(
            "T: Mechanism | Detail || " +
                "1. Enzymatic cleavage | β-lactamases (penicillinases, cephalosporinases, carbapenemases) hydrolyze the amide bond of the β-lactam ring || " +
                "2. Target (PBP) alteration | Low-affinity PBPs: mecA gene → PBP2a in MRSA; mutated PBP in S. pneumoniae || " +
                "3. Porin-channel loss | Down-regulation/mutation of outer-membrane porins in Gram-negatives (e.g. OprD loss in Pseudomonas) || " +
                "4. Active efflux pumps | Over-expressed multidrug efflux pumps pump drug out of the periplasmic space",
            "T: β-lactamase inhibitor | Combined with | Brand || " +
                "Clavulanic acid | Amoxicillin (co-amoxiclav) or Ticarcillin | Augmentin® || " +
                "Sulbactam | Ampicillin | Unasyn® || " +
                "Tazobactam | Piperacillin | Zosyn®"
        ),
        critical = listOf("PBP2a in MRSA", "Augmentin®", "Zosyn®")
    )

    /** الصفحة 6: صفوف جدول فيها جرعات، وخلية تمتد على سطرين. */
    val doseTable = GoldenCase(
        id = "p6-dose-table",
        about = "الجرعات والوحدات في خلايا الجدول تبقى صحيحة، وتكملة الخلية لا تصير صفًا جديدًا",
        strokes = listOf(
            line(5, 0.1165f, 0.067f, 0.883f),
            line(5, 0.1467f, 0.067f, 0.919f),
            line(5, 0.1782f, 0.067f, 0.380f),
            line(5, 0.2084f, 0.067f, 0.923f),
            line(5, 0.2298f, 0.306f, 0.585f),
            line(5, 0.2600f, 0.067f, 0.482f)
        ),
        markTexts = listOf(
            "Streptococcal | Pharyngitis, tonsillitis, otitis media, scarlet fever, rheumatic fever, endocarditis\n" +
                "Pneumococcal | Pneumonia, meningitis, arthritis, osteomyelitis, endocarditis, pericarditis, peritonitis\n" +
                "Meningococcal | Meningitis\n" +
                "Gonococcal | Resistance ↑ → Ceftriaxone 250 mg IM single dose (DOC), or Procaine Pen G 4.8 million units + Probenecid 1 g orally\n" +
                "Syphilis | Pen G or Procaine Pen G"
        ),
        summary = listOf(
            "T: Streptococcal | Pharyngitis, tonsillitis, otitis media, scarlet fever, rheumatic fever, endocarditis || " +
                "Pneumococcal | Pneumonia, meningitis, arthritis, osteomyelitis, endocarditis, pericarditis, peritonitis || " +
                "Meningococcal | Meningitis || " +
                "Gonococcal | Resistance ↑ → Ceftriaxone 250 mg IM single dose (DOC), or Procaine Pen G 4.8 million units + Probenecid 1 g orally || " +
                "Syphilis | Pen G or Procaine Pen G"
        ),
        critical = listOf("Ceftriaxone 250 mg IM single dose", "4.8 million units", "Probenecid 1 g orally")
    )

    /** الصفحة 6: صف واحد من الجدول فيه جرعة. */
    val singleRow = GoldenCase(
        id = "p6-single-row",
        about = "تظليل صف واحد من جدول يبقي حد الخليتين ولا يدمجهما في جملة",
        strokes = listOf(line(5, 0.4349f, 0.067f, 0.594f)),
        markTexts = listOf("Gingivostomatitis | Low-dose Pen V (500 mg q6h × 7 days)"),
        summary = listOf("T: Gingivostomatitis | Low-dose Pen V (500 mg q6h × 7 days)"),
        critical = listOf("500 mg q6h × 7 days")
    )

    /** الصفحة 6: عنوان فرعي بنفس حجم النص (عريض) ثم نقطتان. */
    val subheading = GoldenCase(
        id = "p6-subheading-bullets",
        about = "العنوان الفرعي بنفس حجم الخط لا يندمج مع النقطة التالية، والنقطتان لا تندمجان",
        strokes = listOf(
            line(5, 0.6749f, 0.058f, 0.339f),
            line(5, 0.7001f, 0.079f, 0.753f),
            line(5, 0.7253f, 0.079f, 0.449f)
        ),
        markTexts = listOf(
            "Prophylactic uses of penicillins\n\n" +
                "Streptococcal disease in closed populations (schools, military); deep burns (Pen G/V).\n\n" +
                "Recurrences of rheumatic fever (Pen V or G)."
        ),
        summary = listOf(
            "P: Prophylactic uses of penicillins",
            "P: Streptococcal disease in closed populations (schools, military); deep burns (Pen G/V).",
            "P: Recurrences of rheumatic fever (Pen V or G)."
        )
    )

    /** الصفحة 5: نقاط فيها نسب وأرقام ورمز t½. */
    val numbers = GoldenCase(
        id = "p5-numbers-symbols",
        about = "النسب والرموز (60–70 %، t½، ≈ 30 min) لا تتغير، ولا تُكبَّر t في t½",
        strokes = listOf(
            line(4, 0.7794f, 0.079f, 0.796f),
            line(4, 0.8033f, 0.079f, 0.520f),
            line(4, 0.8285f, 0.079f, 0.912f),
            line(4, 0.8498f, 0.079f, 0.305f)
        ),
        markTexts = listOf(
            "First penicillin isolated; used as stable Na or K salts (crystalline penicillin); expressed in IU.\n\n" +
                "60–70 % destroyed by gastric acid → given IV or IM.\n\n" +
                "t½ ≈ 30 min after IM (main clinical problem) → prolonged by slow-release forms: Procaine penicillin G\n" +
                "and Benzathine penicillin."
        ),
        summary = listOf(
            "P: First penicillin isolated; used as stable Na or K salts (crystalline penicillin); expressed in IU.",
            "P: 60–70 % destroyed by gastric acid → given IV or IM.",
            "P: t½ ≈ 30 min after IM (main clinical problem) → prolonged by slow-release forms: Procaine penicillin G and Benzathine penicillin."
        ),
        critical = listOf("60–70 %", "t½ ≈ 30 min")
    )

    /** الصفحة 30: جدول بخلايا من عدة أسطر في العمودين، وكلمة مقسومة بشرطة داخل خلية. */
    val wrappedCells = GoldenCase(
        id = "p30-wrapped-cells",
        about = "خلايا من عدة أسطر في أي عمود تبقى في صفها، والكلمة المقسومة penicillin-allergic تُجمع",
        strokes = listOf(
            line(29, 0.1253f, 0.067f, 0.716f),
            line(29, 0.1568f, 0.067f, 0.915f),
            line(29, 0.1769f, 0.456f, 0.758f),
            line(29, 0.2084f, 0.067f, 0.770f),
            line(29, 0.2386f, 0.067f, 0.872f),
            line(29, 0.2700f, 0.067f, 0.597f),
            line(29, 0.3003f, 0.067f, 0.603f),
            line(29, 0.3317f, 0.067f, 0.604f),
            line(29, 0.3519f, 0.067f, 0.126f),
            line(29, 0.3833f, 0.067f, 0.566f),
            line(29, 0.4035f, 0.067f, 0.120f)
        ),
        markTexts = listOf(
            "Infection / situation | Drug (as stated in the lectures)\n" +
                "Diphtheria & whooping cough | Erythromycin (DOC). Pen G only eliminates diphtheria carrier state; antitoxin = the effective treatment\n" +
                "Gonorrhea | Ceftriaxone 250 mg IM single dose (DOC)\n" +
                "Anthrax — treatment / prophylaxis | Penicillin G (DOC) / Doxycycline (DOC for prophylaxis)\n" +
                "Rat-bite fever | Penicillin G (DOC)\n" +
                "Lyme disease (Borrelia) | Doxycycline (DOC)\n" +
                "Rickettsial (Rocky Mountain spotted fever, typhus, Q fever) | Tetracycline (DOC)\n" +
                "Chlamydia in pregnancy · syphilis in penicillin-allergic | Erythromycin"
        ),
        summary = listOf(
            "T: Infection / situation | Drug (as stated in the lectures) || " +
                "Diphtheria & whooping cough | Erythromycin (DOC). Pen G only eliminates diphtheria carrier state; antitoxin = the effective treatment || " +
                "Gonorrhea | Ceftriaxone 250 mg IM single dose (DOC) || " +
                "Anthrax — treatment / prophylaxis | Penicillin G (DOC) / Doxycycline (DOC for prophylaxis) || " +
                "Rat-bite fever | Penicillin G (DOC) || " +
                "Lyme disease (Borrelia) | Doxycycline (DOC) || " +
                "Rickettsial (Rocky Mountain spotted fever, typhus, Q fever) | Tetracycline (DOC) || " +
                "Chlamydia in pregnancy · syphilis in penicillin-allergic | Erythromycin"
        ),
        critical = listOf("Ceftriaxone 250 mg IM single dose (DOC)", "penicillin-allergic")
    )

    /** الصفحة 30: صف يمتد عموداه على سطرين. */
    val wrappedBothColumns = GoldenCase(
        id = "p30-wrapped-both-columns",
        about = "صف تلتف خليتاه معًا على سطرين يبقى صفًا واحدًا",
        strokes = listOf(
            line(29, 0.7118f, 0.067f, 0.710f),
            line(29, 0.7319f, 0.067f, 0.092f),
            line(29, 0.7634f, 0.067f, 0.813f),
            line(29, 0.7835f, 0.067f, 0.574f)
        ),
        markTexts = listOf(
            "Penicillin/cephalosporin/carbapenem-allergic Gram –ve | Aztreonam (little cross-reactivity)\n" +
                "Burn-wound infection prevention · trachoma/conjunctivitis | Silver sulfadiazine (no longer recommended) · Sulfacetamide"
        ),
        summary = listOf(
            "T: Penicillin/cephalosporin/carbapenem-allergic Gram –ve | Aztreonam (little cross-reactivity) || " +
                "Burn-wound infection prevention · trachoma/conjunctivitis | Silver sulfadiazine (no longer recommended) · Sulfacetamide"
        )
    )

    /** الصفحة 3: صندوق «شرح» عربي فيه مصطلحات إنجليزية وأرقام. */
    val arabicBox = GoldenCase(
        id = "p3-arabic-box",
        about = "نص عربي من اليمين لليسار مع مصطلحات إنجليزية: ترتيب الكلمات، و«لا» و«الأ»، والأرقام 70S/30S/50S",
        strokes = listOf(
            line(2, 0.5277f, 0.888f, 0.925f),
            line(2, 0.5579f, 0.071f, 0.925f),
            line(2, 0.5856f, 0.168f, 0.925f),
            line(2, 0.6132f, 0.460f, 0.925f)
        ),
        markTexts = listOf(
            "شرح\n\n" +
                "الريبوسوم البكتيري 70S يتكون من وحدتين: 30S (تستقبل mRNA وتبدأ التخليق) و50S (تجري ربط الأحماض الأمينية peptidyl\n" +
                "transferase والانتقال translocation). Aminoglycosides و Tetracyclines → 30S | Macrolides,\n" +
                "Lincosamides, Chloramphenicol, Fusidic acid → 50S."
        ),
        summary = listOf(
            "P: شرح",
            "P: الريبوسوم البكتيري 70S يتكون من وحدتين: 30S (تستقبل mRNA وتبدأ التخليق) و50S (تجري ربط الأحماض الأمينية peptidyl " +
                "transferase والانتقال translocation). Aminoglycosides و Tetracyclines → 30S | Macrolides, " +
                "Lincosamides, Chloramphenicol, Fusidic acid → 50S."
        ),
        critical = listOf("الأحماض الأمينية", "والانتقال", "70S", "و50S", "peptidyl transferase")
    )

    /** الصفحة 3: سطر عنوان فرعي إنجليزي ثم عربي يفصلهما «|». */
    val mixedSubtitle = GoldenCase(
        id = "p3-mixed-subtitle",
        about = "سطر يبدأ بالإنجليزية وينتهي بالعربية: كل كلمة في مكانها، و«|» في النص ليس جدولًا",
        strokes = listOf(line(2, 0.1454f, 0.058f, 0.450f)),
        markTexts = listOf("Where each antibiotic group acts \\| مكان عمل كل مجموعة"),
        summary = listOf("P: Where each antibiotic group acts | مكان عمل كل مجموعة")
    )

    /** الصفحة 2: صندوق «كيف تذاكر» عربي بالكامل مع رقم ومصطلح إنجليزي. */
    val arabicBullets = GoldenCase(
        id = "p2-arabic-bullets",
        about = "فقرات عربية: «بالأسئلة» و«بعلامة» صحيحتان، والرقم 6 والنقطة في مكانهما",
        strokes = listOf(
            line(1, 0.4584f, 0.719f, 0.925f),
            line(1, 0.4886f, 0.512f, 0.902f),
            line(1, 0.5188f, 0.077f, 0.902f),
            line(1, 0.5478f, 0.618f, 0.902f),
            line(1, 0.5780f, 0.235f, 0.902f)
        ),
        markTexts = listOf(
            "كيف تذاكر من هذا الملف؟\n\n" +
                "اقرأ كل جزء ثم اختبر نفسك بالأسئلة السريعة في الجزء 6.\n\n" +
                "الصناديق الحمراء = نقاط يتكرر السؤال عنها. الصناديق الخضراء = طرق حفظ (Mnemonics). الصناديق الزرقاء = تنبيه\n" +
                "على أخطاء/غموض في شرائح المحاضرات.\n\n" +
                "كل المعلومات مأخوذة من المحاضرات الخمس المرفوعة؛ أي إضافة من خارجها موسومة بعلامة (+)."
        ),
        summary = listOf(
            "P: كيف تذاكر من هذا الملف؟",
            "P: اقرأ كل جزء ثم اختبر نفسك بالأسئلة السريعة في الجزء 6.",
            "P: الصناديق الحمراء = نقاط يتكرر السؤال عنها. الصناديق الخضراء = طرق حفظ (Mnemonics). الصناديق الزرقاء = تنبيه " +
                "على أخطاء/غموض في شرائح المحاضرات.",
            "P: كل المعلومات مأخوذة من المحاضرات الخمس المرفوعة؛ أي إضافة من خارجها موسومة بعلامة (+)."
        ),
        critical = listOf("بالأسئلة", "بعلامة", "(Mnemonics)")
    )

    /** الصفحة 10: نقاط عربية بتشكيل (ّ ً ُ) ومصطلحات إنجليزية ملتصقة بحروف عربية. */
    val arabicDiacritics = GoldenCase(
        id = "p10-arabic-diacritics",
        about = "التشكيل يبقى على حرفه (يُسأل، يدمّره، خصوصًا)، و«لأن» صحيحة، والـseizures ملتصقة كما في الملف",
        strokes = listOf(
            line(9, 0.0897f, 0.682f, 0.925f),
            line(9, 0.1199f, 0.088f, 0.902f),
            line(9, 0.1488f, 0.804f, 0.902f),
            line(9, 0.1790f, 0.369f, 0.902f)
        ),
        markTexts = listOf(
            "أهم ما يُسأل عنه في هذا الجزء\n\n" +
                "Imipenem + cilastatin (لأن dehydropeptidase في الكلية يدمّره) — والـseizures مع imipenem خصوصًا في\n" +
                "الفشل الكلوي.\n\n" +
                "Aztreonam: Gram-negative aerobes فقط، وآمن في حساسية البنسلين."
        ),
        summary = listOf(
            "P: أهم ما يُسأل عنه في هذا الجزء",
            "P: Imipenem + cilastatin (لأن dehydropeptidase في الكلية يدمّره) — والـseizures مع imipenem خصوصًا في الفشل الكلوي.",
            "P: Aztreonam: Gram-negative aerobes فقط، وآمن في حساسية البنسلين."
        ),
        critical = listOf("يُسأل", "يدمّره", "خصوصًا", "(لأن", "Gram-negative aerobes")
    )

    /** الصفحة 10: تمييزان على نفس السطر العربي، الأيمن أولًا في القراءة. */
    val rtlSameLineOrder = GoldenCase(
        id = "p10-rtl-same-line-order",
        about = "تمييزان على سطر عربي واحد: الجزء الأيمن يأتي أولًا في الملخص",
        strokes = listOf(
            // الجزء الأيسر (آخر السطر في القراءة) يُظلَّل أولًا وبلون آخر
            line(9, 0.2671f, 0.133f, 0.400f, Pipeline.GREEN),
            line(9, 0.2671f, 0.576f, 0.902f)
        ),
        markTexts = listOf(
            "Ceftriaxone = DOC للسيلان (gonorrhea)؛",
            "BBB؛ ceftazidime ضد Pseudomonas."
        ),
        summary = listOf(
            "P: Ceftriaxone = DOC للسيلان (gonorrhea)؛",
            "P: BBB؛ ceftazidime ضد Pseudomonas."
        ),
        critical = listOf("للسيلان")
    )

    /** الصفحتان 5 و6: التمييز في الصفحة اللاحقة أولًا، ثم السابقة. */
    val pageOrder = GoldenCase(
        id = "p5-p6-page-order",
        about = "ترتيب الصفحات يُحفظ مهما كان ترتيب التظليل",
        strokes = listOf(
            line(5, 0.5961f, 0.058f, 0.438f),
            line(4, 0.7522f, 0.058f, 0.350f)
        ),
        // العنوانان بنفس الخط الأزرق الأكبر من نص الصفحة في الملف، فكلاهما عنوان
        markTexts = listOf("# Penicillin G (Benzylpenicillin)", "# Penicillin V (Phenoxymethylpenicillin)"),
        summary = listOf("H: Penicillin G (Benzylpenicillin)", "H: Penicillin V (Phenoxymethylpenicillin)")
    )

    /** الصفحة 5: نفس السطر يُظلَّل مرتين. */
    val reHighlight = GoldenCase(
        id = "p5-re-highlight",
        about = "تظليل نفس النص مرتين لا يكرره، بل يجعله مهمًا",
        strokes = listOf(
            line(4, 0.8033f, 0.079f, 0.520f),
            line(4, 0.8033f, 0.079f, 0.520f)
        ),
        markTexts = listOf("60–70 % destroyed by gastric acid → given IV or IM."),
        summary = listOf("P: 60–70 % destroyed by gastric acid → given IV or IM.")
    )

    /** الصفحة 5: سحب ثانٍ يتداخل مع الأول ويمتد بعده على نفس السطر. */
    val overlapExtend = GoldenCase(
        id = "p5-overlap-extend",
        about = "سحب ثانٍ يغطي معظم الأول ويمتد بعده: لا تضيع الكلمات الجديدة",
        strokes = listOf(
            Stroke(4, stroke(0.7794f, 0.075f, 0.600f)),
            Stroke(4, stroke(0.7794f, 0.190f, 0.800f))
        ),
        markTexts = listOf("First penicillin isolated; used as stable Na or K salts (crystalline penicillin); expressed in IU."),
        summary = listOf("P: First penicillin isolated; used as stable Na or K salts (crystalline penicillin); expressed in IU.")
    )

    /** الصفحة 5: تمييزان متداخلان جزئيًا بلونين. */
    val overlapTwoColors = GoldenCase(
        id = "p5-overlap-two-colors",
        about = "تمييزان متداخلان بلونين مختلفين: الكلمات المشتركة لا تتكرر في الملخص",
        strokes = listOf(
            Stroke(4, stroke(0.7794f, 0.075f, 0.450f)),
            Stroke(4, stroke(0.7794f, 0.300f, 0.800f), Pipeline.GREEN)
        ),
        markTexts = listOf(
            "First penicillin isolated; used as stable Na or K salts",
            "(crystalline penicillin); expressed in IU."
        ),
        summary = listOf(
            "P: First penicillin isolated; used as stable Na or K salts (crystalline penicillin); expressed in IU."
        )
    )

    val all = listOf(
        headingAndBullets, twoTables, doseTable, singleRow, subheading, numbers, wrappedCells,
        wrappedBothColumns, arabicBox, mixedSubtitle, arabicBullets, arabicDiacritics, rtlSameLineOrder,
        pageOrder, reHighlight, overlapExtend, overlapTwoColors
    )
}
