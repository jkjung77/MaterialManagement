package kr.baraplt.material.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kr.baraplt.material.data.AppDatabase
import kr.baraplt.material.data.SeedData
import kr.baraplt.material.data.entity.DailyProductionEntity
import kr.baraplt.material.data.entity.FinishedCompositionEntity
import kr.baraplt.material.data.entity.FinishedGoodEntity
import kr.baraplt.material.data.entity.MaterialEntity
import kr.baraplt.material.data.entity.MonthCloseEntity
import kr.baraplt.material.data.entity.MonthlyPlanEntity
import kr.baraplt.material.data.entity.OpeningStockEntity
import kr.baraplt.material.data.entity.ProductBomEntity
import kr.baraplt.material.data.entity.ProductEntity
import kr.baraplt.material.data.entity.ProductPlanEntity
import kr.baraplt.material.data.entity.StockMovementEntity
import kr.baraplt.material.domain.BomLine
import kr.baraplt.material.domain.FinishedSnapshot
import kr.baraplt.material.domain.MaterialDeletePolicy
import kr.baraplt.material.domain.ProductDeletePolicy
import kr.baraplt.material.domain.MaterialSnapshot
import kr.baraplt.material.domain.MonthReport
import kr.baraplt.material.domain.MovementType
import kr.baraplt.material.domain.ProductSnapshot
import kr.baraplt.material.domain.RequiredMaterial
import kr.baraplt.material.domain.StockCalculator
import kr.baraplt.material.domain.YearMonthKey

class AppRepository(private val db: AppDatabase) {

    fun observeMaterials() = db.materials().observeActive()
    fun observeProducts() = db.products().observeActive()
    fun observeFinished() = db.finished().observeActive()
    fun observeBom() = db.bom().observeAll()
    fun observeComposition() = db.composition().observeAll()
    fun observeMovements() = db.movements().observeAll()
    fun observeMonthMovements(month: YearMonthKey) = db.movements().observeMonth(month.value)
    fun observeProduction(month: YearMonthKey) = db.production().observeMonth(month.value)
    fun observeCloses() = db.closes().observeAll()
    fun observeFinishedPlans(month: YearMonthKey) = db.monthlyPlans().observeMonth(month.value)

    suspend fun buildWorkspace(month: YearMonthKey): Workspace {
        val materials = db.materials().getAll().filter { it.isActive }
        val products = db.products().getAll().filter { it.isActive }
        val finished = db.finished().getAll().filter { it.isActive }
        val bom = db.bom().getAll()
        val comps = db.composition().getAll()
        val prod = db.production().forMonth(month.value)
        val moves = db.movements().forMonth(month.value)
        val openings = db.openings().forMonth(month.value)
        val productPlans = db.productPlans().forMonth(month.value)
        val finishedPlans = db.monthlyPlans().forMonth(month.value)
        val closed = db.closes().get(month.value) != null
        return assemble(
            month, materials, products, finished, bom, comps,
            prod, moves, openings, productPlans, finishedPlans, closed
        )
    }

    suspend fun snapshotFrom(inputs: WorkspaceInputs, month: YearMonthKey): Workspace {
        val openings = db.openings().forMonth(month.value)
        val productPlans = db.productPlans().forMonth(month.value)
        val finishedPlans = db.monthlyPlans().forMonth(month.value)
        val closed = inputs.closes.any { it.yearMonth == month.value }
        return assemble(
            month, inputs.materials, inputs.products, inputs.finished,
            inputs.bom, inputs.comps, inputs.production, inputs.movements,
            openings, productPlans, finishedPlans, closed
        )
    }

