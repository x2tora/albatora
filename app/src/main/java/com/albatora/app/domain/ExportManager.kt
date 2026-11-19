package com.albatora.app.domain

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.albatora.app.model.PaymentStatus
import java.io.File
import java.io.FileOutputStream

object ExportManager {

    fun buildCsvContent(
        items: List<ResolvedRecordItem>,
        includeBom: Boolean = true
    ): String {
        val sb = StringBuilder()
        if (includeBom) sb.append('\uFEFF')
        sb.appendLine("날짜,근무지,구분,시작,종료,인정시간(분),기본급,수당합계,지원금,세금,수수료,경비,추가공제,실수령액,입금상태,메모")
        for (item in items) {
            val rec = item.record
            val br = item.breakdown
            val row = listOf(
                rec.date,
                rec.title,
                rec.status.label,
                PayrollCalculator.formatMinutes(rec.startMinute),
                PayrollCalculator.formatMinutes(rec.endMinute),
                br.paidWorkMinutes.toString(),
                br.basePay.toString(),
                br.allowancePay.toString(),
                br.siteBonus.toString(),
                br.taxAmount.toString(),
                br.agencyFee.toString(),
                (br.transportExpense + br.mealExpense).toString(),
                br.extraDeduction.toString(),
                br.netPay.toString(),
                if (item.isVirtualSchedule) "예정" else rec.paymentStatus.label,
                rec.memo
            ).joinToString(",") { escapeCsvCell(it) }
            sb.appendLine(row)
        }
        return sb.toString()
    }

    fun escapeCsvCell(raw: String): String {
        val needsQuote = raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
        if (!needsQuote) return raw
        return "\"${raw.replace("\"", "\"\"")}\""
    }

    fun renderPayslipPngUri(
        context: Context,
        title: String,
        summary: SettlementSummary
    ): Result<Uri> = runCatching {
        val lines = PayrollCalculator.buildPayslipText(title, summary).lines()
        val width = 1080
        val lineHeight = 52
        val headerHeight = 160
        val footerHeight = 90
        val height = (headerHeight + (lines.size * lineHeight) + footerHeight).coerceAtLeast(640)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#FFFFFF"))

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7280")
            textSize = 28f
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3482FF")
            strokeWidth = 4f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F2937")
            textSize = 30f
            typeface = Typeface.MONOSPACE
        }

        canvas.drawText("알바토라 급여 명세서", 64f, 82f, headerPaint)
        canvas.drawText("${summary.periodStart} ~ ${summary.periodEnd}", 64f, 126f, subPaint)
        canvas.drawLine(64f, 148f, (width - 64).toFloat(), 148f, accentPaint)

        var y = headerHeight + 44f
        for (line in lines) {
            canvas.drawText(line, 64f, y, bodyPaint)
            y += lineHeight
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "payslip_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun renderPayslipPdfUri(
        context: Context,
        title: String,
        summary: SettlementSummary
    ): Result<Uri> = runCatching {
        val lines = PayrollCalculator.buildPayslipText(title, summary).lines()
        val pageWidth = 595
        val pageHeight = 842
        val pdf = PdfDocument()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas
        canvas.drawText("알바토라 급여 명세서 - $title", 40f, 52f, titlePaint)
        var y = 84f

        for (line in lines) {
            if (y > pageHeight - 48f) {
                pdf.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }
            canvas.drawText(line, 40f, y, bodyPaint)
            y += 18f
        }
        pdf.finishPage(page)

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "payslip_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out ->
            pdf.writeTo(out)
        }
        pdf.close()

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareUri(
        context: Context,
        uri: Uri,
        mimeType: String,
        chooserTitle: String
    ) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }
}
