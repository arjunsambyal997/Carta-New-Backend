package com.equigcc.document;

import com.equigcc.certificate.CertificatePdfService;
import com.equigcc.certificate.IssueCertificateCommand;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Bilingual (English and Arabic) PDFs for the paperwork around the register: board minutes, a share transfer
 * instrument and a shareholder statement. Every label has an English line and an Arabic line.
 */
@Service
public class DocumentPdfService {

    public record Party(String nameEn, String nameAr, String detail) {
    }

    public record VoteLine(String holderEn, String holderAr, String vote, BigDecimal weight, Instant castAt) {
    }

    public record SignLine(String name, String role, String statement, Instant at, boolean valid) {
    }

    public record Minutes(String orgEn, String orgAr, String licence, String titleEn, String titleAr, String description, String threshold,
                          BigDecimal quorumPct, Instant deadline, Instant closedAt, String result, BigDecimal forWeight, BigDecimal againstWeight,
                          BigDecimal abstainWeight, BigDecimal participationPct, boolean quorumMet, BigDecimal totalPower,
                          List<VoteLine> votes, List<SignLine> signatures) {
    }

    public record TransferForm(String orgEn, String orgAr, String licence, Party transferor, Party transferee, String className, long quantity,
                               String currency, BigDecimal pricePerShare, BigDecimal consideration, LocalDate date, String oldCertificateNo,
                               String newCertificateNo) {
    }

    public record Holding(String certificateNo, String className, String instrument, long quantity, LocalDate dateIssued, String currency,
                          BigDecimal pricePerShare) {
    }

    public record Statement(String orgEn, String orgAr, String licence, Party holder, LocalDate asOf, List<Holding> holdings, long issuedShares,
                            long fullyDilutedShares, BigDecimal issuedPct, BigDecimal fullyDilutedPct, BigDecimal votes, BigDecimal votingPct) {
    }

    public record OptionGrant(String certificateNo, String className, String currency, BigDecimal strikePrice, long granted, long vested, long exercised,
                              long exercisable, long unvested, LocalDate startDate, int cliffMonths, int vestingMonths, LocalDate cliffDate,
                              LocalDate fullyVestedDate, LocalDate nextVestDate) {
    }

    public record OptionStatement(String orgEn, String orgAr, String licence, Party holder, LocalDate asOf, List<OptionGrant> grants) {
    }

    public record FilingOwner(String nameEn, String nameAr, String nationality, String documentNo, BigDecimal economicPct, BigDecimal votingPct,
                              String qualifiesBy, String route) {
    }

    public record FilingGap(String nameEn, String nameAr, BigDecimal economicPct, BigDecimal votingPct, String reason) {
    }

    public record FilingPack(String orgEn, String orgAr, String licence, String jurisdictionEn, String jurisdictionAr, String registryEn, String registryAr,
                             LocalDate asOf, BigDecimal thresholdPct, String currency, List<FilingOwner> owners, List<FilingGap> gaps, List<String> warnings) {
    }

    private static final Color INK = new Color(0x0f, 0x17, 0x2a);
    private static final Color MUTED = new Color(0x64, 0x74, 0x8b);
    private static final Color GREEN = new Color(0x04, 0x78, 0x57);
    private static final Color HEAD = new Color(0xec, 0xfd, 0xf5);
    private static final Color RULE = new Color(0xd1, 0xd5, 0xdb);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final byte[] regular;
    private final byte[] bold;

    public DocumentPdfService() throws IOException {
        this.regular = read("fonts/DejaVuSans.ttf");
        this.bold = read("fonts/DejaVuSans-Bold.ttf");
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }

    // ---- the three documents -----------------------------------------------------------------------------

