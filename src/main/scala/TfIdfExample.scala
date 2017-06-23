import org.apache.spark.ml.feature.{CountVectorizer, _}
import org.apache.spark.ml.linalg.SparseVector
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types._

import scala.collection.mutable.ListBuffer

object TfIdfExample {

  /**
    * The first argument should be path to file.
    * The second argument should be number of documents for each word.
    * The third argument should be path to save.
    *
    * @param args arguments.
    */
  def main(args: Array[String]): Unit = {
    makeIndex(args(0), args(1).toInt, args(1))
  }

  def makeIndex(pathToFiles: String, relevantNum: Int, pathToSave: String) {
    val spark = SparkSession
      .builder
      .appName("TfIdfExample")
      .getOrCreate()

    import spark.implicits._

    val rowData = spark.sparkContext.textFile(pathToFiles)
      .map(_.split("\\t"))
      .map(array => Tuple2(array(0), array(1).toLowerCase))
      .toDF("id", "text")
    rowData.show()

    val regexTokenizer = new RegexTokenizer()
      .setInputCol("text")
      .setOutputCol("words")
      .setPattern("\\W")
    val wordsData = regexTokenizer.transform(rowData)

    val countVectorizer = new CountVectorizer()
      .setInputCol("words").setOutputCol("rawFeatures")
    val featurizedDataModel = countVectorizer.fit(wordsData)

    val featurizedData = featurizedDataModel.transform(wordsData)

    val idf = new IDF().setInputCol("rawFeatures").setOutputCol("features")
    val idfModel = idf.fit(featurizedData)

    val rescaledData = idfModel.transform(featurizedData)
    rescaledData.select("id", "features").show(false)

    val schema = StructType(Seq(StructField("id", StringType),
      StructField("index", IntegerType),
      StructField("value", DoubleType)))

    val data = rescaledData.rdd.map(row => {
      val id = row.getAs[String]("id")
      val buffer = ListBuffer[(Int, List[(String, Double)])]()
      row.getAs[SparseVector]("features")
        .foreachActive((index, value) => {
          buffer += Tuple2(index, List(Tuple2(id, value)))
        })
      buffer.toList
    })
      .flatMap(tuple => tuple)
      .reduceByKey((list1, list2) => (list1 ::: list2).sortWith(_._2 > _._2).take(relevantNum))
      .map(tuple => Tuple2(featurizedDataModel.vocabulary(tuple._1), tuple._2))
      .toDF("word", "relevants")
    data.show(false)

    data.write.json(pathToSave)
    spark.stop()
  }
}