-- مدة الحفظ (سياسة الخصوصية: «نتائج فحص السلامة الفاشلة 90 يومًا ثم تُحذف تلقائيًا»).
-- مهمة يومية في قاعدة البيانات تحذف ما مضى عليه أكثر من 90 يومًا من integrity_failures.
create extension if not exists pg_cron;

-- cron.schedule بنفس الاسم يحدّث المهمة الموجودة بدل تكرارها، فيمكن تشغيل هذا الملف أكثر من مرة.
select cron.schedule(
  'purge-integrity-failures',
  '17 3 * * *',
  $$delete from public.integrity_failures where created_at < now() - interval '90 days'$$
);

-- حذف فوري لما تجاوز المدة الآن، دون انتظار أول تشغيل للمهمة.
delete from public.integrity_failures where created_at < now() - interval '90 days';