    public byte[] minutes(Minutes m) {
        return build("Minutes of board resolution", m.orgEn(), (doc, f) -> {
            title(doc, f, m.orgEn(), m.orgAr(), m.licence(), "Minutes of board resolution", "محضر قرار مجلس الإدارة");
            doc.add(new Paragraph(m.titleEn(), f.h2));
            if (has(m.titleAr())) {
                doc.add(rtl(m.titleAr(), f.h2));
            }
            if (has(m.description())) {
                doc.add(new Paragraph(m.description(), f.body));
            }
            doc.add(space(f));
            kv(doc, f, List.of(
                    kv("Result\nالنتيجة", "PASSED".equals(m.result()) ? "Passed\nتمت الموافقة" : "Rejected\nمرفوض"),
                    kv("Majority required\nالأغلبية المطلوبة", "SPECIAL".equals(m.threshold()) ? "Special, 75% or more\nخاصة، 75% فأكثر" : "TWO_THIRDS".equals(m.threshold()) ? "Two-thirds or more\nثلثا الأصوات فأكثر" : "Ordinary, more than 50%\nعادية، أكثر من 50%"),
                    kv("Quorum required\nالنصاب المطلوب", pct(m.quorumPct()) + (m.quorumMet() ? "  (reached / تحقق)" : "  (not reached / لم يتحقق)")),
                    kv("Participation\nالمشاركة", pct(m.participationPct())),
                    kv("Votes\nالأصوات", "For / مع: " + n(m.forWeight()) + "    Against / ضد: " + n(m.againstWeight()) + "    Abstain / امتناع: " + n(m.abstainWeight())),
                    kv("Total voting power\nإجمالي القوة التصويتية", n(m.totalPower())),
                    kv("Voting deadline\nآخر موعد للتصويت", when(m.deadline())),
                    kv("Closed\nأُغلق", when(m.closedAt()))));
            doc.add(space(f));
            doc.add(new Paragraph("Votes cast / الأصوات المدلى بها", f.h2));
            var votes = new PdfPTable(new float[]{4, 1.6f, 1.6f, 2.6f});
            votes.setWidthPercentage(100);
            head(votes, f, "Shareholder\nالمساهم", "Vote\nالصوت", "Weight\nالوزن", "Cast at\nوقت التصويت");
            if (m.votes().isEmpty()) {
                none(votes, f, 4);
            }
            for (var v : m.votes()) {
                cell(votes, f, name(v.holderEn(), v.holderAr()), false);
                cell(votes, f, voteLabel(v.vote()), false);
                cell(votes, f, n(v.weight()), true);
                cell(votes, f, when(v.castAt()), false);
            }
            doc.add(votes);
            doc.add(space(f));
            doc.add(new Paragraph("Signatures / التوقيعات", f.h2));
            if (m.signatures().isEmpty()) {
                doc.add(new Paragraph("No signatures recorded yet. / لا توجد توقيعات مسجلة بعد.", f.small));
            } else {
                var sigs = new PdfPTable(new float[]{3, 2, 4, 2.6f});
                sigs.setWidthPercentage(100);
                head(sigs, f, "Signer\nالموقّع", "Role\nالصفة", "Statement\nالإقرار", "Signed at\nتاريخ التوقيع");
                for (var s : m.signatures()) {
                    cell(sigs, f, s.name() + (s.valid() ? "" : "\n(changed since signing / تغيّر بعد التوقيع)"), false);
                    cell(sigs, f, s.role(), false);
                    cell(sigs, f, s.statement(), false);
                    cell(sigs, f, when(s.at()), false);
                }
                doc.add(sigs);
            }
            doc.add(space(f));
            doc.add(new Paragraph("Signatures are authenticator-code confirmations recorded in the register. They are not qualified electronic signatures. / "
                    + "التوقيعات المسجلة هنا إقرارات برمز المصادقة في السجل وليست توقيعات إلكترونية موثقة.", f.small));
        });
    }

    public byte[] transferForm(TransferForm t) {
        return build("Share transfer instrument", t.orgEn(), (doc, f) -> {
            title(doc, f, t.orgEn(), t.orgAr(), t.licence(), "Share transfer instrument", "وثيقة نقل ملكية أسهم");
            kv(doc, f, List.of(
                    kv("Transferor (seller)\nالمحيل (البائع)", party(t.transferor())),
                    kv("Transferee (buyer)\nالمحال إليه (المشتري)", party(t.transferee())),
                    kv("Share class\nفئة الأسهم", t.className()),
                    kv("Number of shares\nعدد الأسهم", String.format(Locale.ROOT, "%,d", t.quantity())),
                    kv("Price per share\nسعر السهم", t.currency() + " " + n(t.pricePerShare())),
                    kv("Total consideration\nإجمالي المقابل", t.currency() + " " + n(t.consideration())),
                    kv("Transfer date\nتاريخ النقل", t.date().toString()),
                    kv("Certificate surrendered\nالشهادة المسلّمة", t.oldCertificateNo()),
                    kv("New certificate\nالشهادة الجديدة", t.newCertificateNo())));
            doc.add(space(f));
            doc.add(new Paragraph("The transferor transfers the shares above to the transferee, and the register has been updated accordingly. "
                    + "The surrendered certificate is retired and the new certificate is issued to the transferee.", f.body));
            doc.add(rtl("ينقل المحيل الأسهم المذكورة أعلاه إلى المحال إليه، وقد جرى تحديث السجل وفقًا لذلك. تُلغى الشهادة المسلّمة وتصدر شهادة جديدة باسم المحال إليه.", f.body));
            signatureBlock(doc, f, "Transferor\nالمحيل", "Transferee\nالمحال إليه", "For the company\nعن الشركة");
        });
    }

