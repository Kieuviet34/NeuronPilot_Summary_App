package com.bhs.meetingnotes.util

import android.os.Environment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import android.widget.Toast
import com.bhs.meetingnotes.db.MeetingEntity
import com.bhs.meetingnotes.model.ActionItem
import java.io.File
import java.io.FileOutputStream

object ReportExporter {
    private const val TAG = "ReportExporter"

    fun exportReport(context: Context, meeting: MeetingEntity, format: String) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val currentDate = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "meeting_summary_$currentDate"
        
        when (format.uppercase()) {
            "PDF" -> exportPdf(context, meeting, File(dir, "$fileName.pdf"))
            "DOCX" -> exportDocx(context, meeting, File(dir, "$fileName.docx"))
            else -> exportTxt(context, meeting, File(dir, "$fileName.txt"))
        }
    }

    private fun exportTxt(context: Context, meeting: MeetingEntity, file: File) {
        try {
            val content = buildTextContent(meeting)
            file.writeText(content)
            showExportSuccess(context, file)
        } catch (e: Exception) {
            Log.e(TAG, "Export TXT failed", e)
            Toast.makeText(context, "Xuất TXT thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportDocx(context: Context, meeting: MeetingEntity, file: File) {
        try {
            val content = buildTextContent(meeting)
            file.writeText(content)
            showExportSuccess(context, file)
        } catch (e: Exception) {
            Log.e(TAG, "Export DOCX failed", e)
            Toast.makeText(context, "Xuất DOCX thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportPdf(context: Context, meeting: MeetingEntity, file: File) {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paint = Paint()
            paint.color = Color.BLACK
            paint.textSize = 14f

            val titlePaint = Paint()
            titlePaint.color = Color.parseColor("#1D4ED8")
            titlePaint.textSize = 20f
            titlePaint.isFakeBoldText = true

            var y = 50f
            canvas.drawText("BÁO CÁO CUỘC HỌP", 50f, y, titlePaint)
            y += 30f

            paint.textSize = 14f
            paint.isFakeBoldText = true
            canvas.drawText("Tên cuộc họp: ${meeting.title}", 50f, y, paint)
            y += 20f

            paint.isFakeBoldText = false
            canvas.drawText("Thời gian: ${FormatUtils.formatDate(meeting.timestamp)} (${FormatUtils.formatDuration(meeting.durationMs)})", 50f, y, paint)
            y += 20f
            canvas.drawText("Ngôn ngữ: ${if (meeting.language == "vi") "Tiếng Việt" else "English"} | Model: ${meeting.asrModel} + ${meeting.llmModel}", 50f, y, paint)
            y += 30f

            titlePaint.textSize = 16f
            canvas.drawText("1. TÓM TẮT CUỘC HỌP", 50f, y, titlePaint)
            y += 25f

            paint.textSize = 12f
            val summaryLines = meeting.summary.split("\n")
            for (line in summaryLines) {
                val wrapped = wrapText(line, 80)
                for (wLine in wrapped) {
                    if (y > 780f) break
                    canvas.drawText(wLine, 50f, y, paint)
                    y += 18f
                }
            }
            y += 20f

            if (y < 780f) {
                canvas.drawText("2. HÀNH ĐỘNG TIẾP THEO", 50f, y, titlePaint)
                y += 25f
                val actions = ActionItem.fromJson(meeting.actionItemsJson)
                for ((idx, act) in actions.withIndex()) {
                    if (y > 780f) break
                    val actStr = "${idx + 1}. ${act.task} [Phụ trách: ${act.assignee} | Deadline: ${act.deadline}]"
                    canvas.drawText(actStr, 50f, y, paint)
                    y += 20f
                }
            }

            pdfDocument.finishPage(page)

            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()

            showExportSuccess(context, file)
        } catch (e: Exception) {
            Log.e(TAG, "Export PDF failed", e)
            exportTxt(context, meeting, file)
        }
    }

    private fun buildTextContent(meeting: MeetingEntity): String {
        val sb = StringBuilder()
        sb.append("=========================================\n")
        sb.append("BÁO CÁO CUỘC HỌP\n")
        sb.append("=========================================\n\n")
        sb.append("Tên cuộc họp: ").append(meeting.title).append("\n")
        sb.append("Thời gian: ").append(FormatUtils.formatDate(meeting.timestamp)).append(" (").append(FormatUtils.formatDuration(meeting.durationMs)).append(")\n")
        sb.append("Số từ: ").append(FormatUtils.formatWordCount(meeting.wordCount, meeting.language)).append("\n")
        sb.append("Mô hình AI: ").append(meeting.asrModel).append(" + ").append(meeting.llmModel).append("\n\n")
        sb.append("-----------------------------------------\n")
        sb.append("1. TÓM TẮT CUỘC HỌP\n")
        sb.append("-----------------------------------------\n")
        sb.append(meeting.summary).append("\n\n")
        sb.append("-----------------------------------------\n")
        sb.append("2. HÀNH ĐỘNG TIẾP THEO\n")
        sb.append("-----------------------------------------\n")
        val actions = ActionItem.fromJson(meeting.actionItemsJson)
        for ((idx, act) in actions.withIndex()) {
            sb.append("${idx + 1}. ").append(act.task).append("\n")
                .append("   - Phụ trách: ").append(act.assignee).append("\n")
                .append("   - Deadline: ").append(act.deadline).append("\n\n")
        }
        sb.append("-----------------------------------------\n")
        sb.append("3. TRANSCRIPT ĐÃ SỬA LỖI\n")
        sb.append("-----------------------------------------\n")
        sb.append(meeting.correctedTranscript.ifBlank { meeting.rawTranscript }).append("\n")
        return sb.toString()
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val result = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = ""
        for (w in words) {
            if ((currentLine + " " + w).length > maxChars) {
                result.add(currentLine)
                currentLine = w
            } else {
                currentLine = if (currentLine.isEmpty()) w else "$currentLine $w"
            }
        }
        if (currentLine.isNotEmpty()) result.add(currentLine)
        return result
    }

    private fun showExportSuccess(context: Context, file: File) {
        val msg = "Đã xuất báo cáo tại:\n${file.absolutePath}"
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
}