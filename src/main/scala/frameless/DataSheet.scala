package frameless

import org.apache.spark.api.java.JavaRDD
import org.apache.spark.api.java.function.{ Function => JFunction }
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{ DataFrame, Row, SaveMode, SQLContext }
import org.apache.spark.storage.StorageLevel

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe.TypeTag

import shapeless.{ Generic, HList }
import shapeless.ops.hlist.Prepend
import shapeless.ops.traversable.FromTraversable
import shapeless.syntax.std.traversable._

/** Wrapper around [[org.apache.spark.sql.DataFrame]] using [[shapeless.HList]]s to track schema.
  *
  * All heavy-lifting is still being done by the backing DataFrame so this API will more or less
  * be 1-to-1 with that of the DataFrame's.
  */
final class DataSheet[L <: HList] private(val dataFrame: DataFrame) {
  import DataSheet._

  def as(alias: Symbol): DataSheet[L] = DataSheet(dataFrame.as(alias))

  def as(alias: String): DataSheet[L] = DataSheet(dataFrame.as(alias))

  def cache(): this.type = {
    dataFrame.cache()
    this
  }

  def count(): Long = dataFrame.count()

  def distinct: DataSheet[L] = DataSheet(dataFrame.distinct)

  def except(other: DataSheet[L]): DataSheet[L] =
    DataSheet(dataFrame.except(other.dataFrame))

  def explain(): Unit = dataFrame.explain()

  def explain(extended: Boolean): Unit = dataFrame.explain(extended)

  def intersect(other: DataSheet[L]): DataSheet[L] =
    DataSheet(dataFrame.intersect(other.dataFrame))

  def isLocal: Boolean = dataFrame.isLocal

  def join[M <: HList, Out <: HList](right: DataSheet[M])(implicit P: Prepend.Aux[L, M, Out]): DataSheet[Out] =
    DataSheet(dataFrame.join(right.dataFrame))

  def limit(n: Int): DataSheet[L] = DataSheet(dataFrame.limit(n))

  def persist(newLevel: StorageLevel): this.type = {
    dataFrame.persist(newLevel)
    this
  }

  def persist(): this.type = {
    dataFrame.persist()
    this
  }

  def printSchema(): Unit = dataFrame.printSchema()

  val queryExecution = dataFrame.queryExecution

  def registerTempTable(tableName: String): Unit = dataFrame.registerTempTable(tableName)

  def repartition(numPartitions: Int): DataSheet[L] = DataSheet(dataFrame.repartition(numPartitions))

  def sample(withReplacement: Boolean, fraction: Double): DataSheet[L] =
    DataSheet(dataFrame.sample(withReplacement, fraction))

  def sample(withReplacement: Boolean, fraction: Double, seed: Long): DataSheet[L] =
    DataSheet(dataFrame.sample(withReplacement, fraction, seed))

  def save(source: String, mode: SaveMode, options: Map[String, String]): Unit = dataFrame.save(source, mode, options)

  def save(path: String, source: String, mode: SaveMode): Unit = dataFrame.save(path, source, mode)

  def save(path: String, source: String): Unit = dataFrame.save(path, source)

  def save(path: String, mode: SaveMode): Unit = dataFrame.save(path, mode)

  def save(path: String): Unit = dataFrame.save(path)

  def saveAsParquetFile(path: String): Unit = dataFrame.saveAsParquetFile(path)

  def saveAsTable(tableName: String, source: String, mode: SaveMode, options: Map[String, String]): Unit =
    dataFrame.saveAsTable(tableName, source, mode, options)

  def saveAsTable(tableName: String, source: String, mode: SaveMode): Unit =
    dataFrame.saveAsTable(tableName, source, mode)

  def saveAsTable(tableName: String, source: String): Unit =
    dataFrame.saveAsTable(tableName, source)

  def saveAsTable(tableName: String, mode: SaveMode): Unit =
    dataFrame.saveAsTable(tableName, mode)

  def saveAsTable(tableName: String): Unit =
    dataFrame.saveAsTable(tableName)

  def show(): Unit = dataFrame.show()

  def show(numRows: Int): Unit = dataFrame.show(numRows)

  val sqlContext: SQLContext = dataFrame.sqlContext