    private fun assemble(
        month: YearMonthKey,
        materials: List<MaterialEntity>,
        products: List<ProductEntity>,
        finished: List<FinishedGoodEntity>,
        bom: List<ProductBomEntity>,
        comps: List<FinishedCompositionEntity>,
        production: List<DailyProductionEntity>,
        movements: List<StockMovementEntity>,
        openings: List<OpeningStockEntity>,
        productPlans: List<ProductPlanEntity>,
        finishedPlans: List<MonthlyPlanEntity>,
        closed: Boolean
    ): Workspace {
        val openingMap = openings.associate { it.materialId to it.qty }
        val producedByProduct = production.groupBy { it.productId }.mapValues { e -> e.value.sumOf { it.qty } }
        val bomByProduct = bom.groupBy { it.productId }
        val usage = StockCalculator.explodeUsage(
            producedByProduct,
            bomByProduct.mapValues { e -> e.value.map { it.materialId to it.usQty } }
        )
        val materialSnaps = materials.map { m ->
            val monthMoves = movements.filter { it.materialId == m.id }
            fun sum(type: MovementType) = monthMoves.filter { it.type == type.name }.sumOf { it.qty }
            val inboundMoves = monthMoves.filter { it.type == MovementType.INBOUND.name }
            val scrapMoves = monthMoves.filter { it.type == MovementType.SCRAP.name }
            MaterialSnapshot(
                id = m.id,
                codeNo = m.codeNo,
                name = m.name,
                unit = m.unit,
                packUnit = m.packUnit,
                unitPrice = m.unitPrice,
                safetyStock = m.safetyStock,
                leadTimeDays = m.leadTimeDays,
                opening = openingMap[m.id] ?: 0.0,
                inbound = sum(MovementType.INBOUND),
                outbound = sum(MovementType.OUTBOUND),
                scrap = sum(MovementType.SCRAP),
                adjust = sum(MovementType.ADJUST),
                usage = usage[m.id] ?: 0.0,
                purchaseAmount = inboundMoves.sumOf { it.qty * it.unitPrice },
                scrapCost = scrapMoves.sumOf { it.qty * it.unitPrice }
            )
        }
        val materialById = materials.associateBy { it.id }
        val productSnaps = products.map { p ->
            val lines = bomByProduct[p.id].orEmpty().sortedBy { it.sortOrder }.mapNotNull { line ->
                val mat = materialById[line.materialId] ?: return@mapNotNull null
                BomLine(mat.id, mat.codeNo, mat.name, mat.unit, mat.unitPrice, line.usQty)
            }
            ProductSnapshot(
                id = p.id,
                codeNo = p.codeNo,
                name = p.name,
                sellPrice = p.sellPrice,
                bom = lines,
                monthPlan = productPlans.firstOrNull { it.productId == p.id }?.qty ?: 0,
                produced = producedByProduct[p.id] ?: 0
            )
        }
        val productById = productSnaps.associateBy { it.id }
        val finishedSnaps = finished.map { f ->
            val ids = comps.filter { it.finishedGoodId == f.id }.sortedBy { it.sortOrder }.map { it.productId }
            val names = ids.map { productById[it]?.name ?: "?" }
            val cost = ids.sumOf { productById[it]?.materialCost ?: 0.0 }
            FinishedSnapshot(
                id = f.id,
                codeNo = f.codeNo,
                name = f.name,
                sellPrice = f.sellPrice,
                productIds = ids,
                productNames = names,
                monthPlan = finishedPlans.firstOrNull { it.finishedGoodId == f.id }?.qty ?: 0,
                materialCost = cost
            )
        }
        val report = MonthReport(
            yearMonth = month,
            salesAmount = productSnaps.sumOf { it.salesAmount },
            usageAmount = productSnaps.sumOf { it.usageAmount },
            purchaseAmount = materialSnaps.sumOf { it.purchaseAmount },
            scrapCost = materialSnaps.sumOf { it.scrapCost },
            producedQty = productSnaps.sumOf { it.produced },
            inboundQty = materialSnaps.sumOf { it.inbound }
        )
        val required = StockCalculator.requiredFromPlans(
            finishedSnaps.associate { it.id to it.monthPlan },
            finishedSnaps.associate { it.id to it.productIds },
            productSnaps.associate { it.id to it.bom.map { line -> line.materialId to line.usQty } }
        )
        val requiredList = materialSnaps.mapNotNull { m ->
            val need = required[m.id] ?: 0.0
            if (need <= 0.0 && m.status == kr.baraplt.material.domain.StockStatus.OK) return@mapNotNull null
            val shortage = (need - m.current).coerceAtLeast(0.0)
            if (shortage <= 0.0 && m.status == kr.baraplt.material.domain.StockStatus.OK) return@mapNotNull null
            RequiredMaterial(m.id, m.codeNo, m.name, m.unit, m.current, need, shortage, m.leadTimeDays)
        }.sortedByDescending { it.shortage }

        return Workspace(
            month = month,
            closed = closed,
            materials = materialSnaps,
            products = productSnaps,
            finished = finishedSnaps,
            production = production,
            movements = movements,
            report = report,
            required = requiredList
        )
    }