    public byte[] statement(Statement s) {
        return build("Shareholder statement", s.orgEn(), (doc, f) -> {
            title(doc, f, s.orgEn(), s.orgAr(), s.licence(), "Shareholder statement", "كشف حساب مساهم");
            kv(doc, f, List.of(
                    kv("Shareholder\nالمساهم", party(s.holder())),
                    kv("As of\nبتاريخ", s.asOf() + " (end of day, UTC)"),
                    kv("Shares held\nالأسهم المملوكة", String.format(Locale.ROOT, "%,d", s.issuedShares())),
                    kv("Ownership (issued)\nالملكية (المصدرة)", pct(s.issuedPct())),
                    kv("Ownership (fully diluted)\nالملكية (بعد التخفيف)", pct(s.fullyDilutedPct()) + "  (" + String.format(Locale.ROOT, "%,d", s.fullyDilutedShares()) + ")"),
                    kv("Voting power\nالقوة التصويتية", n(s.votes()) + "  (" + pct(s.votingPct()) + ")")));
            doc.add(space(f));
            doc.add(new Paragraph("Holdings / الحيازات", f.h2));
            var t = new PdfPTable(new float[]{2.4f, 2.4f, 1.6f, 1.6f, 1.8f, 2.2f});
            t.setWidthPercentage(100);
            head(t, f, "Certificate\nالشهادة", "Class\nالفئة", "Instrument\nالأداة", "Quantity\nالكمية", "Issued\nتاريخ الإصدار", "Price per share\nسعر السهم");
            if (s.holdings().isEmpty()) {
                none(t, f, 6);
            }
            for (var h : s.holdings()) {
                cell(t, f, h.certificateNo(), false);
                cell(t, f, h.className(), false);
                cell(t, f, h.instrument(), false);
                cell(t, f, String.format(Locale.ROOT, "%,d", h.quantity()), true);
                cell(t, f, h.dateIssued().toString(), false);
                cell(t, f, h.currency() + " " + n(h.pricePerShare()), true);
            }
            doc.add(t);
            doc.add(space(f));
            doc.add(new Paragraph("This statement reflects the share register on the date shown. It is not a certificate of title; "
                    + "use the numbered share certificates, which carry a verification QR code.", f.small));
            doc.add(rtl("يعكس هذا الكشف سجل الأسهم في التاريخ المذكور وليس سند ملكية؛ يُرجع إلى شهادات الأسهم المرقمة التي تحمل رمز تحقق QR.", f.small));
        });
    }

