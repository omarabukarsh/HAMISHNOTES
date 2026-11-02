package com.hamishnotes.app.golden

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



    val all = listOf(
        headingAndBullets, twoTables, doseTable, reHighlight
    )
}