    suspend fun isClosed(month: YearMonthKey): Boolean = db.closes().get(month.value) != null

    suspend fun nextMaterialNo(): Int = (db.materials().maxCode() + 1).coerceAtMost(500)
    suspend fun nextProductNo(): Int = (db.products().maxCode() + 1).coerceAtMost(500)
    suspend fun nextFinishedNo(): Int = (db.finished().maxCode() + 1).coerceAtMost(26)

    suspend fun deleteMaterial(id: Long): String? {
        val reason = MaterialDeletePolicy.blockReason(
            db.movements().countForMaterial(id),
            db.bom().countForMaterial(id)
        )
        if (reason != null) return reason
        db.openings().deleteForMaterial(id)
        db.materials().deleteById(id)
        return null
    }

    suspend fun saveMaterial(item: MaterialEntity): Long {
        return if (item.id == 0L) db.materials().insert(item) else {
            db.materials().update(item.copy(updatedAt = System.currentTimeMillis()))
            item.id
        }
    }

    suspend fun deleteProduct(id: Long): String? {
        val reason = ProductDeletePolicy.blockReason(
            db.production().countForProduct(id),
            db.composition().countForProduct(id)
        )
        if (reason != null) return reason
        db.bom().deleteForProduct(id)
        db.productPlans().deleteForProduct(id)
        db.products().deleteById(id)
        return null
    }

    suspend fun deleteFinished(id: Long): String? {
        db.composition().deleteForFinished(id)
        db.monthlyPlans().deleteForFinished(id)
        db.finished().deleteById(id)
        return null
    }

    suspend fun saveProduct(item: ProductEntity, bom: List<ProductBomEntity>): Long {
        val id = if (item.id == 0L) db.products().insert(item) else {
            db.products().update(item.copy(updatedAt = System.currentTimeMillis()))
            item.id
        }
        db.bom().deleteForProduct(id)
        if (bom.isNotEmpty()) {
            db.bom().upsertAll(bom.mapIndexed { i, line -> line.copy(id = 0, productId = id, sortOrder = i) })
        }
        return id
    }

    suspend fun saveFinished(item: FinishedGoodEntity, productIds: List<Long>): Long {
        val id = if (item.id == 0L) db.finished().insert(item) else {
            db.finished().update(item.copy(updatedAt = System.currentTimeMillis()))
            item.id
        }
        db.composition().deleteForFinished(id)
        if (productIds.isNotEmpty()) {
            db.composition().upsertAll(
                productIds.mapIndexed { i, pid ->
                    FinishedCompositionEntity(finishedGoodId = id, productId = pid, sortOrder = i)
                }
            )
        }
        return id
    }

    suspend fun addMovement(item: StockMovementEntity) {
        db.movements().insert(item)
    }

    suspend fun deleteMovement(item: StockMovementEntity) {
        db.movements().delete(item)
    }

    suspend fun setProduction(productId: Long, workDate: String, qty: Int) {
        if (qty <= 0) db.production().delete(productId, workDate)
        else db.production().upsert(DailyProductionEntity(productId = productId, workDate = workDate, qty = qty))
    }

    suspend fun setProductPlan(productId: Long, month: YearMonthKey, qty: Int) {
        db.productPlans().upsert(ProductPlanEntity(productId = productId, yearMonth = month.value, qty = qty))
    }

    suspend fun setFinishedPlan(finishedId: Long, month: YearMonthKey, qty: Int) {
        db.monthlyPlans().upsert(MonthlyPlanEntity(finishedGoodId = finishedId, yearMonth = month.value, qty = qty))
    }

    suspend fun setOpening(materialId: Long, month: YearMonthKey, qty: Double) {
        db.openings().upsert(OpeningStockEntity(materialId = materialId, yearMonth = month.value, qty = qty))
    }

    suspend fun applyStocktake(
        month: YearMonthKey,
        counts: Map<Long, Double>,
        staffName: String,
        date: String
    ) {
        val workspace = buildWorkspace(month)
        counts.forEach { (materialId, physical) ->
            val snap = workspace.materials.firstOrNull { it.id == materialId } ?: return@forEach
            val diff = physical - snap.current
            if (kotlin.math.abs(diff) < 0.0001) return@forEach
            db.movements().insert(
                StockMovementEntity(
                    materialId = materialId,
                    type = MovementType.ADJUST.name,
                    qty = diff,
                    unitPrice = snap.unitPrice,
                    occurredOn = date,
                    note = "재고조사 조정",
                    createdBy = staffName
                )
            )
        }
    }

