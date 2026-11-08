package com.skyboys.personalfinance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.skyboys.personalfinance.data.FinanceTransaction
import com.skyboys.personalfinance.reports.FinanceAnalytics
import java.util.Calendar

private enum class ReportChart { BAR, LINE, DONUT }

@Composable
fun AdvancedReports(modifier: Modifier = Modifier, transactions: List<FinanceTransaction>) {
    var chart by remember { mutableStateOf(ReportChart.BAR) }
    var months by remember { mutableIntStateOf(6) }
    val trend = remember(transactions, months) { FinanceAnalytics.monthlyTrend(transactions, months) }
    val summary = remember(transactions) { FinanceAnalytics.summarize(transactions) }
    val categories = summary.expenseByCategory.entries.toList()
    val maxValue = (trend.flatMap { listOf(it.incomePaise, it.expensePaise) }.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
    val incomeColor = MaterialTheme.colorScheme.primary
    val expenseColor = MaterialTheme.colorScheme.error
    val donutColor = MaterialTheme.colorScheme.tertiary

    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Financial reports", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(3,6,12).forEach { n -> FilterChip(months==n,{months=n},label={Text("${n}M")}) }
        }
        Card(shape=RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Income vs expenses",fontWeight=FontWeight.SemiBold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ReportChart.values().forEach { kind -> FilterChip(chart==kind,{chart=kind},label={Text(kind.name.lowercase().replaceFirstChar{it.uppercase()})}) }
                }
                Canvas(Modifier.fillMaxWidth().height(210.dp)) {
                    val bottom=size.height-24.dp.toPx()
                    val top=12.dp.toPx()
                    val usable=bottom-top
                    when(chart) {
                        ReportChart.BAR -> {
                            val groupWidth=size.width/trend.size.coerceAtLeast(1)
                            trend.forEachIndexed { i, m ->
                                val center=groupWidth*(i+.5f)
                                val barW=groupWidth*.22f
                                val h1=usable*m.incomePaise.toFloat()/maxValue
                                val h2=usable*m.expensePaise.toFloat()/maxValue
                                drawRoundRect(incomeColor,Offset(center-barW-2.dp.toPx(),bottom-h1),androidx.compose.ui.geometry.Size(barW,h1),cornerRadius=androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                                drawRoundRect(expenseColor,Offset(center+2.dp.toPx(),bottom-h2),androidx.compose.ui.geometry.Size(barW,h2),cornerRadius=androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                            }
                        }
                        ReportChart.LINE -> {
                            fun drawSeries(values: List<Long>, color: androidx.compose.ui.graphics.Color) {
                                val path=Path()
                                values.forEachIndexed { i,v ->
                                    val point=Offset((i.toFloat()/(values.size-1).coerceAtLeast(1))*size.width,bottom-usable*v.toFloat()/maxValue)
                                    if(i==0) path.moveTo(point.x,point.y) else path.lineTo(point.x,point.y)
                                    drawCircle(color,4.dp.toPx(),point)
                                }
                                drawPath(path,color,style=Stroke(width=3.dp.toPx(),cap=StrokeCap.Round))
                            }
                            drawSeries(trend.map{it.incomePaise},incomeColor)
                            drawSeries(trend.map{it.expensePaise},expenseColor)
                        }
                        ReportChart.DONUT -> {
                            val total=categories.sumOf{it.value}.coerceAtLeast(1L).toFloat()
                            var angle=-90f
                            categories.take(8).forEachIndexed { i, entry ->
                                val sweep=360f*entry.value/total
                                drawArc(if(i%2==0) incomeColor else donutColor,angle,sweep,false,
                                    topLeft=Offset((size.width-size.height*.82f)/2,(size.height-size.height*.82f)/2),
                                    size=androidx.compose.ui.geometry.Size(size.height*.82f,size.height*.82f),
                                    style=Stroke(width=28.dp.toPx(),cap=StrokeCap.Butt))
                                angle+=sweep
                            }
                        }
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("● Income",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelSmall)
                    Text("● Expenses",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
                }
                if(chart==ReportChart.DONUT) categories.take(8).forEach { Text("${it.key}: ₹"+String.format(java.util.Locale("en","IN"),"%,.2f",it.value/100.0),style=MaterialTheme.typography.bodySmall) }
                else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { trend.forEach { Text(it.monthLabel,style=MaterialTheme.typography.labelSmall) } }
            }
        }
        Card(shape=RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("This month",fontWeight=FontWeight.SemiBold)
                Text("Income  ₹"+String.format(java.util.Locale("en","IN"),"%,.2f",summary.incomePaise/100.0))
                Text("Expenses  ₹"+String.format(java.util.Locale("en","IN"),"%,.2f",summary.expensePaise/100.0))
                Text("Net cash flow  ₹"+String.format(java.util.Locale("en","IN"),"%,.2f",summary.netCashFlowPaise/100.0),fontWeight=FontWeight.Bold)
            }
        }
    }
}
