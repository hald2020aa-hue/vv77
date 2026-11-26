# AUDIT — Manga Virus (المرحلة 0)

> كل ما في هذا الملف مأخوذ من قراءة الشيفرة في الفرع `main` عند الالتزام `91ab210`، ولم يُجرَّب على جهاز. أرقام الملفات من أوامر `grep/find` على المستودع.

## 1. بنية المشروع

| البند | القيمة |
|---|---|
| وحدات Gradle | وحدة واحدة فقط: `:app` (+ `buildSrc`). لا توجد وحدات منفصلة للواجهة أو البيانات. |
| ملفات Kotlin | 728 ملفًا في `app/src/main/java`؛ 11 ملف اختبار فقط. |
| الواجهة | Conductor (Controller لكل شاشة) + FlexibleAdapter + ViewBinding + Material 3 Expressive؛ شاشتا الرئيسية والتنزيلات بـ Jetpack Compose؛ الإعدادات بـ androidx.preference. |
| minSdk / JVM | 26 / 17 |
| الـ flavors | بُعد واحد `default`: `standard` (واجهة التطبيق الأصلية) و`dev` (واجهة النيون الحالية، applicationId = `eu.kanade.tachiyomi.enhanced`، وفي debug تصبح `eu.kanade.tachiyomi.enhanced.debugJ2K`). |
| الموارد | `src/main/res`: 145 تخطيطًا و326 drawable. `src/dev/res`: 39 تخطيطًا و108 drawables تُغطي الأصل بالاسم نفسه. |
| الثيمات | `Theme.Base` يرث `Theme.Material3Expressive.DynamicColors.DayNight.NoActionBar`، وفوقه 12 ثيمًا (Monet، FlatLime، YinYang...). الألوان الديناميكية (Monet) تتفعّل على Android 12+ عبر `values-v31`/`values-night-v31`/`values-v34`. |

### الشاشات وملفاتها
- الرئيسية: `ui/home/HomeController` (Compose) — خاص بالنسخة الحالية.
- المكتبة: `ui/library/*` (LibraryController، LibraryGridHolder، LibraryListHolder...).
- التصفح والمصادر: `ui/source/*` (BrowseController، BrowseSourceController، globalsearch).
- التفاصيل والفصول: `ui/manga/*` (MangaDetailsController، MangaHeaderHolder، chapter/ChapterHolder).
- القارئ: `ui/reader/*` (ReaderActivity 3000+ سطر، viewers).
- التحديثات والسجل: `ui/recents/*`.
- التنزيلات: `ui/download/center/DownloadCenterActivity` + `NeonDownloadPanel` (Compose)، و`download_bottom_sheet`.
- الإعدادات: `ui/setting/*` (+ `NeonSettingsStyler` يلوّن الصفوف وقت التشغيل).
- التنقل الرئيسي: `ui/main/MainActivity` (BottomNavigationView أو SideNav حسب العرض، وقائمة `menu/bottom_navigation.xml`).

### طبقة البيانات والمصادر (لن تُمَسّ)
`extension/`، `source/`، `network/`، `data/` (database، download، backup، track، library، cache)، `smartsearch/`، `crash/`. أي تعديل فيها يتطلب خطأً مثبتًا وتوثيقًا.

## 2. ما وجدته

1. **تسرّب منطق النيون إلى `main`:** 16 ملف Kotlin في `main` يقرأ `R.bool.v4_neon_mode` ويتفرّع (أكثرها `MangaHeaderHolder` 7 مواضع، `ReaderActivity` 5). هذا يعني أن الواجهة ليست معزولة تمامًا عن النواة، وإضافة 4 هويات أخرى بالأسلوب نفسه (فروع `if` في `main`) ستجعل الشيفرة غير قابلة للصيانة.
2. **أسلوب التخصيص الحالي هو "Resource overlay":** تخطيطات `src/dev/res/layout` تُغطّي ملفات `main` بالاسم نفسه. شرطه أن تبقى كل `android:id` في الأصل بالنوع نفسه، لأن Kotlin في `main` يستعمل ViewBinding المُولَّد من التخطيط المدموج. فاحص `qa/v22/check_resources.py` يفرض ذلك.
3. **المتغيرات المؤهَّلة تتجاوز overlay:** `main/res/layout-sw600dp-land/manga_header_item.xml` (وغيره من `layout-land`, `layout-w720dp`, `layout-h600dp`) يتقدّم على `dev/res/layout` بنفس الاسم على الأجهزة الكبيرة، فتظهر واجهة `main` الأصلية. أي هوية جديدة يجب أن تُغطّي هذه المتغيرات أو تقبل الرجوع للأصل فيها.
4. **ألوان Monet:** على Android 12+ تتغلب `colors_monet.xml` في `main/res/values-*-v31|v34` على ألوان flavor العادية، فتظهر الأسطح رمادية. الإصلاح (مطبّق في `dev`): تعريف الملفات نفسها في `src/<flavor>/res/values-*-v31|v34`.
5. **سلسلة التحقق في CI قديمة:** `quality_gate.yml` كان يشغّل 7 مدققات تتوقع واجهة الإصدارات السابقة (قياسات V21 وأسماء)، فتوقف قبل Gradle. استُبدلت بـ `qa/v22/check_resources.py` و`check_runtime_risks.py`. والـ workflow لا يُشغَّل إلا على `main` و`feature/**`؛ أضيف `fix/**` و`design/**`.