    public byte[] optionStatement(OptionStatement s) {
        return build("Option statement", s.orgEn(), (doc, f) -> {
            title(doc, f, s.orgEn(), s.orgAr(), s.licence(), "Option statement", "كشف خيارات الأسهم");
            long granted = s.grants().stream().mapToLong(OptionGrant::granted).sum();
            long exercised = s.grants().stream().mapToLong(OptionGrant::exercised).sum();
            long exercisable = s.grants().stream().mapToLong(OptionGrant::exercisable).sum();
            long unvested = s.grants().stream().mapToLong(OptionGrant::unvested).sum();
            kv(doc, f, List.of(
                    kv("Holder\nالحامل", party(s.holder())),
                    kv("As of\nبتاريخ", s.asOf().toString()),
                    kv("Options granted\nالخيارات الممنوحة", String.format(Locale.ROOT, "%,d", granted)),
                    kv("Exercised\nتمت ممارستها", String.format(Locale.ROOT, "%,d", exercised)),
                    kv("Vested, can exercise now\nمستحقة ويمكن ممارستها الآن", String.format(Locale.ROOT, "%,d", exercisable)),
                    kv("Not yet vested\nغير مستحقة بعد", String.format(Locale.ROOT, "%,d", unvested))));
            doc.add(space(f));
            doc.add(new Paragraph("Grants / المنح", f.h2));
            var t = new PdfPTable(new float[]{2.6f, 1.7f, 1.3f, 1.3f, 1.5f, 1.6f, 1.5f});
            t.setWidthPercentage(100);
            head(t, f, "Certificate\nالشهادة", "Strike\nسعر الممارسة", "Granted\nممنوحة", "Vested\nمستحقة", "Exercised\nممارسة", "Can exercise\nقابلة للممارسة", "Unvested\nغير مستحقة");
            if (s.grants().isEmpty()) {
                none(t, f, 7);
            }
            for (var g : s.grants()) {
                cell(t, f, g.certificateNo() + "\n" + g.className(), false);
                cell(t, f, g.currency() + " " + n(g.strikePrice()), true);
                cell(t, f, String.format(Locale.ROOT, "%,d", g.granted()), true);
                cell(t, f, String.format(Locale.ROOT, "%,d", g.vested()), true);
                cell(t, f, String.format(Locale.ROOT, "%,d", g.exercised()), true);
                cell(t, f, String.format(Locale.ROOT, "%,d", g.exercisable()), true);
                cell(t, f, String.format(Locale.ROOT, "%,d", g.unvested()), true);
                var schedule = cellOf(f.small, "Vesting starts " + g.startDate() + "  |  cliff " + g.cliffMonths() + " of " + g.vestingMonths() + " months (" + g.cliffDate()
                        + ")  |  fully vested " + g.fullyVestedDate() + (g.nextVestDate() == null ? "" : "  |  next vest " + g.nextVestDate()));
                schedule.setColspan(7);
                t.addCell(schedule);
            }
            doc.add(t);
            doc.add(space(f));
            doc.add(new Paragraph("Options vest monthly after the cliff. Vested options can be exercised into shares by contacting the company; the register then retires "
                    + "the option certificate and issues a share certificate. This statement is information only and is not an offer or tax advice.", f.small));
            doc.add(rtl("تستحق الخيارات شهريًا بعد فترة الاستحقاق الأولى. يمكن ممارسة الخيارات المستحقة وتحويلها إلى أسهم بالتواصل مع الشركة، وعندها يلغي السجل شهادة الخيار ويصدر شهادة أسهم. هذا الكشف للعلم فقط وليس عرضًا ولا استشارة ضريبية.", f.small));
        });
    }

