package com.example.privatevault.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 看起来完全正常的简易计算器。
 * 监听按键序列,匹配到 "123+456=" 时触发 onSecretUnlocked。
 */
@Composable
fun CalculatorScreen(onSecretUnlocked: () -> Unit) {
    var display by remember { mutableStateOf("0") }
    var pending by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<String?>(null) }
    var resetOnNext by remember { mutableStateOf(false) }
    // 用于触发解锁的"按键尾序列"
    val keyBuffer = remember { StringBuilder() }

    fun tapDigit(d: String) {
        if (resetOnNext) { display = "0"; resetOnNext = false }
        display = if (display == "0") d else display + d
        keyBuffer.append(d)
    }

    fun tapDot() {
        if (resetOnNext) { display = "0"; resetOnNext = false }
        if (!display.contains(".")) display += "."
    }

    fun tapOp(o: String) {
        val cur = display.toDoubleOrNull() ?: 0.0
        if (pending != null && op != null && !resetOnNext) {
            val r = apply(pending!!, cur, op!!)
            pending = r
            display = format(r)
        } else {
            pending = cur
        }
        op = o
        resetOnNext = true
        // 操作符也算入按键尾序列,例如 "1+2+3=" 都计数
        keyBuffer.append(o)
    }

    fun tapEq() {
        val cur = display.toDoubleOrNull() ?: 0.0
        val r = if (pending != null && op != null) apply(pending!!, cur, op!!) else cur
        display = format(r)
        pending = null
        op = null
        resetOnNext = true
        keyBuffer.append("=")
        // 触发条件
        if (keyBuffer.toString().endsWith("123+456=")) {
            keyBuffer.clear()
            onSecretUnlocked()
        }
    }

    fun tapClear() {
        display = "0"; pending = null; op = null; resetOnNext = false
        keyBuffer.clear()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF4F6F8)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 24.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFEFF1F4))
                    .padding(20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = display,
                    fontSize = 44.sp,
                    color = Color(0xFF202124),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                )
            }
            Spacer(Modifier.height(16.dp))
            val rows = listOf(
                listOf("C", "±", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=", " ")
            )
            rows.forEachIndexed { idx, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (idx == rows.lastIndex) {
                        CalcKey("0", Modifier.weight(2f).aspectRatio(2.1f)) { tapDigit("0") }
                        CalcKey(".", Modifier.weight(1f).aspectRatio(1f)) { tapDot() }
                        CalcKey("=", Modifier.weight(1f).aspectRatio(1f), isAction = true) { tapEq() }
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        row.forEach { label ->
                            val isAction = label in setOf("÷", "×", "−", "+") || label == "="
                            val isUtil = label in setOf("C", "±", "%")
                            when (label) {
                                "C" -> CalcKey(label, Modifier.weight(1f).aspectRatio(1f), isUtil) { tapClear() }
                                "±", "%" -> CalcKey(label, Modifier.weight(1f).aspectRatio(1f), isUtil) { /* no-op */ }
                                "÷" -> CalcKey(label, Modifier.weight(1f).aspectRatio(1f), isAction) { tapOp("/") }
                                "×" -> CalcKey(label, Modifier.weight(1f).aspectRatio(1f), isAction) { tapOp("*") }
                                "−" -> CalcKey(label, Modifier.weight(1f).aspectRatio(1f), isAction) { tapOp("-") }
                                "+" -> CalcKey(label, Modifier.weight(1f).aspectRatio(1f), isAction) { tapOp("+") }
                                else -> CalcKey(label, Modifier.weight(1f).aspectRatio(1f)) { tapDigit(label) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun apply(a: Double, b: Double, op: String): Double = when (op) {
    "+" -> a + b
    "-" -> a - b
    "*" -> a * b
    "/" -> if (b == 0.0) 0.0 else a / b
    else -> b
}

private fun format(v: Double): String {
    if (v == v.toLong().toDouble()) return v.toLong().toString()
    return v.toString().take(12)
}

@Composable
private fun CalcKey(
    label: String,
    modifier: Modifier = Modifier,
    isAction: Boolean = false,
    isUtil: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        isAction -> Color(0xFFFFA000)
        isUtil -> Color(0xFFE9ECF1)
        else -> Color.White
    }
    val fg = if (isAction) Color.White else Color(0xFF202124)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp)),
        color = bg,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, fontSize = 26.sp, color = fg, fontWeight = FontWeight.Medium)
        }
    }
}
