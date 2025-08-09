package jubu.spark
import munit.FunSuite
class SplitterSpec extends FunSuite {
  test("spliter should split a string into words and lowercase them") {
    val input    = "Ahoj svet"
    val expected = Seq("ahoj", "svet")
    assertEquals(splitter(input), expected)
  }
}