    public byte[] filingPack(FilingPack p) {
        return build("Beneficial ownership filing pack", p.orgEn(), (doc, f) -> {
            title(doc, f, p.orgEn(), p.orgAr(), p.licence(), "Beneficial ownership filing pack", "حزمة إيداع المستفيدين الفعليين");
            kv(doc, f, List.of(
                    kv("Filing to\nجهة الإيداع", p.registryEn() + "\n" + p.registryAr()),
                    kv("Jurisdiction\nالاختصاص", p.jurisdictionEn() + "\n" + p.jurisdictionAr()),
                    kv("Company\nالشركة", p.orgEn() + (has(p.orgAr()) ? "\n" + p.orgAr() : "")),
                    kv("Commercial licence no.\nرقم الرخصة التجارية", p.licence()),
                    kv("Register date\nتاريخ السجل", p.asOf() + " (UTC)"),
                    kv("Ownership threshold\nحد الملكية", pct(p.thresholdPct()) + " of economic rights or voting rights")));
            doc.add(space(f));
            doc.add(new Paragraph("Beneficial owners / المستفيدون الفعليون", f.h2));
            var t = new PdfPTable(new float[]{3.2f, 1.4f, 1.8f, 1.2f, 1.2f, 1.4f, 3.6f});
            t.setWidthPercentage(100);
            head(t, f, "Name\nالاسم", "Nationality\nالجنسية", "ID / passport\nالهوية / الجواز", "Economic\nالاقتصادية", "Voting\nالتصويت", "Basis\nالأساس", "Route\nالمسار");
            if (p.owners().isEmpty()) {
                none(t, f, 7);
            }
            for (var o : p.owners()) {
                cell(t, f, name(o.nameEn(), o.nameAr()), false);
                cell(t, f, o.nationality(), false);
                cell(t, f, o.documentNo(), false);
                cell(t, f, pct(o.economicPct()), true);
                cell(t, f, pct(o.votingPct()), true);
                cell(t, f, o.qualifiesBy(), false);
                cell(t, f, o.route(), false);
            }
            doc.add(t);
            if (!p.gaps().isEmpty()) {
                doc.add(space(f));
                doc.add(new Paragraph("Ownership not traced to a natural person / ملكية لم تُتتبَّع إلى شخص طبيعي", f.h2));
                var g = new PdfPTable(new float[]{4, 1.4f, 1.4f, 5});
                g.setWidthPercentage(100);
                head(g, f, "Company\nالشركة", "Economic\nالاقتصادية", "Voting\nالتصويت", "Reason\nالسبب");
                for (var x : p.gaps()) {
                    cell(g, f, name(x.nameEn(), x.nameAr()), false);
                    cell(g, f, pct(x.economicPct()), true);
                    cell(g, f, pct(x.votingPct()), true);
                    cell(g, f, x.reason(), false);
                }
                doc.add(g);
                doc.add(new Paragraph("Resolve these before filing: record who owns each company on the Beneficial owners page. / عالج هذه البنود قبل الإيداع.", f.small));
            }
            doc.add(space(f));
            doc.add(new Paragraph("Documents usually attached / المرفقات المعتادة", f.h2));
            for (String line : List.of(
                    "[  ]  Certified copy of the passport or ID of each beneficial owner  /  صورة مصدقة من جواز سفر أو هوية كل مستفيد فعلي",
                    "[  ]  Proof of residential address of each beneficial owner  /  إثبات عنوان إقامة كل مستفيد فعلي",
                    "[  ]  Extract of the share register (cap table PDF from Sijill)  /  مستخرج من سجل الأسهم",
                    "[  ]  Articles of association  /  عقد التأسيس أو النظام الأساسي",
                    "[  ]  Board resolution authorising the filing, if required  /  قرار مجلس الإدارة بالتفويض بالإيداع إن لزم")) {
                doc.add(new Paragraph(line, f.body));
            }
            doc.add(space(f));
            doc.add(new Paragraph("Declaration / إقرار", f.h2));
            doc.add(new Paragraph("I confirm that the information above is complete and correct to the best of my knowledge, and that I will notify the registry of any change within the period required by law.", f.body));
            doc.add(rtl("أقرّ بأن المعلومات الواردة أعلاه كاملة وصحيحة بحسب علمي، وأنني سأخطر جهة التسجيل بأي تغيير خلال المدة التي يقتضيها القانون.", f.body));
            signatureBlock(doc, f, "Authorised signatory\nالمفوض بالتوقيع", "Name and title\nالاسم والصفة");
            for (String w : p.warnings()) {
                doc.add(new Paragraph("Note: " + w, f.small));
            }
            doc.add(space(f));
            doc.add(new Paragraph("This pack follows a generic layout with the fields these registers ask for. " + p.registryEn() + " publishes its own form and may change it; "
                    + "check the current form and copy these answers across.", f.small));
            doc.add(rtl("تتبع هذه الحزمة تصميمًا عامًا يضم الحقول التي تطلبها هذه السجلات. تنشر جهة التسجيل نموذجها الخاص وقد تغيّره؛ راجع النموذج الحالي وانقل الإجابات إليه.", f.small));
        });
    }

    // ---- building blocks ---------------------------------------------------------------------------------

    private record Fonts(Font h1, Font h2, Font body, Font bold, Font small) {
    }

    private interface Body {
        void write(Document doc, Fonts f) throws DocumentException;
    }

    private byte[] build(String subject, String org, Body body) {
        try {
            var out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter.getInstance(doc, out);
            doc.addTitle(subject + " - " + org);
            doc.addCreator("Sijill");
            doc.open();
            BaseFont reg = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, regular, null);
            BaseFont bld = BaseFont.createFont("DejaVuSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bold, null);
            body.write(doc, new Fonts(new Font(bld, 16, Font.NORMAL, GREEN), new Font(bld, 11, Font.NORMAL, INK), new Font(reg, 9, Font.NORMAL, INK),
                    new Font(bld, 9, Font.NORMAL, INK), new Font(reg, 7.5f, Font.NORMAL, MUTED)));
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Could not render " + subject, e);
        }
    }

