package com.albatora.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.DEFAULT_2026_MIN_WAGE
import com.albatora.app.model.UserPersona
import com.albatora.app.ui.components.MiuixGlassIcon
import com.albatora.app.ui.components.SquirclePillBadge
import com.albatora.app.ui.theme.AlbaMotion
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OnboardingScreen(
    initialPersona: UserPersona,
    initialDefaultMinWage: Long,
    onComplete: (persona: UserPersona, defaultMinWage: Long, loadSampleData: Boolean) -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var selectedPersona by remember { mutableStateOf(initialPersona) }
    var minWageText by remember { mutableStateOf(initialDefaultMinWage.toString()) }
    var loadSampleData by remember { mutableStateOf(false) }

    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top step indicator & header
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SquirclePillBadge(
                        text = if (step == 0) "STEP 1 / 2" else "STEP 2 / 2",
                        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                        contentColor = MiuixTheme.colorScheme.primary
                    )
                    Text(
                        text = "알바토라 시작하기",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }

                // Progress bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(2) { index ->
                        val active = index <= step
                        val color by animateColorAsState(
                            targetValue = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline.copy(alpha = 0.35f),
                            animationSpec = tween(220),
                            label = "step_bar_$index"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .squircleClip(4.dp)
                                .background(color)
                        )
                    }
                }
            }

            // Middle content
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(AlbaMotion.OffsetSpring) { if (forward) it / 3 else -it / 3 } +
                        fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(AlbaMotion.OffsetSpring) { if (forward) -it / 3 else it / 3 } +
                            fadeOut(tween(150)))
                },
                label = "onboarding_step"
            ) { currentStep ->
                if (currentStep == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "어떤 근무 스타일에\n가까우신가요?",
                            style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.Bold, lineHeight = 34.sp),
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "선택한 스타일에 맞춰 홈 화면 카드 순서와 빠른 입력 기본값을 자동으로 최적화합니다.",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        PersonaOptionCard(
                            persona = UserPersona.REGULAR_FOCUSED,
                            icon = MiuixIcons.Timer,
                            accentColor = Color(0xFF3482FF),
                            detailLine = "홈 상단에 주휴수당 · 월급날 D-Day 우선 배치",
                            selected = selectedPersona == UserPersona.REGULAR_FOCUSED,
                            onClick = { selectedPersona = UserPersona.REGULAR_FOCUSED }
                        )
                        PersonaOptionCard(
                            persona = UserPersona.SHORT_TERM_FREELANCE,
                            icon = MiuixIcons.Store,
                            accentColor = Color(0xFF10B981),
                            detailLine = "일당 · 일용/3.3% 공제 · 교통/식대 입력 기본 펼침",
                            selected = selectedPersona == UserPersona.SHORT_TERM_FREELANCE,
                            onClick = { selectedPersona = UserPersona.SHORT_TERM_FREELANCE }
                        )
                        PersonaOptionCard(
                            persona = UserPersona.MULTI_JOB,
                            icon = MiuixIcons.Stopwatch,
                            accentColor = Color(0xFFF59E0B),
                            detailLine = "고정 + 단기 통합 관리 · 전체 미수금 최상단 확인",
                            selected = selectedPersona == UserPersona.MULTI_JOB,
                            onClick = { selectedPersona = UserPersona.MULTI_JOB }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "${selectedPersona.label}으로\n맞춤 준비를 마칠게요",
                            style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.Bold, lineHeight = 34.sp),
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "언제든 설정에서 근무 스타일과 기본 시급을 바꿀 수 있습니다.",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )

                        SmallTitle(text = "기본 시급 기준")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                TextField(
                                    value = minWageText,
                                    onValueChange = { input ->
                                        minWageText = input.filter { it.isDigit() }.take(7)
                                    },
                                    label = "기본 시급 (원)",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "2026년 최저시급 ${PayrollCalculator.formatWon(DEFAULT_2026_MIN_WAGE)}",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                    if (minWageText.toLongOrNull() != DEFAULT_2026_MIN_WAGE) {
                                        Text(
                                            text = "최저시급 적용",
                                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .squircleClip(8.dp)
                                                .clickable { minWageText = DEFAULT_2026_MIN_WAGE.toString() }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        SmallTitle(text = "둘러보기 옵션")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { loadSampleData = !loadSampleData },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "예시 데이터로 먼저 둘러보기",
                                        style = MiuixTheme.textStyles.subtitle,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "선택한 스타일에 맞는 예시 근무·정산 기록을 함께 생성합니다",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                Switch(
                                    checked = loadSampleData,
                                    onCheckedChange = { loadSampleData = it }
                                )
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            SmallTitle(text = "실시간 타이머 알림 (선택)")
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MiuixGlassIcon(
                                            icon = MiuixIcons.Notes,
                                            contentDescription = null,
                                            accentColor = Color(0xFF3482FF),
                                            size = 40.dp,
                                            iconSize = 20.dp,
                                            cornerRadius = 12.dp
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = if (notificationGranted) "알림 권한 허용됨" else "상단바 실시간 수익 알림",
                                                style = MiuixTheme.textStyles.subtitle,
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "근무 중 상단바에서 실시간 수익과 휴게 전환을 바로 확인합니다",
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                        }
                                    }
                                    if (!notificationGranted) {
                                        TextButton(
                                            text = "허용",
                                            onClick = {
                                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        )
                                    } else {
                                        SquirclePillBadge(
                                            text = "완료",
                                            backgroundColor = Color(0xFF10B981).copy(alpha = 0.16f),
                                            contentColor = Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom CTA bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (step == 1) {
                    TextButton(
                        text = "이전",
                        onClick = { step = 0 },
                        modifier = Modifier.weight(0.36f)
                    )
                }
                TextButton(
                    text = if (step == 0) "다음" else "알바토라 시작하기",
                    onClick = {
                        if (step == 0) {
                            step = 1
                        } else {
                            val parsedWage = minWageText.toLongOrNull()?.coerceAtLeast(1L) ?: DEFAULT_2026_MIN_WAGE
                            onComplete(selectedPersona, parsedWage, loadSampleData)
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PersonaOptionCard(
    persona: UserPersona,
    icon: ImageVector,
    accentColor: Color,
    detailLine: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        accentColor.copy(alpha = 0.11f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val borderColor = if (selected) {
        accentColor.copy(alpha = 0.65f)
    } else {
        MiuixTheme.colorScheme.outline.copy(alpha = 0.18f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .squircleClip(20.dp)
            .squircleBackground(color = containerColor, cornerRadius = 20.dp)
            .squircleBorder(width = if (selected) 1.5.dp else 1.dp, color = borderColor, cornerRadius = 20.dp)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixGlassIcon(
                icon = icon,
                contentDescription = null,
                accentColor = accentColor,
                size = 46.dp,
                iconSize = 22.dp,
                cornerRadius = 14.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = persona.label,
                        style = MiuixTheme.textStyles.subtitle.copy(fontWeight = FontWeight.SemiBold),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    SquirclePillBadge(
                        text = persona.badge,
                        backgroundColor = accentColor.copy(alpha = 0.14f),
                        contentColor = accentColor
                    )
                }
                Text(
                    text = persona.subtitle,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = detailLine,
                    style = MiuixTheme.textStyles.footnote1,
                    color = accentColor
                )
            }
            if (selected) {
                SquirclePillBadge(
                    text = "선택",
                    backgroundColor = accentColor.copy(alpha = 0.16f),
                    contentColor = accentColor
                )
            }
        }
    }
}
