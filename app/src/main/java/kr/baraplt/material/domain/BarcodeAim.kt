package kr.baraplt.material.domain

/** 화면 픽셀 기준 사각형. */
data class AimBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

data class SeenBarcode(val value: String, val box: AimBox)

object BarcodeAim {
    /**
     * 카메라 분석 프레임(세운 방향) 좌표를 화면 좌표로 옮긴다.
     * 미리보기는 화면을 꽉 채우고 넘치는 쪽을 가운데 기준으로 자른다(FILL_CENTER).
     */
    fun toView(box: AimBox, imageW: Int, imageH: Int, viewW: Int, viewH: Int): AimBox {
        if (imageW <= 0 || imageH <= 0 || viewW <= 0 || viewH <= 0) return box
        val scale = maxOf(viewW.toFloat() / imageW, viewH.toFloat() / imageH)
        val dx = (imageW * scale - viewW) / 2f
        val dy = (imageH * scale - viewH) / 2f
        return AimBox(
            box.left * scale - dx,
            box.top * scale - dy,
            box.right * scale - dx,
            box.bottom * scale - dy
        )
    }

    /**
     * 조준 네모 안의 바코드 하나를 고른다. 십자(네모 가운데)를 덮는 바코드가 있으면 그것,
     * 없으면 가운데가 네모 안에 들어온 것 중 십자에 가장 가까운 것. 네모 밖 바코드는 버린다.
     */
    fun pick(seen: List<SeenBarcode>, aim: AimBox): SeenBarcode? {
        val inside = seen.filter { it.value.isNotBlank() && aim.contains(it.box.centerX, it.box.centerY) }
        inside.firstOrNull { it.box.contains(aim.centerX, aim.centerY) }?.let { return it }
        return inside.minByOrNull {
            val dx = it.box.centerX - aim.centerX
            val dy = it.box.centerY - aim.centerY
            dx * dx + dy * dy
        }
    }
}

/** 자동 모드: 같은 값이 연달아 [needed]번 잡혀야 확정한다. 흔들려 스친 옆 바코드를 거른다. */
class AutoConfirm(private val needed: Int = 2) {
    private var last: String? = null
    private var count = 0

    fun feed(value: String?): String? {
        if (value == null) {
            last = null
            count = 0
            return null
        }
        if (value == last) count++ else {
            last = value
            count = 1
        }
        return if (count >= needed) value else null
    }
}
