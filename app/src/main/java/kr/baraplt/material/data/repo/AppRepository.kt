package kr.baraplt.material.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kr.baraplt.material.data.AppDatabase
import kr.baraplt.material.data.SeedData
import kr.baraplt.material.data.entity.DailyProductionEntity
import kr.baraplt.material.data.entity.FinishedCompositionEntity
import kr.baraplt.material.data.entity.FinishedGoodEntity
import kr.baraplt.material.data.entity.FinishedProductionEntity
import kr.baraplt.material.data.entity.ProductOpeningEntity
import kr.baraplt.material.data.entity.MaterialEntity
import kr.baraplt.material.data.entity.MonthCloseEntity
import kr.baraplt.material.data.entity.MonthlyPlanEntity
import kr.baraplt.material.data.entity.OpeningStockEntity
import kr.baraplt.material.data.entity.ProductBomEntity
import kr.baraplt.material.data.entity.ProductEntity
import kr.baraplt.material.data.entity.ProductPlanEntity
import kr.baraplt.material.data.entity.StockMovementEntity
import kr.baraplt.material.domain.BomImport
import kr.baraplt.material.domain.BomLine
import kr.baraplt.material.domain.FinishedSnapshot
import kr.baraplt.material.domain.FinishedPlanRollup
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
    fun observeFinishedOutput(month: YearMonthKey) = db.finishedProduction().observeMonth(month.value)
    fun observeProductOpenings(month: YearMonthKey) = db.productOpenings().observeMonth(month.value)

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
        val productOpenings = db.productOpenings().forMonth(month.value)
        val finishedOutput = db.finishedProduction().forMonth(month.value)
        val closed = db.closes().get(month.value) != null
        return assemble(
            month, materials, products, finished, bom, comps,
            prod, moves, openings, productPlans, finishedPlans, closed,
            productOpenings, finishedOutput
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
        closed: Boolean,
        productOpenings: List<ProductOpeningEntity>,
        finishedOutput: List<FinishedProductionEntity>
    ): Workspace {
        val openingMap = openings.associate { it.materialId to it.qty }
        val producedByProduct = production.groupBy { it.productId }.mapValues { e -> e.value.sumOf { it.qty } }
        val compByFinished = comps.groupBy { it.finishedGoodId }.mapValues { e -> e.value.sortedBy { it.sortOrder } }
        val finishedMade = finishedOutput.groupBy { it.finishedGoodId }.mapValues { e -> e.value.sumOf { it.qty } }
        val usedByFinished = StockCalculator.productsUsedByFinished(
            finishedMade,
            compByFinished.mapValues { e -> e.value.map { it.productId to it.qty } }
        )
        val productOpeningMap = productOpenings.associate { it.productId to it.qty }
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
                barcode = m.barcode,
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
                barcode = p.barcode,
                bom = lines,
                monthPlan = productPlans.firstOrNull { it.productId == p.id }?.qty ?: 0,
                produced = producedByProduct[p.id] ?: 0,
                safetyStock = p.safetyStock,
                opening = productOpeningMap[p.id] ?: 0,
                consumed = usedByFinished[p.id] ?: 0
            )
        }
        val productById = productSnaps.associateBy { it.id }
        val finishedSnaps = finished.map { f ->
            val lines = compByFinished[f.id].orEmpty()
            val ids = lines.map { it.productId }
            val names = ids.map { productById[it]?.name ?: "?" }
            val cost = lines.sumOf { (productById[it.productId]?.materialCost ?: 0.0) * it.qty.coerceAtLeast(1) }
            FinishedSnapshot(
                id = f.id,
                codeNo = f.codeNo,
                name = f.name,
                sellPrice = f.sellPrice,
                productIds = ids,
                productNames = names,
                monthPlan = finishedPlans.firstOrNull { it.finishedGoodId == f.id }?.qty ?: 0,
                materialCost = cost,
                productQtys = lines.map { it.qty.coerceAtLeast(1) },
                produced = finishedMade[f.id] ?: 0
            )
        }
        val refs = FinishedPlanRollup.byProduct(finishedSnaps)
        val productsWithRef = productSnaps.map { p ->
            val ref = refs[p.id]
            p.copy(finishedRefQty = ref?.qty ?: 0, finishedRefNote = ref?.sources.orEmpty())
        }
        val report = MonthReport(
            yearMonth = month,
            salesAmount = productSnaps.sumOf { it.salesAmount },
            usageAmount = productSnaps.sumOf { it.usageAmount },
            purchaseAmount = materialSnaps.sumOf { it.purchaseAmount },
            scrapCost = materialSnaps.sumOf { it.scrapCost },
            producedQty = productSnaps.sumOf { it.produced },
            inboundQty = materialSnaps.sumOf { it.inbound },
            stockAmount = materialSnaps.sumOf { it.current * it.unitPrice },
            finishedSalesAmount = finishedSnaps.sumOf { it.salesAmount },
            finishedProducedQty = finishedSnaps.sumOf { it.produced }
        )
        val required = StockCalculator.requiredFromPlans(
            finishedSnaps.associate { it.id to it.monthPlan },
            finishedSnaps.associate { f -> f.id to f.productIds.mapIndexed { i, pid -> pid to f.qtyAt(i) } },
            productSnaps.associate { it.id to it.bom.map { line -> line.materialId to line.usQty } },
            productSnaps.associate { it.id to StockCalculator.productShortfall(it.current, it.safetyStock) },
            productSnaps.associate { it.id to it.monthPlan }
        )
        val materialsWithNeed = materialSnaps.map { m -> m.copy(planNeed = required[m.id] ?: 0.0) }
        val requiredList = materialsWithNeed.mapNotNull { m ->
            val need = m.planNeed
            if (need <= 0.0 && m.status == kr.baraplt.material.domain.StockStatus.OK) return@mapNotNull null
            val shortage = (need - m.current).coerceAtLeast(0.0)
            if (shortage <= 0.0 && m.status == kr.baraplt.material.domain.StockStatus.OK) return@mapNotNull null
            RequiredMaterial(m.id, m.codeNo, m.name, m.unit, m.current, need, shortage, m.leadTimeDays)
        }.sortedByDescending { it.shortage }

        return Workspace(
            month = month,
            closed = closed,
            materials = materialsWithNeed,
            products = productsWithRef,
            finished = finishedSnaps,
            production = production,
            movements = movements,
            report = report,
            required = requiredList,
            finishedOutput = finishedOutput
        )
    }

    suspend fun isClosed(month: YearMonthKey): Boolean = db.closes().get(month.value) != null

    suspend fun nextMaterialNo(): Int = (db.materials().maxCode() + 1).coerceAtMost(500)
    suspend fun nextProductNo(): Int = (db.products().maxCode() + 1).coerceAtMost(500)
    suspend fun nextFinishedNo(): Int = (db.finished().maxCode() + 1).coerceAtMost(500)

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
        db.productOpenings().deleteForProduct(id)
        db.products().deleteById(id)
        return null
    }

    suspend fun deleteFinished(id: Long): String? {
        if (db.finishedProduction().countForFinished(id) > 0) return "완성품 실적을 먼저 지우세요"
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

    suspend fun planBomImport(rows: List<List<String>>): BomImport.Plan = withContext(Dispatchers.IO) {
        val materials = db.materials().getAll()
        val materialCode = materials.associate { it.id to it.codeNo }
        val bomByProduct = db.bom().getAll().groupBy { it.productId }
        val products = db.products().getAll().map { p ->
            BomImport.ProductRef(
                id = p.id,
                codeNo = p.codeNo,
                bom = bomByProduct[p.id].orEmpty().sortedBy { it.sortOrder }.map {
                    BomImport.Line(it.materialId, materialCode[it.materialId] ?: -1, it.usQty)
                }
            )
        }
        BomImport.plan(rows, products, materials.associate { it.codeNo to it.id })
    }

    suspend fun applyBomImport(changes: List<BomImport.Change>) = db.withTransaction {
        changes.forEach { change ->
            db.bom().deleteForProduct(change.productId)
            if (change.lines.isNotEmpty()) {
                db.bom().upsertAll(
                    change.lines.mapIndexed { i, line ->
                        ProductBomEntity(productId = change.productId, materialId = line.materialId, usQty = line.usQty, sortOrder = i)
                    }
                )
            }
        }
    }

    suspend fun saveFinished(item: FinishedGoodEntity, lines: List<Pair<Long, Int>>): Long {
        val id = if (item.id == 0L) db.finished().insert(item) else {
            db.finished().update(item.copy(updatedAt = System.currentTimeMillis()))
            item.id
        }
        db.composition().deleteForFinished(id)
        if (lines.isNotEmpty()) {
            db.composition().upsertAll(
                lines.mapIndexed { i, (pid, qty) ->
                    FinishedCompositionEntity(finishedGoodId = id, productId = pid, sortOrder = i, qty = qty.coerceAtLeast(1))
                }
            )
        }
        return id
    }

    suspend fun setFinishedProduction(finishedId: Long, workDate: String, qty: Int) {
        if (qty <= 0) db.finishedProduction().delete(finishedId, workDate)
        else db.finishedProduction().upsert(FinishedProductionEntity(finishedGoodId = finishedId, workDate = workDate, qty = qty))
    }

    suspend fun setProductOpening(productId: Long, month: YearMonthKey, qty: Int) {
        db.productOpenings().upsert(ProductOpeningEntity(productId = productId, yearMonth = month.value, qty = qty))
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
        recordFinishedPlansOnProducts(month)
    }

    suspend fun recordFinishedPlansOnProducts(month: YearMonthKey) {
        val plans = db.monthlyPlans().forMonth(month.value).associate { it.finishedGoodId to it.qty }
        val comps = db.composition().getAll().groupBy { it.finishedGoodId }
        val sum = mutableMapOf<Long, Int>()
        val touched = mutableSetOf<Long>()
        comps.forEach { (finishedId, lines) ->
            val unique = lines.distinctBy { it.productId }
            touched.addAll(unique.map { it.productId })
            val qty = plans[finishedId] ?: 0
            if (qty <= 0) return@forEach
            unique.forEach { line ->
                sum[line.productId] = (sum[line.productId] ?: 0) + qty * line.qty.coerceAtLeast(1)
            }
        }
        touched.forEach { productId ->
            setProductPlan(productId, month, sum[productId] ?: 0)
        }
    }

    suspend fun setOpening(materialId: Long, month: YearMonthKey, qty: Double) {
        db.openings().upsert(OpeningStockEntity(materialId = materialId, yearMonth = month.value, qty = qty))
    }

    suspend fun applyStocktake(
        month: YearMonthKey,
        counts: Map<Long, Double>,
        notes: Map<Long, String>,
        staffName: String,
        date: String
    ) {
        val workspace = buildWorkspace(month)
        counts.forEach { (materialId, physical) ->
            val snap = workspace.materials.firstOrNull { it.id == materialId } ?: return@forEach
            val diff = physical - snap.current
            if (kotlin.math.abs(diff) < 0.0001) return@forEach
            val reason = notes[materialId]?.trim().orEmpty()
            db.movements().insert(
                StockMovementEntity(
                    materialId = materialId,
                    type = MovementType.ADJUST.name,
                    qty = diff,
                    unitPrice = snap.unitPrice,
                    occurredOn = date,
                    note = if (reason.isBlank()) "재고조사 조정" else "재고조사 조정 · $reason",
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
        workspace.products.forEach { p ->
            db.productOpenings().upsert(
                ProductOpeningEntity(productId = p.id, yearMonth = next.value, qty = p.current)
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
        closes = db.closes().getAll(),
        productOpenings = db.productOpenings().getAll(),
        finishedProduction = db.finishedProduction().getAll()
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
        if (bundle.productOpenings.isNotEmpty()) db.productOpenings().upsertAll(bundle.productOpenings)
        if (bundle.finishedProduction.isNotEmpty()) db.finishedProduction().upsertAll(bundle.finishedProduction)
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
    val required: List<RequiredMaterial>,
    val finishedOutput: List<FinishedProductionEntity> = emptyList()
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
    val closes: List<MonthCloseEntity> = emptyList(),
    val productOpenings: List<ProductOpeningEntity> = emptyList(),
    val finishedProduction: List<FinishedProductionEntity> = emptyList()
)
