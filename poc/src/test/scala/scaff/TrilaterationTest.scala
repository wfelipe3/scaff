package scaff

import zio.test._
import zio.test.Assertion._
import scala.util.chaining._
import mouse.all._

object trilaterationTest extends DefaultRunnableSpec:
  
  def spec = 
    suite("Distance")(
      test("values") {
        p(1, 3) |> { p1 =>
            assert(distance(p(-3,3))(p1))(equalTo(16)) &&
            assert(distance(p(5,1))(p1))(equalTo(20)) &&
            assert(distance(p(3,-3))(p1))(equalTo(40)) &&
            assert(distance(p(1,1))(p(5,4)))(equalTo(25)) &&
            assert(distance(p(-500, -200))(p(1,1)))(equalTo(291402.0)) &&
            assert(distance(p(100, -100))(p(1,1)))(equalTo(20002.0)) &&
            assert(distance(p(500, 100))(p(1,1)))(equalTo(258802.0))
            assert(distance(p(-500, -200))(p(100,200)))(equalTo(520000.0)) &&
            assert(distance(p(100, -100))(p(100,200)))(equalTo(90000.0)) &&
            assert(distance(p(500, 100))(p(100,200)))(equalTo(170000.0))
          }
      },
      test("x1") {
        assert(
          xy(
            pd(-3, 3, 16),
            pd(5, 1, 20),
            pd(3, -3, 40)
          )
        )(equalTo(Point(1,3))) &&
        assert(
          xy(
            pd(5, 4, 25), 
            pd(4,-3, 25), 
            pd(-4, 13, 169)
          )
        )(equalTo(Point(1,1))) &&
        assert(
          xy(
            pd(-500, -200, 100.pw2), 
            pd(100, -100, 115.5.pw2), 
            pd(500, 100, 142.7.pw2)
          )
        )(equalTo("distance from point(-487.2859125, 1557.014225) to point(-500.0, -200.0) is 3087260.634873308 != 10000.0 and distance from point(-487.2859125, 1557.014225) to point(100.0, -100.0) is 3090600.8848733082 != 13340.25 and distance from point(-487.2859125, 1557.014225) to point(500.0, 100.0) is 3097623.9248733083 != 20363.289999999997")) &&
        assert(
          xy(
            pd(-500, -200, 291402.0), 
            pd(100, -100, 20002.0), 
            pd(500, 100, 258802.0)
          )
        )(equalTo(Point(1,1)))
      }
    )

  case class Point(x: Double, y: Double)

  def p(x: Double, y: Double): Point = Point(x, y)

  case class PointDistance(point: Point, distance: Double) { 
    def x = point.x
    def y = point.y
    def d = distance
  }

  extension (p: Point)
    def distance(other: Point) =
      Math.pow(p.x - other.x, 2) + Math.pow(p.y - other.y, 2)

  def pd(x: Int, y: Int, distance: Double): PointDistance = 
    PointDistance(Point(x, y), distance)

  def f(y: Int) =
    (2 * y + 3) / 8.0 -> y

  import Math.pow

  def pow2(value: Int) = pow(value, 2)

  extension (value: Int)
    def pw2 = pow2(value)

  extension (value: Double)
    def pw2 = Math.pow(value, 2)

  def y(p1: PointDistance, p2: PointDistance, p3: PointDistance) =
    val d1 = p1.d
    val d2 = p2.d
    val d3 = p3.d
    ((-2 * p1.x + 2 * p2.x) * (d1 - d3 - p1.x.pw2 - p1.y.pw2 + p3.x.pw2 + p3.y.pw2) 
    - (-2 * p1.x + 2 * p3.x) * (d1 - d2 - p1.x.pw2 - p1.y.pw2 + p2.x.pw2 + p2.y.pw2))
    / (4 * ((-p1.x + p3.x) * (p1.y - p2.y) - (p1.y - p3.y) * (-p1.x + p2.x)))

  def x(p1: PointDistance, p2: PointDistance, p3: PointDistance)(y: Double) =
    val d1 = p1.d
    val d2 = p2.d
    val d3 = p3.d
    (d1 - d2 - p1.x.pw2 - p1.y.pw2 + p2.x.pw2 + p2.y.pw2 - (2 * y * (-p1.y + p2.y)))
    / (-2 * p1.x + 2 * p2.x)

  def xy(p2: PointDistance, p3: PointDistance, p4: PointDistance): String | Point =
    y(p2,p3,p4) |> (y => Point(x(p2,p3,p4)(y), y)) |> (validateDistances.curried(p2)(p3)(p4))

  def validateDistances(p2: PointDistance, p3: PointDistance, p4: PointDistance, point: Point): String | Point =
      validateDistance(p2)(point) 
      && validateDistance(p3)(point) 
      && validateDistance(p4)(point)

  def validateDistance(p: PointDistance)(point: Point): String | Point =
    if point.distance(p.point) != p.d then 
      s"distance from point(${point.x}, ${point.y}) to point(${p.x}, ${p.y}) is ${point.distance(p.point)} != ${p.d}"
    else point

  extension (v: String | Point)
    def &&(other: String | Point): String | Point =
      (v, other) match
        case (x: String, y: String) =>
          s"$x and $y"
        case (x: String, _) =>
          x
        case (_, x: String) =>
          x
        case (p1: Point, p2: Point) =>
          p1


    