## 3. تحليل الانهيار (SM-F936U1، Android 16)

```
IllegalStateException: Attempted to get ShapeAppearanceModel from a MaterialButton which has an overwritten background.
  MaterialButton.getShapeAppearanceModel
  MaterialContainerTransformSharedElementCallback$ShapeableViewShapeProvider.provideShape
  MaterialContainerTransformSharedElementCallback.onCreateSnapshotView
  ... EnterTransitionCoordinator ...
```

- العنصر المشترك في انتقال القارئ مسمّى في الشيفرة: `details start reading transition` (`MangaHeaderHolder.kt:113`)، و`library chapter … transition` (`playButton` في LibraryGridHolder/ListHolder)، و`recents chapter …` و`details chapter …`.
- Material يقرأ شكل العنصر المشترك من `Shapeable.getShapeAppearanceModel()`. في `MaterialButton` يرمي هذا الاستثناء إذا استُبدلت خلفيته (`setBackground`).
- في `main` عند `v4_neon_mode=true` (أي `dev`): `MangaHeaderHolder.kt` (سطر 777 قبل الإصلاح) كان ينفّذ `startReadingButton.background = …v12_neon_read_button` على الزر المشترك نفسه → الاستثناء عند فتح القارئ من صفحة التفاصيل.
- بحثت في كل `main/java` عن `background =` و`setBackground*`: لا يوجد موضع آخر يستبدل خلفية `MaterialButton`. المواضع الأخرى تتعلق بـ `View`/`LinearLayout`/`MaterialRadioButton` ليست عناصر مشتركة في انتقال. وفي التخطيطات (`main` و`dev`) لا يوجد `MaterialButton` بـ `android:background` (يفحصه `check_runtime_risks.py` الآن لـ `dev`).
- الحل الجذري المطبّق في `fix/materialbutton-crash`: كلاس `widget/NeonGradientButton` يحتفظ بخلفية Material الأصلية (فيبقى الـ shape model صالحًا)، ويرسم التدرج والإطار في `onDraw`، ويضع الـ ripple في `foreground`.
- **ملاحظة صريحة:** في محاولة سابقة (قبل هذه التعليمات) أضفت `SafeContainerTransformCallback` يلتقط الاستثناء بـ `try/catch`. هذا مخالف لقواعدك، فأُزيل في هذا الفرع (التزام مستقل). نسخة `backup/main-before-redesign` أُنشئت من `main` كما كان (تحتوي عمل V22 وذلك الحل الالتفافي) لأن الحالة قبل V22 غير موجودة في المستودع أصلًا (النسخة الأصلية V21 لم تُرفع).
- `ReaderActivity` و`MainActivity` عادا لاستعمال `MaterialContainerTransformSharedElementCallback` الأصلي. حركات الانتقال لم تُعطَّل.

## 4. المعمارية المقترحة للهويات الخمس

### الخيارات التي قارنتها
| الخيار | تقييمه |
|---|---|
| **أ) وحدة Gradle لكل هوية (`:ui-ink`, ...)** | غير ممكن عمليًا: الـ Controllers والـ ViewBinding المُولَّد والـ DI كلها داخل `:app`؛ الفصل يتطلب إعادة هيكلة 728 ملفًا. مرفوض. |
| **ب) فروع `if (design == X)` داخل `main`** | هو ما يحدث الآن مع `v4_neon_mode`، ولا يتوسع إلى 5 هويات. مرفوض. |
| **ج) 5 flavors في البعد الحالي، لكل flavor: `src/<flavor>/res` + `src/<flavor>/java`، والنواة في `main` لا تعرف الهوية** | **المقترح.** |
| د) بعد ثانٍ `design` × `standard/dev` | يضاعف عدد المتغيرات (10+) دون فائدة؛ هوية `standard` الأصلية تبقى للمقارنة فقط. |

