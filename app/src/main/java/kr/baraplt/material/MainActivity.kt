package kr.baraplt.material

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kr.baraplt.material.ui.AppViewModel
import kr.baraplt.material.ui.components.BannerAd
import kr.baraplt.material.ui.finished.FinishedEditScreen
import kr.baraplt.material.ui.finished.FinishedListScreen
import kr.baraplt.material.ui.finished.FinishedOutputScreen
import kr.baraplt.material.ui.history.HistoryScreen
import kr.baraplt.material.ui.home.HomeScreen
import kr.baraplt.material.ui.material.MaterialDetailScreen
import kr.baraplt.material.ui.material.MaterialEditScreen
import kr.baraplt.material.ui.material.MaterialListScreen
import kr.baraplt.material.ui.material.MovementScreen
import kr.baraplt.material.ui.navigation.Dest
import kr.baraplt.material.ui.navigation.Routes
import kr.baraplt.material.ui.navigation.bottomDestinations
import kr.baraplt.material.ui.product.ProductDetailScreen
import kr.baraplt.material.ui.product.ProductEditScreen
import kr.baraplt.material.ui.production.ProductionScreen
import kr.baraplt.material.ui.report.ReportScreen
import kr.baraplt.material.ui.settings.MonthCloseScreen
import kr.baraplt.material.ui.settings.MoreScreen
import kr.baraplt.material.ui.settings.SettingsScreen
import kr.baraplt.material.ui.settings.UserGuideScreen
import kr.baraplt.material.ui.settings.WorkspaceSetupScreen
import kr.baraplt.material.ui.stocktake.StocktakeScreen
import kr.baraplt.material.ui.theme.MaterialMgmtTheme
import kr.baraplt.material.update.InAppUpdateCoordinator

class MainActivity : ComponentActivity() {

    private lateinit var inAppUpdate: InAppUpdateCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        inAppUpdate = InAppUpdateCoordinator(this)
        inAppUpdate.register()
        setContent {
            MaterialMgmtTheme {
                MaterialAppRoot()
            }
        }
        inAppUpdate.start()
    }

    override fun onResume() {
        super.onResume()
        if (::inAppUpdate.isInitialized) {
            inAppUpdate.onResume()
        }
    }

    override fun onDestroy() {
        if (::inAppUpdate.isInitialized) {
            inAppUpdate.onDestroy()
        }
        super.onDestroy()
    }
}

