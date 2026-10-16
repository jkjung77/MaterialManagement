package kr.baraplt.material.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeAimTest {
    private val aim = AimBox(100f, 400f, 900f, 600f)

    private fun code(value: String, left: Float, top: Float, right: Float, bottom: Float) =
        SeenBarcode(value, AimBox(left, top, right, bottom))

    @Test
    fun picksBarcodeUnderCrosshairOverCloserNeighbours() {
        val seen = listOf(
            code("V01 RH", 520f, 450f, 880f, 550f),
            code("V01 LH", 300f, 460f, 510f, 540f),
            code("G01 LH", 300f, 700f, 510f, 780f)
        )
        assertEquals("V01 LH", BarcodeAim.pick(seen, aim)?.value)
    }

    @Test
    fun fallsBackToNearestInsideAim() {
        val seen = listOf(
            code("FAR", 120f, 410f, 200f, 450f),
            code("NEAR", 560f, 480f, 700f, 520f)
        )
        assertEquals("NEAR", BarcodeAim.pick(seen, aim)?.value)
    }

    @Test
    fun ignoresBarcodesOutsideAim() {
        val seen = listOf(code("OUT", 100f, 50f, 300f, 120f))
        assertNull(BarcodeAim.pick(seen, aim))
    }

    @Test
    fun mapsImageToCroppedView() {
        // 1280x720 프레임을 세로 화면 1080x1920에 꽉 채우면 가로가 잘린다.
        val upright = AimBox(0f, 0f, 720f, 1280f)
        val view = BarcodeAim.toView(upright, 720, 1280, 1080, 1920)
        assertEquals(0f, view.left, 0.01f)
        assertEquals(1080f, view.right, 0.01f)
        assertEquals(1920f, view.bottom, 0.01f)

        val wide = BarcodeAim.toView(AimBox(280f, 0f, 1000f, 720f), 1280, 720, 1080, 1080)
        assertEquals(0f, wide.left, 0.01f)
        assertEquals(1080f, wide.right, 0.01f)
    }

    @Test
    fun autoConfirmNeedsSameValueTwiceInARow() {
        val confirm = AutoConfirm()
        assertNull(confirm.feed("A"))
        assertNull(confirm.feed("B"))
        assertEquals("B", confirm.feed("B"))
        assertNull(confirm.feed(null))
        assertNull(confirm.feed("B"))
    }
}