    suspend fun closeMonth(month: YearMonthKey, staffName: String) {
        val workspace = buildWorkspace(month)
        val next = month.next()
        workspace.materials.forEach { m ->
            db.openings().upsert(
                OpeningStockEntity(materialId = m.id, yearMonth = next.value, qty = m.current)
            )
        }
        db.closes().upsert(
            MonthCloseEntity(yearMonth = month.value, closedAt = System.currentTimeMillis(), closedBy = staffName)
        )
    }

    suspend fun reopenMonth(month: YearMonthKey) {
        db.closes().delete(month.value)
    }

    suspend fun seedSample() = withContext(Dispatchers.IO) {
        SeedData.populate(db)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
    }

    suspend fun exportBundle(): BackupBundle = withContext(Dispatchers.IO) {
        BackupBundle(
        materials = db.materials().getAll(),
        openings = db.openings().getAll(),
        movements = db.movements().getAll(),
        products = db.products().getAll(),
        bom = db.bom().getAll(),
        production = db.production().getAll(),
        productPlans = db.productPlans().getAll(),
        finished = db.finished().getAll(),
        composition = db.composition().getAll(),
        monthlyPlans = db.monthlyPlans().getAll(),
        closes = db.closes().getAll()
        )
    }

    suspend fun importBundle(bundle: BackupBundle) = withContext(Dispatchers.IO) {
        db.clearAllTables()
        if (bundle.materials.isNotEmpty()) db.materials().insertAll(bundle.materials.map { it.copy(id = 0) })
    }

    suspend fun restoreBundle(bundle: BackupBundle) = withContext(Dispatchers.IO) {
        db.clearAllTables()
        restoreWithIds(bundle)
    }

    private suspend fun restoreWithIds(bundle: BackupBundle) {
        if (bundle.materials.isNotEmpty()) db.materials().insertAll(bundle.materials)
        if (bundle.openings.isNotEmpty()) db.openings().upsertAll(bundle.openings)
        if (bundle.movements.isNotEmpty()) db.movements().insertAll(bundle.movements)
        if (bundle.products.isNotEmpty()) db.products().insertAll(bundle.products)
        if (bundle.bom.isNotEmpty()) db.bom().upsertAll(bundle.bom)
        if (bundle.production.isNotEmpty()) db.production().upsertAll(bundle.production)
        bundle.productPlans.forEach { db.productPlans().upsert(it) }
        if (bundle.finished.isNotEmpty()) db.finished().insertAll(bundle.finished)
        if (bundle.composition.isNotEmpty()) db.composition().upsertAll(bundle.composition)
        bundle.monthlyPlans.forEach { db.monthlyPlans().upsert(it) }
        bundle.closes.forEach { db.closes().upsert(it) }
    }
}

data class Workspace(
    val month: YearMonthKey,
    val closed: Boolean,
    val materials: List<MaterialSnapshot>,
    val products: List<ProductSnapshot>,
    val finished: List<FinishedSnapshot>,
    val production: List<DailyProductionEntity>,
    val movements: List<StockMovementEntity>,
    val report: MonthReport,
    val required: List<RequiredMaterial>
)

data class WorkspaceInputs(
    val materials: List<MaterialEntity>,
    val products: List<ProductEntity>,
    val finished: List<FinishedGoodEntity>,
    val bom: List<ProductBomEntity>,
    val comps: List<FinishedCompositionEntity>,
    val production: List<DailyProductionEntity>,
    val movements: List<StockMovementEntity>,
    val closes: List<MonthCloseEntity>
)

data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

data class BackupBundle(
    val materials: List<MaterialEntity> = emptyList(),
    val openings: List<OpeningStockEntity> = emptyList(),
    val movements: List<StockMovementEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val bom: List<ProductBomEntity> = emptyList(),
    val production: List<DailyProductionEntity> = emptyList(),
    val productPlans: List<ProductPlanEntity> = emptyList(),
    val finished: List<FinishedGoodEntity> = emptyList(),
    val composition: List<FinishedCompositionEntity> = emptyList(),
    val monthlyPlans: List<MonthlyPlanEntity> = emptyList(),
    val closes: List<MonthCloseEntity> = emptyList()
)