@Composable
private fun MaterialAppRoot(vm: AppViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val labels by vm.screenLabels.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var productionPick by remember { mutableStateOf<Long?>(null) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottom = bottomDestinations.any { it.route == route }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.consumeMessage()
    }

    if (state.ready && (state.needsWorkspace || state.needsWorkspacePassword)) {
        WorkspaceSetupScreen(vm, presetId = if (state.needsWorkspacePassword) state.workspaceId else "")
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
            if (showBottom) {
                NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                    bottomDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = route == dest.route,
                            onClick = {
                                nav.navigate(dest.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    when (dest) {
                                        Dest.Home -> Icons.Default.Home
                                        Dest.Materials -> Icons.Default.Inventory2
                                        Dest.Production -> Icons.Default.Factory
                                        Dest.Report -> Icons.Default.Assessment
                                        Dest.More -> Icons.Default.MoreHoriz
                                    },
                                    contentDescription = dest.label
                                )
                            },
                            label = {
                                val sub = when (dest) {
                                    Dest.Materials -> labels.materials
                                    Dest.Production -> labels.production
                                    Dest.Report -> labels.report
                                    else -> ""
                                }
                                if (sub.isBlank()) {
                                    Text(dest.label)
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(dest.label)
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
            if (state.ready) BannerAd()
            }
        }
    ) { padding ->
        if (!state.ready) {
            Box(Modifier.padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        NavHost(
            navController = nav,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Home.route) {
                HomeScreen(
                    state = state,
                    titleSub = labels.title,
                    onPrevMonth = { vm.shiftMonth(-1) },
                    onNextMonth = { vm.shiftMonth(1) },
                    onInbound = { nav.navigate(Routes.movement("INBOUND")) },
                    onProduction = { nav.navigate(Dest.Production.route) },
                    onFinishedOutput = { nav.navigate(Routes.FINISHED_OUTPUT) },
                    onScrap = { nav.navigate(Routes.movement("SCRAP")) },
                    onStocktake = { nav.navigate(Routes.STOCKTAKE) },
                    onMaterial = { nav.navigate(Routes.materialDetail(it)) },
                    onProduct = { nav.navigate(Routes.productDetail(it)) },
                    onSync = { vm.syncNow() }
                )
            }
            composable(Dest.Materials.route) {
                MaterialListScreen(
                    state = state,
                    onOpen = { nav.navigate(Routes.materialDetail(it)) },
                    onAdd = { nav.navigate("${Routes.MATERIAL_EDIT}?id=") },
                    canAdd = state.role.name == "MANAGER",
                    onMessage = vm::show
                )
            }
            composable(Dest.Production.route) {
                ProductionScreen(
                    state = state,
                    vm = vm,
                    presetProductId = productionPick,
                    onPrevMonth = { vm.shiftMonth(-1) },
                    onNextMonth = { vm.shiftMonth(1) },
                    onPresetUsed = { productionPick = null }
                )
            }
            composable(Dest.Report.route) {
                ReportScreen(state, { vm.shiftMonth(-1) }, { vm.shiftMonth(1) })
            }
            composable(Dest.More.route) {
                MoreScreen(
                    state = state,
                    onProducts = { nav.navigate(Routes.PRODUCTS) },
                    onFinished = { nav.navigate(Routes.FINISHED) },
                    onHistory = { nav.navigate(Routes.HISTORY) },
                    onStocktake = { nav.navigate(Routes.STOCKTAKE) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onGuide = { nav.navigate(Routes.USER_GUIDE) },
                    onClose = { nav.navigate("month_close") }
                )
            }
            composable(
                Routes.MATERIAL_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                MaterialDetailScreen(
                    state = state,
                    vm = vm,
                    materialId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("${Routes.MATERIAL_EDIT}?id=$id") },
                    onInbound = { nav.navigate("${Routes.movement("INBOUND")}?materialId=$id") },
                    onScrap = { nav.navigate("${Routes.movement("SCRAP")}?materialId=$id") }
                )
            }
            composable(
                "${Routes.MATERIAL_EDIT}?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                MaterialEditScreen(state, vm, id) { nav.popBackStack() }
            }
            composable(
                "${Routes.MOVEMENT}?materialId={materialId}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("materialId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { entry ->
                val type = entry.arguments?.getString("type") ?: "INBOUND"
                val mid = entry.arguments?.getString("materialId")?.toLongOrNull()
                MovementScreen(state, vm, type, mid) { nav.popBackStack() }
            }
            composable(Routes.PRODUCTS) {
                kr.baraplt.material.ui.product.ProductListScreen(
                    state = state,
                    onOpen = { nav.navigate(Routes.productDetail(it)) },
                    onAdd = { nav.navigate("${Routes.PRODUCT_EDIT}?id=") },
                    onBack = { nav.popBackStack() },
                    onMessage = vm::show
                )
            }
            composable(
                Routes.PRODUCT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                ProductDetailScreen(
                    state = state,
                    vm = vm,
                    productId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("${Routes.PRODUCT_EDIT}?id=$id") },
                    onCopy = { nav.navigate("${Routes.PRODUCT_EDIT}?id=&copy=$id") },
                    onProduction = {
                        productionPick = id
                        nav.navigate(Dest.Production.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                "${Routes.PRODUCT_EDIT}?id={id}&copy={copy}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("copy") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { entry ->
                ProductEditScreen(
                    state,
                    vm,
                    entry.arguments?.getString("id")?.toLongOrNull(),
                    copyFromId = entry.arguments?.getString("copy")?.toLongOrNull()
                ) { nav.popBackStack() }
            }
            composable(Routes.FINISHED) {
                FinishedListScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onAdd = { nav.navigate("${Routes.FINISHED_EDIT}?id=") },
                    onEdit = { nav.navigate("${Routes.FINISHED_EDIT}?id=$it") },
                    onOutput = { nav.navigate(Routes.FINISHED_OUTPUT) }
                )
            }
            composable(
                "${Routes.FINISHED_EDIT}?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                FinishedEditScreen(state, vm, entry.arguments?.getString("id")?.toLongOrNull()) { nav.popBackStack() }
            }
            composable(Routes.FINISHED_OUTPUT) {
                FinishedOutputScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onPrevMonth = { vm.shiftMonth(-1) },
                    onNextMonth = { vm.shiftMonth(1) }
                )
            }
            composable(Routes.HISTORY) { HistoryScreen(state, vm) { nav.popBackStack() } }
            composable(Routes.STOCKTAKE) { StocktakeScreen(state, vm) { nav.popBackStack() } }
            composable(Routes.SETTINGS) { SettingsScreen(state, vm) { nav.popBackStack() } }
            composable(Routes.USER_GUIDE) { UserGuideScreen { nav.popBackStack() } }
            composable("month_close") { MonthCloseScreen(state, vm) { nav.popBackStack() } }
        }
    }
}