    private static void title(Document doc, Fonts f, String orgEn, String orgAr, String licence, String en, String ar) throws DocumentException {
        doc.add(new Paragraph(en, f.h1));
        doc.add(rtl(ar, f.h1));
        doc.add(new Paragraph(orgEn, f.h2));
        if (has(orgAr)) {
            doc.add(rtl(orgAr, f.h2));
        }
        doc.add(new Paragraph("Licence no. / رقم الرخصة: " + licence, f.small));
        doc.add(space(f));
    }

    private static Paragraph space(Fonts f) {
        return new Paragraph(" ", f.small);
    }

    private static String[] kv(String label, String value) {
        return new String[]{label, value};
    }

    private static void kv(Document doc, Fonts f, List<String[]> rows) throws DocumentException {
        var t = new PdfPTable(new float[]{2.4f, 5f});
        t.setWidthPercentage(100);
        for (String[] r : rows) {
            var l = cellOf(f.bold, r[0]);
            l.setBackgroundColor(HEAD);
            t.addCell(l);
            t.addCell(cellOf(f.body, r[1]));
        }
        doc.add(t);
    }

    private static void signatureBlock(Document doc, Fonts f, String... who) throws DocumentException {
        doc.add(new Paragraph(" ", f.body));
        doc.add(new Paragraph(" ", f.body));
        var t = new PdfPTable(who.length);
        t.setWidthPercentage(100);
        for (String w : who) {
            var c = new PdfPCell();
            c.setBorder(0);
            c.setPaddingTop(28);
            c.setPaddingRight(14);
            var line = new PdfPTable(1);
            line.setWidthPercentage(100);
            var rule = new PdfPCell(new Phrase(" ", f.body));
            rule.setBorder(org.openpdf.text.Rectangle.BOTTOM);
            rule.setBorderColor(INK);
            line.addCell(rule);
            c.addElement(line);
            for (String l : w.split("\n")) {
                c.addElement(CertificatePdfService.containsArabic(l) ? rtl(l, f.small) : new Paragraph(l, f.small));
            }
            c.addElement(new Paragraph("Date / التاريخ: ____________", f.small));
            t.addCell(c);
        }
        doc.add(t);
    }

    private static void head(PdfPTable t, Fonts f, String... labels) {
        for (String l : labels) {
            var c = cellOf(f.bold, l);
            c.setBackgroundColor(HEAD);
            t.addCell(c);
        }
    }

    private static void none(PdfPTable t, Fonts f, int span) {
        var c = new PdfPCell(new Phrase("None / لا يوجد", f.body));
        c.setColspan(span);
        c.setPadding(5);
        t.addCell(c);
    }

    private static void cell(PdfPTable t, Fonts f, String text, boolean right) {
        var c = cellOf(f.body, text == null ? "" : text);
        if (right) {
            c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        }
        t.addCell(c);
    }

    /** A cell whose lines containing Arabic are shaped and run right to left, and whose other lines stay left to right. */
    private static PdfPCell cellOf(Font font, String text) {
        var c = new PdfPCell();
        for (String line : text.split("\n")) {
            c.addElement(CertificatePdfService.containsArabic(line) ? rtl(line, font) : new Paragraph(line, font));
        }
        c.setPadding(4);
        c.setBorderColor(RULE);
        return c;
    }

    private static PdfPTable rtl(String text, Font f) {
        var t = new PdfPTable(1);
        t.setWidthPercentage(100);
        var c = new PdfPCell(new Phrase(text, f));
        c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setBorder(0);
        c.setPadding(0);
        t.addCell(c);
        return t;
    }

    private static String party(Party p) {
        String s = has(p.nameAr()) ? p.nameEn() + "\n" + p.nameAr() : p.nameEn();
        return has(p.detail()) ? s + "\n" + p.detail() : s;
    }

    private static String name(String en, String ar) {
        return has(ar) ? en + "\n" + ar : en;
    }

    private static String voteLabel(String v) {
        return switch (v) {
            case "FOR" -> "For\nمع";
            case "AGAINST" -> "Against\nضد";
            default -> "Abstain\nامتناع";
        };
    }

    private static String when(Instant i) {
        return i == null ? "" : WHEN.format(i);
    }

    private static String pct(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString() + "%";
    }

    private static String n(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }
}
