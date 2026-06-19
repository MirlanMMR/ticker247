@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHomeScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onGoogleSignIn: () -> Unit = {}
) {
    var showSettings by remember { mutableStateOf(false) }
    var readerUrl by remember { mutableStateOf<String?>(null) }
    var readerTitle by remember { mutableStateOf("") }
    var readerSource by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    val selectedCategories = remember { mutableStateListOf<MainAppCategory>() }
    var showManualSetup by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("🌐 Auto (System)") }
    val context = LocalContext.current

    val isDark = isSystemInDarkTheme()
    val bgColor      = if (isDark) Color(0xFF0A0A0F) else Color(0xFFF2F4F8)
    val surfaceColor = if (isDark) Color(0xFF13131A) else Color(0xFFFFFFFF)
    val textColor    = if (isDark) Color(0xFFEAEAEA) else Color(0xFF1A1A2E)
    val subColor     = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
    val accentColor  = Color(0xFF00D4FF)

    val flowItems by DataBridge.newsItemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val flowTicker by DataBridge.tickerFlow.collectAsStateWithLifecycle(initialValue = "")

    val filteredItems = when (val cat = tabCategories.getOrElse(selectedTab) { "ALL" }) {
        "ALL" -> flowItems.toList()
        "URGENT" -> {
            val urgent = flowItems.filter { it.category == "URGENT" }
            if (urgent.isNotEmpty()) urgent
            else flowItems.filter { it.category in setOf("NEWS", "SPORT", "TECH") }
                .sortedByDescending { it.priority * 1_000_000L + it.publishedAt }.take(15)
        }
        else -> flowItems.filter { it.category == cat }
    }

    val featuredItems = flowItems.filter { it.priority >= 1 }.take(5)

    if (readerUrl != null) {
        BackHandler { readerUrl = null }
        ReaderScreen(url = readerUrl!!, title = readerTitle, source = readerSource,
            isDark = isDark, onBack = { readerUrl = null })
        return
    }

    if (selectedTab != 0) {
        BackHandler { selectedTab = 0 }
    }

    val isOnboardingDone by viewModel.isOnboardingDone.collectAsStateWithLifecycle(initialValue = true)

    if (isOnboardingDone == false) {
        OnboardingContent(
            selectedCategories = selectedCategories,
            showManualSetup = showManualSetup,
            selectedLanguage = selectedLanguage,
            textColor = textColor,
            surfaceColor = surfaceColor,
            bgColor = bgColor,
            subTextColor = subColor,
            onShowManualSetup = { showManualSetup = true },
            onLanguageSelected = { selectedLanguage = it },
            onAcceptRecommended = { viewModel.completeOnboarding() },
            onAcceptCustom = { viewModel.completeOnboarding() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 18.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("Ticker 24/7", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = textColor)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, null, tint = subColor, modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth().background(bgColor).padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Created by MMR®", fontSize = 11.sp, color = subColor, fontWeight = FontWeight.Light)
            }
        },
        containerColor = bgColor
    ) { padding ->

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TickerBar(text = flowTicker, bgColor = surfaceColor, accentColor = accentColor)

            if (featuredItems.isNotEmpty()) {
                FeaturedBanner(items = featuredItems, isDark = isDark,
                    textColor = textColor, accentColor = accentColor, context = context,
                    onItemClick = { item ->
                        if (item.url.isNotEmpty()) {
                            readerUrl = item.url; readerTitle = item.title; readerSource = item.source
                        }
                    })
            }

            CategoryTabs(tabs = feedTabs, selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                accentColor = accentColor, bgColor = bgColor,
                textColor = textColor, subColor = subColor)

            val sorted = filteredItems
                .distinctBy { it.url.ifEmpty { it.title } }
                .sortedByDescending { it.priority * 1000 + it.publishedAt / 1000 }

            var trendCounter = 1
            val trendIndex = mutableMapOf<String, Int>()
            sorted.forEach { item ->
                if (item.category == "TRENDS") trendIndex[item.url + item.title] = trendCounter++
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (flowItems.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = accentColor)
                                Spacer(Modifier.height(16.dp))
                                Text("Загружаем данные...", color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                items(sorted) { item ->
                    when (item.category) {
                        "CURRENCY" -> CurrencyCard(item, isDark, textColor, subColor)
                        "CRYPTO"   -> CryptoCard(item, isDark, textColor, subColor)
                        "TRENDS"   -> TrendCard(item, trendIndex[item.url + item.title] ?: 0, isDark, textColor, subColor)
                        else       -> FeedCard(item, isDark, textColor, subColor, onTap = {
                            if (item.url.isNotEmpty()) {
                                readerUrl = item.url; readerTitle = item.title; readerSource = item.source
                            }
                        })
                    }
                }
            }
        }
    }
}