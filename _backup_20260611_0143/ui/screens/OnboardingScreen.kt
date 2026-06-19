package com.mirlanmamytov.ticker247.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val BgDeep         = Color(0xFF121316)
private val BgCard         = Color(0xFF1E2025)
private val BgCardSelected = Color(0xFF252A35)
private val AccentBlue     = Color(0xFF4FC3F7)
private val TextPrimary    = Color(0xFFF2F2F2)
private val TextSecondary  = Color(0xFF8A8E99)
private val RecommendedTag = Color(0xFF1B3A5C)
private val StrokeDim      = Color(0xFF2C2F38)
private val StrokeSelected = Color(0xFF4FC3F7)

data class CategoryItem(
    val category: com.mirlanmamytov.ticker247.workmanager.Category,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val isRecommended: Boolean = false,
    val group: String
)

data class RegionOption(val code: String, val label: String, val flag: String)

private val REGIONS = listOf(
    RegionOption("KG", "Кыргызстан", "🇰🇬"),
    RegionOption("RU", "Россия",     "🇷🇺"),
    RegionOption("KZ", "Казахстан",  "🇰🇿"),
    RegionOption("US", "США",        "🇺🇸"),
)

private val ALL_CATEGORIES = listOf(
    CategoryItem(com.mirlanmamytov.ticker247.workmanager.Category.CURRENCY,       "💱", "Курсы валют",      "USD, EUR, RUB, KGS",              group = "Финансы"),
    CategoryItem(com.mirlanmamytov.ticker247.workmanager.Category.CRYPTO,         "₿",  "Криптовалюта",     "BTC и топ монеты",                group = "Финансы"),
    CategoryItem(com.mirlanmamytov.ticker247.workmanager.Category.STOCK_INDEX,    "📈", "Индексы",          "Dow Jones, S&P 500",              group = "Финансы"),
    CategoryItem(com.mirlanmamytov.ticker247.workmanager.Category.FUEL,           "⛽", "Цены на ГСМ",      "Бензин / Дизель",                 group = "Финансы"),
    CategoryItem(com.mirlanmamytov.ticker247.workmanager.Category.FOOTBALL,       "⚽", "Футбол",           "Результаты и расписание",          group = "Спорт"),
    CategoryItem(com.mirlanmamytov.ticker247.workmanager.Category.BASKETBALL,     "🏀", "Баскетбол",        "Результаты матчей",                group = "Спорт"),
    CategoryItem(com.mirlanmamytov.ticker247.workmanager.Category.BOXING,         "🥊", "Бокс / UFC",       "Результаты или анонс начала",      group = "Спорт"),
    CategoryItem(com.mirlanmamytov.ticker247.workmanager.Category.EMERGENCY,      "⚡", "Экстренные новости","Землетрясения, катаклизмы",       isRecommended = true, group = "Экстренные"),
    CategoryItem(com.mirlanmamytov.ticker247.workmanager.Category.CITY_UTILITIES, "🏙️", "Город / ЖКХ",     "Перекрытия, свет, вода",           isRecommended = true, group = "Город / ЖКХ"),
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onConfirmClick: (selectedRegion: String) -> Unit = {},
    onCloseAndRun: (selectedRegion: String) -> Unit = {}
) {
    val selectedCategories = viewModel.selectedCategories
    var selectedRegion by remember { mutableStateOf(REGIONS.first()) }
    val groups = ALL_CATEGORIES.groupBy { it.group }

    Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        Column(modifier = Modifier.fillMaxSize()) {
            OnboardingHeader()

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    RegionSelector(
                        selected = selectedRegion,
                        regions = REGIONS,
                        onSelect = { selectedRegion = it }
                    )
                }

                val groupNames = groups.keys.toList()
                groupNames.forEach { groupName ->
                    item(key = groupName) {
                        GroupHeader(title = groupName)
                    }

                    val itemsInGroup = groups[groupName] ?: emptyList()
                    items(
                        items = itemsInGroup,
                        key = { it.category.name }
                    ) { item ->
                        CategoryRow(
                            item = item,
                            isSelected = item.category in selectedCategories,
                            onToggle = { viewModel.toggleCategory(item.category) }
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            ActionButtons(
                onOpenFeed = {
                    viewModel.saveAndFinish(selectedRegion.code)
                    onConfirmClick(selectedRegion.code)
                },
                onCloseAndRun = {
                    viewModel.saveAndFinish(selectedRegion.code)
                    onCloseAndRun(selectedRegion.code)
                }
            )
        }
    }
}

@Composable
private fun RegionSelector(selected: RegionOption, regions: List<RegionOption>, onSelect: (RegionOption) -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        Text("РЕГИОН КОНТЕНТА", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            regions.forEach { region ->
                val isSelected = region.code == selected.code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) BgCardSelected else BgCard)
                        .border(1.dp, if (isSelected) StrokeSelected else StrokeDim, RoundedCornerShape(10.dp))
                        .clickable { onSelect(region) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(region.flag, fontSize = 18.sp)
                        Text(region.label, fontSize = 10.sp, color = if (isSelected) AccentBlue else TextSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeader() {
    Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF0D1117), BgDeep))).padding(horizontal = 20.dp, vertical = 28.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("24/7", fontSize = 32.sp, fontWeight = FontWeight.Black, color = AccentBlue, letterSpacing = (-1).sp)
                Spacer(Modifier.width(10.dp))
                Text("Информер", fontSize = 18.sp, fontWeight = FontWeight.Light, color = TextSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Text("Выберите регион и категории для шторки", fontSize = 14.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(text = title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp))
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun CategoryRow(item: CategoryItem, isSelected: Boolean, onToggle: () -> Unit) {
    val borderColor by animateColorAsState(targetValue = if (isSelected) StrokeSelected else StrokeDim, animationSpec = tween(200), label = "border")
    val cardBg by animateColorAsState(targetValue = if (isSelected) BgCardSelected else BgCard, animationSpec = tween(200), label = "bg")

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cardBg).border(1.dp, borderColor, RoundedCornerShape(12.dp)).clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.emoji, fontSize = 22.sp, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.isRecommended) {
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(RecommendedTag).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("Рекомендуем", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AccentBlue, letterSpacing = 0.3.sp)
                    }
                }
            }
            Text(item.subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (isSelected) AccentBlue else Color(0xFF2C2F38)).border(1.dp, if (isSelected) AccentBlue else StrokeDim, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            this@Row.AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(tween(150)) + fadeIn(),
                exit = scaleOut(tween(100)) + fadeOut()
            ) {
                Text("✓", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onOpenFeed: () -> Unit,
    onCloseAndRun: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF161921),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onOpenFeed() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color(0xFF0D1117))
            ) {
                Text(text = "Сохранить и открыть ленту", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { onCloseAndRun() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, StrokeDim),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text(text = "Ок, закрыть. Оповещайте меня!", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}