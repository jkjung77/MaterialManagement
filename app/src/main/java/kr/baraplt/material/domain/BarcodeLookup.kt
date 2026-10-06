package kr.baraplt.material.domain

object BarcodeLookup {
    fun <T> pick(scanned: String, items: List<T>, barcodeOf: (T) -> String, codeOf: (T) -> Int): T? {
        val code = scanned.trim()
        if (code.isEmpty()) return null
        val byBarcode = items.filter { barcodeOf(it).trim().equals(code, true) }
        if (byBarcode.isNotEmpty()) return byBarcode.singleOrNull()
        val number = code.toIntOrNull() ?: return null
        return items.filter { codeOf(it) == number }.singleOrNull()
    }
}