  override def toString(): String = s"DataSheet:\n${dataFrame.toString}"

  def unionAll(other: DataSheet[L]): DataSheet[L] =
    DataSheet(dataFrame.unionAll(other.dataFrame))

  def unpersist(): this.type = {
    dataFrame.unpersist()
    this
  }

  def unpersist(blocking: Boolean): this.type = {
    dataFrame.unpersist(blocking)
    this
  }

  /////////////////////////

  def collect[P <: Product]()(implicit Gen: Generic.Aux[P, L], P: ClassTag[P], L: FromTraversable[L]): Array[P] =
    dataFrame.collect().map(unsafeRowToProduct[P, L])

  def collectAsList[P <: Product]()(implicit Gen: Generic.Aux[P, L], L: FromTraversable[L]): List[P] =
    dataFrame.collectAsList().asScala.toList.map(unsafeRowToProduct[P, L])

  def collectAsJavaList[P <: Product]()(implicit Gen: Generic.Aux[P, L], L: FromTraversable[L]): java.util.List[P] =
    collectAsList().asJava

  def first[P <: Product]()(implicit Gen: Generic.Aux[P, L], L: FromTraversable[L]): P =
    unsafeRowToProduct(dataFrame.first())

  def flatMap[R](f: L => TraversableOnce[R])(implicit R: ClassTag[R], L: FromTraversable[L]): RDD[R] =
    dataFrame.flatMap(f.compose(unsafeRowToHList[L]))

  def foreach(f: L => Unit)(implicit L: FromTraversable[L]): Unit =
    dataFrame.foreach(f.compose(unsafeRowToHList[L]))

  def foreachPartition(f: Iterator[L] => Unit)(implicit L: FromTraversable[L]): Unit =
    dataFrame.foreachPartition(f.compose(_.map(unsafeRowToHList[L])))

  def head[P <: Product]()(implicit Gen: Generic.Aux[P, L], L: FromTraversable[L]): P =
    unsafeRowToProduct(dataFrame.head())

  def head[P <: Product](n: Int)(implicit Gen: Generic.Aux[P, L], P: ClassTag[P], L: FromTraversable[L]): Array[P] =
    dataFrame.head(n).map(unsafeRowToProduct[P, L])

  def javaRDD[P <: Product](implicit Gen: Generic.Aux[P, L], L: FromTraversable[L]): JavaRDD[P] = {
    val f = new JFunction[Row, P] { def call(v1: Row): P = unsafeRowToProduct(v1) }
    dataFrame.javaRDD.map(f)
  }

  def map[R](f: L => R)(implicit R: ClassTag[R], L: FromTraversable[L]): RDD[R] =
    dataFrame.map(f.compose(unsafeRowToHList[L]))

  def mapPartitions[R](f: Iterator[L] => Iterator[R])(implicit R: ClassTag[R], L: FromTraversable[L]): RDD[R] =
    dataFrame.mapPartitions(f.compose(_.map(unsafeRowToHList[L])))

  def rdd[P <: Product](implicit Gen: Generic.Aux[P, L], P: ClassTag[P], L: FromTraversable[L]): RDD[P] =
    dataFrame.rdd.map(unsafeRowToProduct[P, L])

  def take[P <: Product](n: Int)(implicit Gen: Generic.Aux[P, L], P: ClassTag[P], L: FromTraversable[L]): Array[P] =
    dataFrame.take(n).map(unsafeRowToProduct[P, L])
}

object DataSheet {
  private def apply[L <: HList](dataFrame: DataFrame): DataSheet[L] =
    new DataSheet[L](dataFrame)

  private def unsafeRowToHList[L <: HList : FromTraversable](row: Row): L =
    row.toSeq.toHList[L].get

  private def unsafeRowToProduct[P <: Product, L <: HList](row: Row)(implicit Gen: Generic.Aux[P, L], L: FromTraversable[L]): P =
    Gen.from(unsafeRowToHList(row))

  def fromRDD[P <: Product : TypeTag, L <: HList](rdd: RDD[P])(implicit Gen: Generic.Aux[P, L]): DataSheet[L] =
    DataSheet(new SQLContext(rdd.sparkContext).implicits.rddToDataFrameHolder(rdd).toDF())
}
