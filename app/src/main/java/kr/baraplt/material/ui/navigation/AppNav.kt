package kr.baraplt.material.ui.navigation

sealed class Dest(val route: String, val label: String) {
    data object Home : Dest("home", "홈")
    data object Materials : Dest("materials", "자재")
    data object Production : Dest("production", "생산")
    data object Report : Dest("report", "실적")
    data object More : Dest("more", "더보기")
}

val bottomDestinations = listOf(Dest.Home, Dest.Materials, Dest.Production, Dest.Report, Dest.More)

object Routes {
    const val PRODUCTS = "products"
    const val MATERIAL_EDIT = "material_edit"
    const val MATERIAL_DETAIL = "material_detail/{id}"
    const val PRODUCT_EDIT = "product_edit"
    const val PRODUCT_DETAIL = "product_detail/{id}"
    const val FINISHED = "finished"
    const val FINISHED_EDIT = "finished_edit"
    const val HISTORY = "history"
    const val STOCKTAKE = "stocktake"
    const val SETTINGS = "settings"
    const val USER_GUIDE = "user_guide"
    const val MOVEMENT = "movement/{type}"

    fun materialDetail(id: Long) = "material_detail/$id"
    fun productDetail(id: Long) = "product_detail/$id"
    fun movement(type: String) = "movement/$type"
}