### الاختيار (ج) بالتفصيل
1. **flavors:** `standard` (يبقى كما هو)، و`neonReference`, `cinemaNova`, `inkAtelier`, `reactorX`, `fluidBloom`. لكل منها `applicationIdSuffix` و`app_name` وأيقونة خاصة، وABI واحد `arm64-v8a`. تُبنى كل هوية بمهمة Gradle منفصلة (`assemble<Flavor>Debug`) في job مستقل (matrix) حتى لا يعطّل فشل واحدة الباقي.
2. **الموارد:** كل هوية تغطي `main` بالأسماء نفسها (تخطيطات، drawables، values، menus، anim، transitions) مع الحفاظ على كل `android:id` بنوعه (يُعمَّم فاحص `check_resources.py` على كل flavor). عناصر الواجهة التي تخص هوية بعينها (مثلًا درج جانبي) تُضاف بمعرّفات تُعلَّن في `main/values/ids.xml` ويصل لها Kotlin عبر `findViewById(...)?` فقط، لا عبر حقول الـ binding.
3. **نقاط التمدد في Kotlin:** بدل `v4_neon_mode` أُدخل واجهة صغيرة في `main`، مثلًا `interface DesignHooks` (نمط التنقل، ألوان/أنماط العناصر التي تُرسَم وقت التشغيل مثل `MangaHeaderHolder` و`LibraryBadge`، لغة الانتقال: container transform / shared axis / fade through)، ولها تنفيذ افتراضي في `main` (سلوك `standard`)، وكل flavor يقدّم تنفيذه في `src/<flavor>/java` بنفس اسم الكلاس (يتوفر عند الربط لأن الـ source set الخاص بالـ flavor يضيف الكلاس نفسه ولا يوجد في `main`). الـ 16 موضعًا الحالية لـ `v4_neon_mode` تُنقل تدريجيًا إلى هذه الخطافات.
4. **الانتقالات:** تُعرَّف في `res/transition` و`res/anim` لكل flavor، ولا تُعطَّل (لا try/catch). يُسمح بتغيير نوع الانتقال لكل هوية لكن العناصر المشتركة تبقى بأسمائها الحالية.
5. **التنقل (شريط سفلي، درج، شريط عائم، تبويبات علوية، إيماءات):** `MainActivity` اليوم تفترض `BottomNavigationView`/`SideNav`. سيُستخرج جزء التنقل خلف خطاف `NavigationHost` بحيث تُنفّذه كل هوية، دون تغيير منطق `setRoot`/`backstack` الداخلي.
6. **الأيقونات:** مجموعة drawables vector لكل flavor بالأسماء نفسها التي يستعملها الكود، فلا يتغير الكود.
7. **RTL:** استعمال `start/end` حصرًا، واختبار الأيقونات التي يجب أن تنعكس عبر `autoMirrored`.
8. **بيانات المستخدم:** كل flavor بـ applicationId مختلف يعني قاعدة بيانات وملفات منفصلة. الانتقال بين الهويات يتم **حصرًا** عبر النسخ الاحتياطي/الاستعادة الموجود بالتطبيق (`data/backup`). لن يحذف شيء أي بيانات. سيُوثَّق في `docs/<variant>.md`.

### ما لا يمكن فصله تمامًا (موثّق)
- **ViewBinding المشترك:** التخطيطات يجب أن تحوي كل الـ ids التي يستعملها Kotlin، لذلك هوية مثل Ink Atelier لا تستطيع حذف عنصر؛ تخفيه (`gone`) أو تعيد استعماله بشكل مختلف.
- **المتغيرات المؤهَّلة (`sw600dp-land`...):** تحتاج نسخة لكل هوية أو تقبل الرجوع إلى تخطيط `main` على الأجهزة الكبيرة.
- **`ReaderActivity`:** ملف ضخم يمزج الواجهة مع منطق العرض؛ سيُقتصر التخصيص على التخطيط (`reader_activity.xml`, `reader_nav.xml`, sheets) وعلى الخطافات، دون لمس محرك العرض.
- **الإعدادات:** شاشات androidx.preference تُبنى من DSL؛ التخصيص عبر ثيم/أنماط الـ preference وخطّاف التلوين، لا عبر إعادة كتابة الشاشات.

## 5. قرارات أحتاج موافقتك قبل بدء النسخ
1. **applicationId لـ Neon Reference:** إن بقي `eu.kanade.tachiyomi.enhanced` (المثبّت حاليًا عندك) تُحفظ البيانات وتتحدث النسخة فوق الحالية. وإن غُيّر إلى suffix جديد (مثل `.neon`) فستُثبَّت كتطبيق منفصل بلا بيانات (استعادة من نسخة احتياطية). أقترح الإبقاء عليه.
2. **flavor `dev` الحالي:** أقترح إعادة تسميته إلى `neonReference` (مع الإبقاء على الـ suffix) بدل إبقائه بجانبه.
3. **الأيقونات والخطوط:** لا يوجد اتصال شبكة في بيئة العمل لتنزيل خطوط؛ الخطوط ستكون ممّا هو متاح داخل المشروع/النظام أو تُضمَّن ملفات ترخيصها حر تقدّمها أنت.

## 6. حدود بيئتي (لا يُدّعى غير ذلك)
- لا Android SDK ولا اتصال بمستودعات Maven محليًا؛ البناء يتم فقط عبر GitHub Actions.
- لا جهاز ولا محاكي: لا أستطيع تشغيل التطبيق أو رؤية الواجهة أو اختبار الانتقال. أي اختبار تشغيل سيُسجَّل كـ"لم يُختبر" إلى أن ترسل أنت لقطات/سجلات.
- لم يصلني الملف `materialbutton-crash-fix.patch` المذكور (المرفق هو `vv77-main_1.zip` ونص التعليمات فقط)، فكتبت الإصلاح من الوصف في تعليماتك. إن أرسلت الـ patch أقارنه بما كتبته وأعتمد ما يلزم.
